package ar.edu.unaj.am.myapplication.features.turnos.ubicacion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.maps.*
import kotlin.math.ceil

@Composable
fun ComoLlegarRoute(viewModel: ComoLlegarViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ubicacion = rememberUbicacionDispositivoDetallada { posicion ->
        viewModel.iniciarSeguimiento(posicion.coordenadas, posicion.precisionMetros, posicion.instanteMonotonicoMillis)
    }
    ObservarUbicacionDispositivo(state.siguiendoUbicacion,
        onUbicacion = { viewModel.actualizarPosicion(it.coordenadas, it.precisionMetros, it.instanteMonotonicoMillis) },
        onAviso = viewModel::avisoUbicacion)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.suspenderConsulta() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reanudarConsulta() }
    var solicitada by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.destino) {
        if (state.destino != null && !solicitada && state.origen == null) {
            solicitada = true
            ubicacion.solicitar()
        }
    }
    ComoLlegarScreen(state, ubicacion, { punto -> ubicacion.cancelar(); viewModel.origen(punto) },
        onReintentar = { if (state.destino == null) viewModel.cargar() else viewModel.calcularRuta() }, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComoLlegarScreen(
    state: ComoLlegarUiState,
    ubicacion: ControlUbicacion,
    onOrigen: (Coordenadas) -> Unit,
    onReintentar: () -> Unit,
    onBack: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    var centrarUsuario by rememberSaveable { mutableStateOf(true) }
    Scaffold(topBar = { AppTopBar("Cómo llegar", navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.cargando || (state.ruta == null && (state.calculando || ubicacion.buscando))) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!state.cargando && state.destino == null && state.error == null)
                    Text("El barbero todavía no configuró la ubicación de su barbería.")
                if (state.destino != null) {
                    Text(if (state.siguiendoUbicacion) "Pin rojo: barbería · Punto azul: tu ubicación"
                        else "Pin rojo: barbería · Punto azul: tu origen", style = MaterialTheme.typography.bodySmall)
                    Text("Podés cambiar el punto de partida tocando el mapa.", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { centrarUsuario = true; ubicacion.solicitar() }, enabled = !ubicacion.buscando) {
                            Text(if (ubicacion.buscando) "Obteniendo ubicación…" else "Usar mi ubicación")
                        }
                        if (state.siguiendoUbicacion) TextButton(onClick = { centrarUsuario = true }) { Text("Centrar") }
                    }
                }
                state.avisoUbicacion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ubicacion.mensaje?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = ubicacion.abrirPermisos) { Text("Ajustes de permisos") }
                }
                state.error?.let {
                    Text(it.asMessage(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onReintentar) { Text("Reintentar") }
                }
                if (state.calculando && state.ruta == null) Text("Calculando recorrido…")
                state.ruta?.let { ruta ->
                    val metros = state.distanciaRestanteMetros ?: ruta.distanciaMetros
                    val distancia = if (metros < 1000) "${ceil(metros).toInt()} m"
                        else String.format(locale, "%.1f km", metros / 1000)
                    Text(distancia, style = MaterialTheme.typography.titleMedium)
                }
            }
            MapaBarberia(destino = state.destino, origen = state.origen,
                recorrido = state.ruta?.puntos.orEmpty(), modifier = Modifier.weight(1f).fillMaxWidth(),
                seguimientoHabilitado = state.siguiendoUbicacion, centrarEnOrigen = centrarUsuario,
                onExplorarMapa = { centrarUsuario = false },
                onSeleccionar = if (state.destino != null) onOrigen else null)
        }
    }
}
