package ar.edu.unaj.am.myapplication.core.ui.maps

import ar.edu.unaj.am.myapplication.core.ui.R

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class ControlUbicacion internal constructor(
    val buscando: Boolean,
    val mensaje: String?,
    val solicitar: () -> Unit,
    val cancelar: () -> Unit,
    val abrirPermisos: () -> Unit
)

/** Adquiere una posición puntual; deja de usar el sensor al salir de la pantalla. */
@Composable
fun rememberUbicacionDispositivo(onUbicacion: (Coordenadas) -> Unit): ControlUbicacion {
    val callback by rememberUpdatedState(onUbicacion)
    return rememberUbicacionDispositivoDetallada { callback(it.coordenadas) }
}

@Composable
fun rememberUbicacionDispositivoDetallada(onUbicacion: (PosicionDispositivo) -> Unit): ControlUbicacion {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val manager = remember { context.getSystemService(LocationManager::class.java) }
    val scope = rememberCoroutineScope()
    val alUbicar by rememberUpdatedState(onUbicacion)
    var buscando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var mostrarActivarGps by remember { mutableStateOf(false) }
    var trabajo by remember { mutableStateOf<Job?>(null) }
    fun permitido(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun localizar() {
        if (buscando) return
        if (!permitido(Manifest.permission.ACCESS_FINE_LOCATION) && !permitido(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            mensaje = resources.getString(R.string.core_ui_permiti_el_acceso_a_la_ubicacion_o_elegi_un_punto_en_el_mapa)
            return
        }
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            mostrarActivarGps = true
            return
        }
        buscando = true
        mensaje = null
        trabajo = scope.launch {
            try {
                val preciso = permitido(Manifest.permission.ACCESS_FINE_LOCATION)
                val location = obtenerUbicacionDisponible(manager, preciso, ContextCompat.getMainExecutor(context))
                if (location == null) mensaje = resources.getString(R.string.core_ui_el_dispositivo_todavia_no_pudo_determinar_tu_ubicacion_reintenta_o_elegi_un_punto_en_el_mapa)
                else {
                    alUbicar(location)
                    if (!preciso || location.precisionMetros > 100) mensaje = resources.getString(R.string.core_ui_ubicacion_aproximada_podes_ajustar_el_punto_tocando_el_mapa)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mensaje = resources.getString(R.string.core_ui_no_se_pudo_obtener_tu_ubicacion_revisa_el_gps_y_los_permisos)
            } finally {
                buscando = false
            }
        }
    }
    val configuracionGps = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (LocationManagerCompat.isLocationEnabled(manager)) localizar()
        else mensaje = resources.getString(R.string.core_ui_la_ubicacion_sigue_apagada_podes_activarla_o_elegir_un_punto_en_el_mapa)
    }
    val configuracionPermisos = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        localizar()
    }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) localizar()
        else mensaje = resources.getString(R.string.core_ui_sin_permiso_de_ubicacion_elegi_un_punto_en_el_mapa_o_habilita_el_permiso_en_ajustes)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        trabajo?.cancel()
    }
    if (mostrarActivarGps) AlertDialog(
        onDismissRequest = { mostrarActivarGps = false },
        title = { Text(stringResource(R.string.core_ui_activar_ubicacion)) },
        text = { Text(stringResource(R.string.core_ui_activa_la_ubicacion_del_dispositivo_para_usar_tu_posicion_en_el_mapa)) },
        confirmButton = { TextButton(onClick = {
            mostrarActivarGps = false
            configuracionGps.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }) { Text(stringResource(R.string.core_ui_activar_gps)) } },
        dismissButton = { TextButton(onClick = {
            mostrarActivarGps = false
            mensaje = resources.getString(R.string.core_ui_podes_elegir_la_ubicacion_tocando_el_mapa)
        }) { Text(stringResource(R.string.core_ui_elegir_en_el_mapa)) } }
    )
    return ControlUbicacion(buscando, mensaje, solicitar = {
        if (permitido(Manifest.permission.ACCESS_FINE_LOCATION) || permitido(Manifest.permission.ACCESS_COARSE_LOCATION)) localizar()
        else permisos.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, cancelar = { trabajo?.cancel(); mensaje = null }, abrirPermisos = {
        configuracionPermisos.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    })
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
