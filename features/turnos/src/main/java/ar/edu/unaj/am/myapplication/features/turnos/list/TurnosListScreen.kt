package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.features.turnos.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar

import ar.edu.unaj.am.myapplication.features.turnos.components.SemanaSelector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent
import ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.features.turnos.components.TurnoCard
import ar.edu.unaj.am.myapplication.core.domain.usecase.instanteRecordatorio

@Composable
fun TurnosListRoute(viewModel: TurnoListViewModel, onReservar: (() -> Unit)? = null,
    onComoLlegar: ((Turno) -> Unit)? = null,
    onModificar: ((Turno) -> Unit)? = null,
    onRecordatorio: ((Turno) -> Unit)? = null, recordatoriosActivos: Set<String> = emptySet()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val avisoSincronizacion by viewModel.avisoSincronizacion.collectAsStateWithLifecycle()
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
        onComoLlegar = onComoLlegar,
        avisoSincronizacion = avisoSincronizacion?.asMessage(),
        semanaOffset = semanaOffset, onSemanaChange = viewModel::seleccionarSemana,
        ahoraMillis = ahoraMillis, completandoId = completandoId,
        errorCompletar = errorCompletar?.asMessage(),
        onRecordatorio = onRecordatorio, recordatoriosActivos = recordatoriosActivos,
        onModificar = onModificar, onCancelar = if (viewModel.puedeCancelar) viewModel::cancelar else null,
        onMarcarRealizado = if (viewModel.puedeCompletar) viewModel::marcarRealizado else null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnosListScreen(
    uiState: TurnosListUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onReservar: (() -> Unit)? = null,
    onComoLlegar: ((Turno) -> Unit)? = null,
    semanaOffset: Int = 0,
    onSemanaChange: (Int) -> Unit = {},
    ahoraMillis: Long = System.currentTimeMillis(),
    completandoId: String? = null,
    errorCompletar: String? = null,
    onMarcarRealizado: ((Turno) -> Unit)? = null,
    onRecordatorio: ((Turno) -> Unit)? = null,
    recordatoriosActivos: Set<String> = emptySet(),
    onCancelar: ((Turno) -> Unit)? = null, onModificar: ((Turno) -> Unit)? = null,
    avisoSincronizacion: String? = null
) {
    var filtroEstado by rememberSaveable { mutableStateOf("Todos") }
    var cancelar by remember { androidx.compose.runtime.mutableStateOf<Turno?>(null) }
    cancelar?.let { turno ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { cancelar = null },
            title = { Text(stringResource(R.string.features_turnos_cancelar_turno)) }, text = { Text(stringResource(R.string.features_turnos_queres_cancelar_este_turno_se_liberara_el_horario)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { cancelar = null; onCancelar?.invoke(turno) }) { Text(stringResource(R.string.features_turnos_cancelar_turno)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { cancelar = null }) { Text(stringResource(R.string.features_turnos_volver)) } })
    }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = if (onReservar != null) stringResource(R.string.features_turnos_mis_turnos) else stringResource(R.string.features_turnos_turnos)) },
        bottomBar = {
            onReservar?.let { reservar ->
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                    Button(
                        onClick = reservar,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(ar.edu.unaj.am.myapplication.core.ui.R.string.core_ui_reservar_turno),
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
        avisoSincronizacion?.let {
            Text(stringResource(R.string.turnos_cached, it), modifier = Modifier.padding(16.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.features_turnos_actualizar)) }
        }
        SemanaSelector(semanaOffset = semanaOffset, onSemanaChange = onSemanaChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Todos", "Pendiente", "Cancelado", "Completado").forEach { estado ->
                FilterChip(
                    selected = filtroEstado == estado,
                    onClick = { filtroEstado = estado },
                    label = { Text(stringResource(when (estado) {
                        "Pendiente" -> R.string.turnos_filter_pending
                        "Cancelado" -> R.string.turnos_filter_canceled
                        "Completado" -> R.string.turnos_filter_completed
                        else -> R.string.turnos_filter_all
                    })) }
                )
            }
        }
        errorCompletar?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp)) }
        val contentModifier = Modifier.weight(1f).fillMaxSize()
        when (uiState) {
            TurnosListUiState.Loading -> LoadingContent(
                message = stringResource(R.string.features_turnos_cargando_turnos),
                modifier = contentModifier
            )
            is TurnosListUiState.Error -> ErrorContent(
                title = stringResource(R.string.features_turnos_no_se_pudieron_cargar_los_turnos),
                message = uiState.error.asMessage(),
                onRetry = onRetry,
                modifier = contentModifier
            )
            is TurnosListUiState.Success -> {
                val turnosVisibles = remember(uiState.turnos, filtroEstado) {
                    uiState.turnos.filter { turno ->
                        when (filtroEstado) {
                            "Pendiente" -> turno.estadoNormalizado == EstadoTurno.PENDIENTE
                            "Cancelado" -> turno.estadoNormalizado == EstadoTurno.CANCELADO
                            "Completado" -> turno.estadoNormalizado == EstadoTurno.REALIZADO
                            else -> true
                        }
                    }
                }
                val secciones = seccionesTurnos(turnosVisibles, ahoraMillis)
                if (turnosVisibles.isEmpty()) {
                    Box(modifier = contentModifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(if (uiState.turnos.isNotEmpty())
                            stringResource(R.string.turnos_empty_filter)
                            else if (onReservar != null) stringResource(R.string.features_turnos_no_tenes_turnos_reservados_para_la_semana_seleccionada) else stringResource(R.string.features_turnos_no_hay_turnos_para_la_semana_seleccionada),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = contentModifier,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        secciones.forEach { (seccion, turnos) ->
                            item(key = "seccion_$seccion", contentType = "encabezado") {
                                Text(stringResource(when (seccion) {
                                    SeccionTurnos.PENDIENTES -> R.string.turnos_section_pending
                                    SeccionTurnos.CANCELADOS -> R.string.turnos_section_canceled
                                    SeccionTurnos.COMPLETADOS -> R.string.turnos_section_completed
                                    SeccionTurnos.OTROS -> R.string.turnos_section_other
                                }), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() })
                            }
                        items(turnos, key = { it.id }) { turno ->
                            TurnoCard(turno = turno,
                                onCancelar = onCancelar?.let { { cancelar = turno } },
                                onModificar = onModificar?.let { action -> { action(turno) } },
                                editable = turno.estadoNormalizado == EstadoTurno.PENDIENTE && (turno.inicioMillis ?: 0L) > ahoraMillis,
                                operando = completandoId != null,
                                onComoLlegar = onComoLlegar?.let { abrir -> { abrir(turno) } },
                                onRecordatorio = onRecordatorio?.let { action -> { action(turno) } },
                                recordatorioActivo = turno.id in recordatoriosActivos,
                                recordatorioHabilitado = instanteRecordatorio(turno, ahoraMillis) != null,
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
