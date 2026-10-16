package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import ar.edu.unaj.am.myapplication.features.turnos.components.SemanaSelector
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent
import ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.features.turnos.components.TurnoCard

@Composable
fun TurnosListRoute(viewModel: TurnoListViewModel, onReservar: (() -> Unit)? = null) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semanaOffset by viewModel.semanaOffset.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.actualizarSemana() }
    val completandoId by viewModel.completandoId.collectAsStateWithLifecycle()
    val errorCompletar by viewModel.errorCompletar.collectAsStateWithLifecycle()
    var ahoraMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                ahoraMillis = System.currentTimeMillis()
                viewModel.actualizarSemana()
                delay(1000)
            }
        }
    }
    TurnosListScreen(uiState = uiState, onRetry = viewModel::onRetry, onReservar = onReservar,
        semanaOffset = semanaOffset, onSemanaChange = viewModel::seleccionarSemana,
        ahoraMillis = ahoraMillis, completandoId = completandoId,
        errorCompletar = errorCompletar?.asMessage(),
        onMarcarRealizado = if (viewModel.puedeCompletar) viewModel::marcarRealizado else null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosListScreen(
    uiState: TurnosListUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onReservar: (() -> Unit)? = null,
    semanaOffset: Int = 0,
    onSemanaChange: (Int) -> Unit = {},
    ahoraMillis: Long = System.currentTimeMillis(),
    completandoId: String? = null,
    errorCompletar: String? = null,
    onMarcarRealizado: ((Turno) -> Unit)? = null
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
        SemanaSelector(semanaOffset = semanaOffset, onSemanaChange = onSemanaChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        errorCompletar?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp)) }
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
                            TurnoCard(turno = turno,
                                onMarcarRealizado = onMarcarRealizado?.let { action -> { action(turno) } },
                                habilitado = completandoId == null &&
                                    turno.inicioMillis?.let { it <= ahoraMillis } == true,
                                completando = completandoId == turno.id)
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
