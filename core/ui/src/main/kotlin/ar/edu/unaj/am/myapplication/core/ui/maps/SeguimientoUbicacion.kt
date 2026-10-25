package ar.edu.unaj.am.myapplication.core.ui.maps

import ar.edu.unaj.am.myapplication.core.ui.R

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive

/** La suscripción existe solamente mientras la pantalla está en primer plano. */
@Composable
fun ObservarUbicacionDispositivo(
    activo: Boolean,
    onUbicacion: (PosicionDispositivo) -> Unit,
    onAviso: (String?) -> Unit
) {
    val context = LocalContext.current.applicationContext
    ObservarPosicionesEnPrimerPlano(activo, { observarPosiciones(context) }, onUbicacion, onAviso)
}

@Composable
internal fun ObservarPosicionesEnPrimerPlano(
    activo: Boolean,
    posiciones: () -> Flow<PosicionDispositivo>,
    onUbicacion: (PosicionDispositivo) -> Unit,
    onAviso: (String?) -> Unit
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val fuente by rememberUpdatedState(posiciones)
    val callback by rememberUpdatedState(onUbicacion)
    val aviso by rememberUpdatedState(onAviso)
    LaunchedEffect(activo, lifecycle) {
        if (!activo) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Si se apaga ubicación o se revoca el permiso, reintentar sin abrir diálogos en bucle.
            while (isActive) {
                try {
                    fuente().collect { posicion ->
                        aviso(null)
                        callback(posicion)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    aviso(resources.getString(R.string.core_ui_seguimiento_pausado_revisa_la_ubicacion_y_sus_permisos))
                }
                delay(5_000)
            }
        }
    }
}

@SuppressLint("MissingPermission")
internal fun observarPosiciones(context: Context) = callbackFlow {
    val manager = context.getSystemService(LocationManager::class.java)
    fun permiso(value: String) = ContextCompat.checkSelfPermission(context, value) == PackageManager.PERMISSION_GRANTED
    val preciso = permiso(Manifest.permission.ACCESS_FINE_LOCATION)
    check(preciso || permiso(Manifest.permission.ACCESS_COARSE_LOCATION))
    check(LocationManagerCompat.isLocationEnabled(manager))
    val proveedores = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (preciso) add(LocationManager.GPS_PROVIDER)
    }.filter { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
    val request = LocationRequestCompat.Builder(1_000)
        .setMinUpdateIntervalMillis(1_000).setMinUpdateDistanceMeters(1f)
        .setQuality(if (preciso) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
        .build()
    val registrados = mutableListOf<LocationListenerCompat>()
    try {
        proveedores.forEach { proveedor ->
            val listener = object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) {
                    val posicion = location.aPosicion()
                    if (posicion.esReciente(SystemClock.elapsedRealtime())) trySend(posicion)
                }
                override fun onProviderDisabled(provider: String) {
                    // Reconstruir la suscripción también al volver a activar el proveedor.
                    close(IllegalStateException("Proveedor desactivado"))
                }
            }
            try {
                LocationManagerCompat.requestLocationUpdates(manager, proveedor, request,
                    ContextCompat.getMainExecutor(context), listener)
                registrados.add(listener)
            } catch (_: Exception) {
                manager.removeUpdates(listener)
            }
        }
        check(registrados.isNotEmpty())
        awaitClose { }
    } finally {
        registrados.forEach { manager.removeUpdates(it) }
    }
}.conflate()
