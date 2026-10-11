package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent
import ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.features.turnos.components.TurnoCard

@Composable
fun TurnosListRoute(viewModel: TurnoListViewModel, onReservar: (() -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semanaOffset by viewModel.semanaOffset.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.actualizarSemana() }
    LaunchedEffect(viewModel) {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        while (true) {
            val now = ZonedDateTime.now(zone)
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone))
                .toMillis().coerceAtLeast(1000))
            viewModel.actualizarSemana()
        }
    }
    TurnosListScreen(uiState = uiState, onRetry = viewModel::onRetry, onReservar = onReservar,
        headerContent = headerContent, semanaOffset = semanaOffset, onSemanaChange = viewModel::seleccionarSemana)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosListScreen(
    uiState: TurnosListUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onReservar: (() -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    semanaOffset: Int = 0,
    onSemanaChange: (Int) -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(if (onReservar != null) "Mis turnos" else "Turnos",
            fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            actions = {
                onReservar?.let { TextButton(onClick = it) { Text("Reservar turno") } }
            }) }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
        headerContent?.invoke()
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = semanaOffset == 0, onClick = { onSemanaChange(0) },
                label = { Text("Esta semana") })
            FilterChip(selected = semanaOffset == 1, onClick = { onSemanaChange(1) },
                label = { Text("Próxima semana") })
        }
        val contentModifier = Modifier.weight(1f).fillMaxSize()
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
                        Text(if (onReservar != null) "No tenés turnos reservados para la semana seleccionada." else "No hay turnos para la semana seleccionada.",
                            style = MaterialTheme.typography.bodyLarge)
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
                estado = "Confirmado",
                clienteNombre = "Nicolás Salas",
                servicioNombre = "Corte de cabello"
            ))),
            onRetry = {}
        )
    }
}
