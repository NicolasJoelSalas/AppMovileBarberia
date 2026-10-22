package ar.edu.unaj.am.myapplication.features.turnos.list

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import ar.edu.unaj.am.myapplication.core.ui.components.ReservarTurnoButton
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar

import ar.edu.unaj.am.myapplication.features.turnos.components.SemanaSelector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import ar.edu.unaj.am.myapplication.core.domain.usecase.instanteRecordatorio

@Composable
fun TurnosListRoute(viewModel: TurnoListViewModel, onReservar: (() -> Unit)? = null,
    onModificar: ((Turno) -> Unit)? = null,
    onRecordatorio: ((Turno) -> Unit)? = null, recordatoriosActivos: Set<String> = emptySet()) {
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
    semanaOffset: Int = 0,
    onSemanaChange: (Int) -> Unit = {},
    ahoraMillis: Long = System.currentTimeMillis(),
    completandoId: String? = null,
    errorCompletar: String? = null,
    onMarcarRealizado: ((Turno) -> Unit)? = null,
    onRecordatorio: ((Turno) -> Unit)? = null,
    recordatoriosActivos: Set<String> = emptySet(),
    onCancelar: ((Turno) -> Unit)? = null, onModificar: ((Turno) -> Unit)? = null
) {
    var filtroEstado by rememberSaveable { mutableStateOf("Todos") }
    var cancelar by remember { androidx.compose.runtime.mutableStateOf<Turno?>(null) }
    cancelar?.let { turno ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { cancelar = null },
            title = { Text("Cancelar turno") }, text = { Text("¿Querés cancelar este turno? Se liberará el horario.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { cancelar = null; onCancelar?.invoke(turno) }) { Text("Cancelar turno") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { cancelar = null }) { Text("Volver") } })
    }
    val context = LocalContext.current
    val abrirRuta: () -> Unit = {
        val destino = "Universidad Nacional Arturo Jauretche, Av. Calchaquí 6200, B1888 Florencio Varela, Provincia de Buenos Aires, Argentina"
        val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", destino)
            .build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay una aplicación disponible para abrir mapas.", Toast.LENGTH_LONG).show()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = if (onReservar != null) "Mis turnos" else "Turnos", actions = {
                onReservar?.let { reservar ->
                    ReservarTurnoButton(
                        onClick = reservar,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }) }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
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
                    label = { Text(estado) }
                )
            }
        }
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
                val turnosVisibles = remember(uiState.turnos, filtroEstado) {
                    uiState.turnos.filter { turno ->
                        when (filtroEstado) {
                            "Pendiente" -> turno.estado.lowercase() in listOf("pendiente", "confirmado")
                            "Cancelado" -> turno.estado.lowercase() in listOf("cancelado", "cancelada")
                            "Completado" -> turno.estado.lowercase() in listOf("realizado", "completado")
                            else -> true
                        }
                    }.sortedByDescending { it.inicioMillis ?: Long.MIN_VALUE }
                }
                if (turnosVisibles.isEmpty()) {
                    Box(modifier = contentModifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(if (uiState.turnos.isNotEmpty())
                            "No hay turnos con estado ${filtroEstado.lowercase()} para la semana seleccionada."
                            else if (onReservar != null) "No tenés turnos reservados para la semana seleccionada." else "No hay turnos para la semana seleccionada.",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = contentModifier,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(turnosVisibles, key = { it.id }) { turno ->
                            TurnoCard(turno = turno,
                                onCancelar = onCancelar?.let { { cancelar = turno } },
                                onModificar = onModificar?.let { action -> { action(turno) } },
                                editable = turno.estado.lowercase() in listOf("confirmado", "pendiente") && (turno.inicioMillis ?: 0L) > ahoraMillis,
                                operando = completandoId != null,
                                onComoLlegar = if (onReservar != null) abrirRuta else null,
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
