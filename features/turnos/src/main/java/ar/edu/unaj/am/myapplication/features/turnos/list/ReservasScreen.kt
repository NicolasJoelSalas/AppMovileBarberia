package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ReservasRoute(viewModel: ReservasViewModel,
    turnosCliente: @Composable () -> Unit,
    agendaBarbero: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    if (state.catalogo?.esBarbero == true) agendaBarbero()
    else if (state.catalogo != null) turnosCliente()
    else if (error != null) ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent(
        title = "No se pudieron cargar tus turnos", message = error.asMessage(), onRetry = viewModel::cargar)
    else ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent(message = "Cargando turnos…")
}

@Composable
fun ReservarTurnoRoute(viewModel: ReservasViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.reservaConfirmada) {
        if (state.reservaConfirmada) onBack()
    }
    androidx.activity.compose.BackHandler(enabled = state.reservando) { }
    ReservasScreen(state, viewModel, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservasScreen(state: ReservasUiState, vm: ReservasViewModel, onBack: () -> Unit) {
    val zone = ZoneId.of(state.semana?.zonaHoraria ?: "America/Argentina/Buenos_Aires")
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.catalogo != null && !state.loading) vm.refrescar()
    }
    LaunchedEffect(state.semana?.inicioSemanaMillis, zone) {
        if (state.semana != null) {
            while (true) {
                val now = java.time.ZonedDateTime.now(zone)
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
                delay(java.time.Duration.between(now, midnight).toMillis().coerceAtLeast(1000))
                vm.refrescar()
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Reservar turno") }, actions = {
        TextButton(onClick = onBack, enabled = !state.reservando) { Text("Volver") }
    }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Reservas para dos semanas · lunes a sábado", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.semanaOffset == 0, onClick = { vm.semana(0) },
                        enabled = !state.reservando, label = { Text("Semana actual") })
                    FilterChip(selected = state.semanaOffset == 1, onClick = { vm.semana(1) },
                        enabled = !state.reservando, label = { Text("Próxima semana") })
                }
                Text("Elegí barbero, servicio y un horario disponible.")
            }
            state.catalogo?.let { catalogo ->
                item {
                    Selector("Barbero", state.barberoId, catalogo.barberos.map { it.id to it.nombre },
                        !state.reservando, vm::barbero)
                    Spacer(Modifier.height(8.dp))
                    Selector("Servicio", state.servicioId,
                        catalogo.servicios.map { it.id to "${it.nombre} · ${it.duracion} min" },
                        !state.reservando, vm::servicio)
                    if (catalogo.barberos.isEmpty()) Text("No hay barberos habilitados.")
                    if (catalogo.servicios.isEmpty()) Text("No hay servicios habilitados de 40 minutos.")
                }
            }
            if (state.loading) item { CircularProgressIndicator() }
            state.mensaje?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
            state.error?.let { error ->
                item {
                    Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { if (state.catalogo == null) vm.cargar() else vm.refrescar() },
                        enabled = !state.reservando && !state.loading) { Text("Actualizar") }
                }
            }
            state.semana?.let { semana ->
                val monday = Instant.ofEpochMilli(semana.inicioSemanaMillis).atZone(zone).toLocalDate()
                for (offset in 0L..5L) {
                    val day = monday.plusDays(offset)
                    val slots = semana.horarios.filter {
                        Instant.ofEpochMilli(it.inicioMillis).atZone(zone).toLocalDate() == day
                    }
                    item(key = "dia-$day") {
                        Text(day.format(dayFormat), style = MaterialTheme.typography.titleMedium)
                        if (slots.isEmpty()) Text("Sin horarios disponibles.")
                    }
                    items(slots, key = { it.inicioMillis }) { slot ->
                        OutlinedButton(
                            onClick = { vm.seleccionar(slot) }, enabled = !state.reservando,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${Instant.ofEpochMilli(slot.inicioMillis).atZone(zone).format(hourFormat)} a " +
                                Instant.ofEpochMilli(slot.finMillis).atZone(zone).format(hourFormat))
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = vm::refrescar, enabled = !state.loading && !state.reservando) {
                        Text("Actualizar disponibilidad")
                    }
                }
            }
        }
    }
    state.selected?.let { slot ->
        val start = Instant.ofEpochMilli(slot.inicioMillis).atZone(zone)
        AlertDialog(
            onDismissRequest = { vm.seleccionar(null) },
            title = { Text("Confirmar reserva") },
            text = {
                Column {
                    Text(state.catalogo?.barberos?.find { it.id == state.barberoId }?.nombre.orEmpty())
                    Text(state.catalogo?.servicios?.find { it.id == state.servicioId }?.nombre.orEmpty())
                    Text(start.format(dayFormat) + " · " + start.format(hourFormat))
                    OutlinedTextField(value = state.nombre, onValueChange = vm::nombre,
                        label = { Text("Tu nombre") }, singleLine = true, enabled = !state.reservando)
                    state.error?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::confirmar,
                    enabled = !state.reservando && state.nombre.isNotBlank() && state.nombre.length <= 120) {
                    Text(if (state.reservando) "Reservando…" else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.seleccionar(null) }, enabled = !state.reservando) { Text("Volver") }
            }
        )
    }
}

@Composable
private fun Selector(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean,
    select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: " + (options.find { it.first == value }?.second ?: "Seleccionar"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.second) }, onClick = {
                    expanded = false
                    select(option.first)
                })
            }
        }
    }
}
