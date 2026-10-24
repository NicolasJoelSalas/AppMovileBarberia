package ar.edu.unaj.am.myapplication.features.cuenta.ubicacion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.maps.*

@Composable
fun UbicacionBarberiaRoute(viewModel: UbicacionBarberiaViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ubicacion = rememberUbicacionDispositivo(viewModel::seleccionar)
    UbicacionBarberiaScreen(state, { punto -> ubicacion.cancelar(); viewModel.seleccionar(punto) }, viewModel::guardar, viewModel::cargar,
        ubicacion, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UbicacionBarberiaScreen(
    state: UbicacionBarberiaUiState,
    onSeleccionar: (Coordenadas) -> Unit,
    onGuardar: () -> Unit,
    onReintentar: () -> Unit,
    ubicacion: ControlUbicacion,
    onBack: () -> Unit
) {
    var confirmarSalida by remember { mutableStateOf(false) }
    val volver = { if (state.puedeGuardar) confirmarSalida = true else if (!state.guardando) onBack() }
    BackHandler { volver() }
    if (confirmarSalida) AlertDialog(onDismissRequest = { confirmarSalida = false },
        title = { Text("¿Salir sin guardar?") }, text = { Text("La nueva ubicación todavía no se guardó.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Salir") } },
        dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Seguir editando") } })
    Scaffold(topBar = { AppTopBar("Ubicación de la barbería", navigationIcon = {
        IconButton(onClick = volver, enabled = !state.guardando) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
        }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tocá el mapa para colocar el pin en la entrada de tu barbería. Los clientes usarán este punto para llegar.")
                TextButton(onClick = ubicacion.solicitar, enabled = state.cargada && !state.guardando && !ubicacion.buscando) {
                    Text(if (ubicacion.buscando) "Obteniendo ubicación…" else "Usar mi ubicación")
                }
                ubicacion.mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (ubicacion.mensaje != null) TextButton(onClick = ubicacion.abrirPermisos) { Text("Ajustes de permisos") }
                state.error?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
                if (!state.cargando && !state.cargada) TextButton(onClick = onReintentar) { Text("Reintentar") }
                state.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            MapaBarberia(state.seleccion, Modifier.weight(1f).fillMaxWidth(),
                onSeleccionar = if (state.cargada && !state.guardando) onSeleccionar else null)
            Button(onClick = onGuardar, enabled = state.puedeGuardar,
                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (state.guardando) "Guardando…" else "Guardar ubicación")
            }
        }
    }
}
