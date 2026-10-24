package ar.edu.unaj.am.myapplication.core.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
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
    val context = LocalContext.current
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
            mensaje = "Permití el acceso a la ubicación o elegí un punto en el mapa."
            return
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mostrarActivarGps = true
            return
        }
        buscando = true
        mensaje = null
        trabajo = scope.launch {
            try {
                val preciso = permitido(Manifest.permission.ACCESS_FINE_LOCATION)
                val proveedor = if (preciso) LocationManager.GPS_PROVIDER
                    else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
                    else null
                val location = if (proveedor == null) null else withTimeoutOrNull(25_000) {
                    obtenerPosicion(manager, proveedor, ContextCompat.getMainExecutor(context))
                }
                if (location == null) mensaje = "No se pudo obtener tu ubicación. Reintentá al aire libre o elegí un punto en el mapa."
                else {
                    alUbicar(Coordenadas(location.latitude, location.longitude))
                    if (!preciso || location.accuracy > 100) mensaje = "Ubicación aproximada. Podés ajustar el punto tocando el mapa."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mensaje = "No se pudo obtener tu ubicación. Revisá el GPS y los permisos."
            } finally {
                buscando = false
            }
        }
    }
    val configuracionGps = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) localizar()
        else mensaje = "El GPS sigue apagado. Podés activarlo o elegir un punto en el mapa."
    }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) localizar()
        else mensaje = "Sin permiso de ubicación. Elegí un punto en el mapa o habilitá el permiso en Ajustes."
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        trabajo?.cancel()
    }
    if (mostrarActivarGps) AlertDialog(
        onDismissRequest = { mostrarActivarGps = false },
        title = { Text("Activar ubicación") },
        text = { Text("Activá el GPS del dispositivo para usar tu posición en el mapa.") },
        confirmButton = { TextButton(onClick = {
            mostrarActivarGps = false
            configuracionGps.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }) { Text("Activar GPS") } },
        dismissButton = { TextButton(onClick = {
            mostrarActivarGps = false
            mensaje = "Podés elegir la ubicación tocando el mapa."
        }) { Text("Elegir en el mapa") } }
    )
    return ControlUbicacion(buscando, mensaje, solicitar = {
        if (permitido(Manifest.permission.ACCESS_FINE_LOCATION) || permitido(Manifest.permission.ACCESS_COARSE_LOCATION)) localizar()
        else permisos.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, cancelar = { trabajo?.cancel(); mensaje = null }, abrirPermisos = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    })
}

@SuppressLint("MissingPermission")
private suspend fun obtenerPosicion(manager: LocationManager, proveedor: String, executor: java.util.concurrent.Executor): Location? =
    suspendCancellableCoroutine { continuation ->
        val cancel = CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, proveedor, cancel, executor) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }
