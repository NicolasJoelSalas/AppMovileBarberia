package ar.edu.unaj.am.myapplication.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.*
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.UbicacionDispositivoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.buscarUbicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.coroutines.resume

@Singleton
class UbicacionDispositivoAndroid @Inject constructor(@ApplicationContext private val context: Context) : UbicacionDispositivoRepository {
    private val manager get() = context.getSystemService(LocationManager::class.java)
    override fun habilitada() = LocationManagerCompat.isLocationEnabled(manager)
    override suspend fun obtener(preciso: Boolean) = obtenerUbicacionDisponible(manager,preciso,ContextCompat.getMainExecutor(context))
    override fun observar() = observarPosiciones(context)
}

@SuppressLint("MissingPermission")
internal suspend fun obtenerUbicacionDisponible(
    manager: LocationManager,
    preciso: Boolean,
    executor: java.util.concurrent.Executor
): PosicionDispositivo? {
    val proveedores = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (preciso) add(LocationManager.GPS_PROVIDER)
    }.filter { proveedor ->
        LocationManagerCompat.hasProvider(manager, proveedor) && manager.isProviderEnabled(proveedor)
    }
    return buscarUbicacion(proveedores,
        ultimaPosicion = { manager.getLastKnownLocation(it)?.aPosicion() },
        obtenerActual = { obtenerPosicion(manager, it, executor)?.aPosicion() },
        reloj = SystemClock::elapsedRealtime)
}

internal fun Location.aPosicion() = PosicionDispositivo(
    Coordenadas(latitude, longitude), if (hasAccuracy()) accuracy else Float.MAX_VALUE,
    elapsedRealtimeNanos / 1_000_000
)

@SuppressLint("MissingPermission")
private suspend fun obtenerPosicion(manager: LocationManager, proveedor: String, executor: java.util.concurrent.Executor): Location? =
    suspendCancellableCoroutine { continuation ->
        val cancel = CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, proveedor, cancel, executor) { location ->
            if (continuation.isActive) continuation.resume(location)
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
