package ar.edu.unaj.am.myapplication.features.turnos.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import ar.edu.unaj.am.myapplication.core.domain.model.HorarioDisponible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.features.turnos.components.SemanaSelector
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
    Scaffold(topBar = { AppTopBar(title = if (state.turnoId != null) "Modificar turno" else "Reservar turno", actions = {
        TextButton(onClick = onBack, enabled = !state.reservando) { Text("Volver") }
    }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.catalogo?.sinConexion == true) {
                item {
                    Text("Necesitás conexión para consultar disponibilidad y confirmar cambios. Tus turnos guardados siguen disponibles en Mis turnos.")
                    TextButton(onClick = vm::cargar) { Text("Reintentar conexión") }
                }
            }
            state.inicioOriginal?.let { inicio ->
                item {
                    val fecha = Instant.ofEpochMilli(inicio).atZone(zone)
                    Text("Horario actual: " + fecha.format(dayFormat) + " · " + fecha.format(hourFormat))
                    if (state.barberoId == state.barberoOriginal) {
                        OutlinedButton(onClick = { vm.seleccionar(ar.edu.unaj.am.myapplication.core.domain.model.HorarioDisponible(inicio, inicio + 2400000)) },
                            enabled = !state.loading && !state.reservando && state.servicioId.isNotBlank()) {
                            Text("Conservar horario actual")
                        }
                    }
                }
            }
            item {
                SemanaSelector(semanaOffset = state.semanaOffset, onSemanaChange = vm::semana,
                    enabled = !state.reservando)
            }
            state.catalogo?.let { catalogo ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Elegí tu turno", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("Seleccioná barbero, servicio y un horario disponible de lunes a sábado.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Selector("Barbero", state.barberoId, catalogo.barberos.map { it.id to it.nombre },
                                !state.reservando, Icons.Outlined.Person, vm::barbero)
                            Selector("Servicio", state.servicioId,
                                catalogo.servicios.map { it.id to "${it.nombre} · ${it.duracion} min" },
                                !state.reservando, Icons.Outlined.ContentCut, vm::servicio)
                            if (catalogo.barberos.isEmpty()) Text("No hay barberos habilitados.")
                            if (catalogo.servicios.isEmpty()) Text("No hay servicios habilitados de 40 minutos.")
                        }
                    }
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
                        key(day, state.barberoId, state.servicioId) {
                            HorariosDelDia(
                                fecha = day.format(dayFormat).replaceFirstChar { it.titlecase() },
                                slots = slots,
                                zone = zone,
                                enabled = !state.loading && !state.reservando,
                                onSelect = vm::seleccionar
                            )
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
            title = { Text(if (state.turnoId != null) "Confirmar modificación" else "Confirmar reserva") },
            text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text(if (state.reservando) "Reservando…" else if (state.turnoId != null) "Guardar cambios" else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.seleccionar(null) }, enabled = !state.reservando) { Text("Volver") }
            }
        )
    }
}

@Composable
private fun HorariosDelDia(
    fecha: String,
    slots: List<HorarioDisponible>,
    zone: ZoneId,
    enabled: Boolean,
    onSelect: (HorarioDisponible) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val available = slots.isNotEmpty()
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { expanded = !expanded },
        enabled = enabled && available,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainerLow,
            disabledContentColor = colors.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null,
                tint = if (available) colors.primary else colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fecha, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    when (slots.size) {
                        0 -> "Sin horarios disponibles"
                        1 -> "1 horario disponible"
                        else -> "${slots.size} horarios disponibles"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available) colors.primary else colors.onSurfaceVariant
                )
            }
            if (available) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Ocultar horarios" else "Ver horarios"
                )
            }
        }
        AnimatedVisibility(visible = expanded && available) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = colors.outlineVariant)
                Text("Elegí un horario", style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
                slots.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { slot ->
                            OutlinedButton(
                                onClick = { onSelect(slot) },
                                enabled = enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(Instant.ofEpochMilli(slot.inicioMillis).atZone(zone).format(hourFormat),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("Hasta " + Instant.ofEpochMilli(slot.finMillis).atZone(zone).format(hourFormat),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Selector(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean,
    icon: ImageVector, select: (String) -> Unit, hint: String = "Seleccionar") {
    var expanded by remember { mutableStateOf(false) }
    val canSelect = enabled && options.isNotEmpty()
    val optionTextStyle = MaterialTheme.typography.bodyLarge
    ExposedDropdownMenuBox(expanded = expanded && canSelect,
        onExpandedChange = { if (canSelect) expanded = it }) {
        OutlinedTextField(
            value = options.find { it.first == value }?.second ?: if (value.isEmpty()) hint else "",
            onValueChange = {}, readOnly = true, enabled = canSelect,
            textStyle = optionTextStyle.copy(textAlign = TextAlign.Center),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = canSelect).fillMaxWidth(),
            label = { Text(label) }, placeholder = {
                Text(hint, modifier = Modifier.fillMaxWidth(),
                    style = optionTextStyle, textAlign = TextAlign.Center)
            },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canSelect) },
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded && canSelect, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = {
                    Text(option.second, modifier = Modifier.fillMaxWidth(),
                        style = optionTextStyle, textAlign = TextAlign.Center)
                }, onClick = {
                    expanded = false
                    select(option.first)
                })
            }
        }
    }
}
