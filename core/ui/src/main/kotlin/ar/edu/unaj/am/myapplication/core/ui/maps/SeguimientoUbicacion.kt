package ar.edu.unaj.am.myapplication.core.ui.maps

import ar.edu.unaj.am.myapplication.core.ui.R
import ar.edu.unaj.am.myapplication.core.domain.model.PosicionDispositivo

import androidx.compose.ui.res.stringResource

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive

/** La suscripción existe solamente mientras la pantalla está en primer plano. */
@Composable
fun ObservarUbicacionDispositivo(
    activo: Boolean,
    onUbicacion: (PosicionDispositivo) -> Unit,
    onAviso: (String?) -> Unit
) {
    val proveedor = LocalUbicacionDispositivo.current
    ObservarPosicionesEnPrimerPlano(activo, proveedor::observar, onUbicacion, onAviso)
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
