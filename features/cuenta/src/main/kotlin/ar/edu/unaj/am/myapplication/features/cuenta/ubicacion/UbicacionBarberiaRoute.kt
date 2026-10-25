package ar.edu.unaj.am.myapplication.features.cuenta.ubicacion

import ar.edu.unaj.am.myapplication.features.cuenta.R

import androidx.compose.ui.res.stringResource

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
        title = { Text(stringResource(R.string.features_cuenta_salir_sin_guardar)) }, text = { Text(stringResource(R.string.features_cuenta_la_nueva_ubicacion_todavia_no_se_guardo)) },
        confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.features_cuenta_salir)) } },
        dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text(stringResource(R.string.features_cuenta_seguir_editando)) } })
    Scaffold(topBar = { AppTopBar(stringResource(R.string.features_cuenta_ubicacion_de_la_barberia), navigationIcon = {
        IconButton(onClick = volver, enabled = !state.guardando) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.features_cuenta_volver))
        }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.features_cuenta_toca_el_mapa_para_colocar_el_pin_en_la_entrada_de_tu_barberia_los_clientes_usaran_este_punto_para_llegar))
                TextButton(onClick = ubicacion.solicitar, enabled = state.cargada && !state.guardando && !ubicacion.buscando) {
                    Text(if (ubicacion.buscando) stringResource(R.string.features_cuenta_obteniendo_ubicacion) else stringResource(R.string.features_cuenta_usar_mi_ubicacion))
                }
                ubicacion.mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (ubicacion.mensaje != null) TextButton(onClick = ubicacion.abrirPermisos) { Text(stringResource(R.string.features_cuenta_ajustes_de_permisos)) }
                state.error?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
                if (!state.cargando && !state.cargada) TextButton(onClick = onReintentar) { Text(stringResource(R.string.features_cuenta_reintentar)) }
                state.mensaje?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
            }
            MapaBarberia(state.seleccion, Modifier.weight(1f).fillMaxWidth(),
                onSeleccionar = if (state.cargada && !state.guardando) onSeleccionar else null)
            Button(onClick = onGuardar, enabled = state.puedeGuardar,
                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (state.guardando) stringResource(R.string.features_cuenta_guardando) else stringResource(R.string.features_cuenta_guardar_ubicacion))
            }
        }
    }
}
