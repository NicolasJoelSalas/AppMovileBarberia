package ar.edu.unaj.am.myapplication.features.cuenta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar

@Composable
fun ReporteGananciasRoute(viewModel: ReporteGananciasViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.cargar()
        onDispose { viewModel.detener() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.actualizar() }
    Scaffold(topBar = { AppTopBar(title = "Ganancias") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ReporteGananciasContent(state, viewModel::periodo, viewModel::mover, viewModel::cargar)
        }
    }
}
