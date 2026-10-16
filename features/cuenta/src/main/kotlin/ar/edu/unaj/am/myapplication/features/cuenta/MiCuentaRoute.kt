package ar.edu.unaj.am.myapplication.features.cuenta

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle

@Composable
fun MiCuentaRoute(viewModel: MiCuentaViewModel, onPerfilGuardado: () -> Unit, onLogout: () -> Unit,
    reporteViewModel: ReporteGananciasViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reporte by reporteViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.perfil?.esBarbero) {
        if (state.perfil?.esBarbero == true) reporteViewModel.cargar()
        else reporteViewModel.detener()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reporteViewModel.actualizar() }
    DisposableEffect(reporteViewModel) { onDispose { reporteViewModel.detener() } }
    MiCuentaScreen(state, viewModel::onNombreChange,
        onGuardar = { viewModel.guardarCambios(onPerfilGuardado) },
        onReintentar = viewModel::cargarPerfil, onLogout = onLogout,
        reporteState = reporte, onPeriodo = reporteViewModel::periodo,
        onMover = reporteViewModel::mover,
        onReintentarReporte = { if (state.perfil?.esBarbero == true) reporteViewModel.cargar() })
}
