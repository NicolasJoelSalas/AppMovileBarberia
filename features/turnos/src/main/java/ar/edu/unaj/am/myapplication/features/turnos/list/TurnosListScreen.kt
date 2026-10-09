package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent
import ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.features.turnos.components.TurnoCard

@Composable
fun TurnosListRoute(viewModel: TurnoListViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TurnosListScreen(uiState = uiState, onRetry = viewModel::onRetry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosListScreen(
    uiState: TurnosListUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Turnos") }) }
    ) { paddingValues ->
        val contentModifier = Modifier.fillMaxSize().padding(paddingValues)
        when (uiState) {
            TurnosListUiState.Loading -> LoadingContent(
                message = "Cargando turnos…",
                modifier = contentModifier
            )
            is TurnosListUiState.Error -> ErrorContent(
                title = "No se pudieron cargar los turnos",
                message = uiState.error.asMessage(),
                onRetry = onRetry,
                modifier = contentModifier
            )
            is TurnosListUiState.Success -> {
                if (uiState.turnos.isEmpty()) {
                    Box(modifier = contentModifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Todavía no hay turnos cargados.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = contentModifier,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.turnos, key = { it.id }) { turno ->
                            TurnoCard(turno = turno)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TurnosListScreenPreview() {
    MaterialTheme {
        TurnosListScreen(
            uiState = TurnosListUiState.Success(listOf(Turno(
                id = "ejemplo",
                clienteId = "cliente",
                barberoId = "barbero",
                servicioId = "corte",
                inicioMillis = 1791468000000L,
                finMillis = 1791470400000L,
                estado = "Confirmado"
            ))),
            onRetry = {}
        )
    }
}
