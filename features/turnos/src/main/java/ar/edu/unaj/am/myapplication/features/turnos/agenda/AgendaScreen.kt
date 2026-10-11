package ar.edu.unaj.am.myapplication.features.turnos.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaRoute(viewModel: AgendaViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember(state.agenda?.zonaHoraria) {
        runCatching { ZoneId.of(state.agenda?.zonaHoraria ?: "America/Argentina/Buenos_Aires") }
            .getOrDefault(ZoneId.of("America/Argentina/Buenos_Aires"))
    }
    val hoy by produceState(initialValue = LocalDate.now(zone), key1 = zone) {
        while (true) {
            val now = ZonedDateTime.now(zone)
            value = now.toLocalDate()
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone))
                .toMillis().coerceAtLeast(1000L))
        }
    }
    val lunes = state.agenda?.inicioSemana?.let(LocalDate::parse)
        ?: hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(state.semanaOffset.toLong())
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var confirmarSalida by remember { mutableStateOf(false) }
    val salir = {
        if (!state.guardando) {
            if (viewModel.tieneCambiosPendientes()) confirmarSalida = true else onBack()
        }
    }
    BackHandler(onBack = salir)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Gestionar semana", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            actions = {
            TextButton(onClick = salir, enabled = !state.guardando) { Text("Volver") }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.semanaOffset == 0, onClick = { viewModel.semana(0) },
                        enabled = !state.guardando && !state.cargando, label = { Text("Semana actual") })
                    FilterChip(selected = state.semanaOffset == 1, onClick = { viewModel.semana(1) },
                        enabled = !state.guardando && !state.cargando, label = { Text("Próxima semana") })
                }
                Text("Hoy: ${hoy.format(dateFormat)}")
                Text("Semana del ${lunes.format(dateFormat)} al ${lunes.plusDays(5).format(dateFormat)}")
            }
            if (state.cargando) item { CircularProgressIndicator() }
            state.error?.let { error -> item {
                Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
                if (state.agenda == null) OutlinedButton(onClick = viewModel::cargar) { Text("Reintentar") }
            } }
            state.mensaje?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
            state.agenda?.dias?.forEachIndexed { index, day ->
                val nombre = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")[index]
                item(key = nombre) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(nombre, style = MaterialTheme.typography.titleMedium)
                                    Text(lunes.plusDays(index.toLong()).format(dateFormat),
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(if (day.habilitado) "Abierto" else "Cerrado")
                                Switch(checked = day.habilitado, onCheckedChange = { viewModel.habilitar(index, it) },
                                    enabled = !state.guardando)
                            }
                            if (day.habilitado) {
                                day.franjas.forEachIndexed { span, franja ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = franja.inicio,
                                            onValueChange = { viewModel.hora(index, span, true, it) },
                                            label = { Text("Inicio") }, placeholder = { Text("09:00") },
                                            singleLine = true, enabled = !state.guardando, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = franja.fin,
                                            onValueChange = { viewModel.hora(index, span, false, it) },
                                            label = { Text("Fin") }, placeholder = { Text("13:00") },
                                            singleLine = true, enabled = !state.guardando, modifier = Modifier.weight(1f))
                                    }
                                    TextButton(onClick = { viewModel.quitar(index, span) }, enabled = !state.guardando) {
                                        Text("Quitar franja ${span + 1}")
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.agregar(index) }, enabled = !state.guardando) {
                                    Text("Agregar franja")
                                }
                            }
                        }
                    }
                }
            }
            if (state.agenda != null) item {
                Button(onClick = viewModel::guardar, enabled = !state.guardando && !state.cargando,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.guardando) "Guardando y publicando…" else "Guardar y publicar semana")
                }
            }
        }
    }
    if (confirmarSalida) AlertDialog(onDismissRequest = { confirmarSalida = false },
        title = { Text("¿Salir sin guardar?") }, text = { Text("Tenés cambios pendientes en tu agenda.") },
        confirmButton = { TextButton(onClick = { confirmarSalida = false; onBack() }) { Text("Salir sin guardar") } },
        dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Seguir editando") } })
}
