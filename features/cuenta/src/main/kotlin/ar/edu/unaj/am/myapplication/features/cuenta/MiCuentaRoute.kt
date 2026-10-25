package ar.edu.unaj.am.myapplication.features.cuenta

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MiCuentaRoute(viewModel: MiCuentaViewModel, onPerfilGuardado: () -> Unit, onLogout: () -> Unit,
    onUbicacionBarberia: () -> Unit = {}, onServicios: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MiCuentaScreen(state, viewModel::onNombreChange,
        onGuardar = { viewModel.guardarCambios(onPerfilGuardado) },
        onReintentar = viewModel::cargarPerfil, onLogout = onLogout, onUbicacionBarberia = onUbicacionBarberia,
        onServicios = onServicios)
}
