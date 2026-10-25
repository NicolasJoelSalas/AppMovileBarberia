package ar.edu.unaj.am.myapplication.features.turnos.agenda

import ar.edu.unaj.am.myapplication.features.turnos.R

import androidx.compose.ui.res.stringResource

import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.features.turnos.components.AgendaInfoRow
import ar.edu.unaj.am.myapplication.features.turnos.components.FranjaHorariaFields
import ar.edu.unaj.am.myapplication.features.turnos.components.SemanaSelector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
        AppTopBar(title = stringResource(R.string.features_turnos_gestionar_semana), actions = {
            TextButton(onClick = salir, enabled = !state.guardando) { Text(stringResource(R.string.features_turnos_volver)) }
        })
    }, bottomBar = {
        if (state.agenda != null) {
            Box(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(onClick = viewModel::guardar, enabled = !state.guardando && !state.cargando,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .shadow(8.dp, CircleShape), shape = CircleShape) {
                    if (state.guardando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.guardando) stringResource(R.string.features_turnos_guardando_y_publicando) else stringResource(R.string.features_turnos_guardar_y_publicar_semana))
                }
            }
        }
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SemanaSelector(semanaOffset = state.semanaOffset, onSemanaChange = viewModel::semana,
                    enabled = !state.guardando && !state.cargando)
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AgendaInfoRow(Icons.Outlined.CalendarToday, stringResource(R.string.features_turnos_hoy), hoy.format(dateFormat))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        AgendaInfoRow(Icons.Outlined.DateRange, stringResource(R.string.features_turnos_semana_seleccionada),
                            stringResource(R.string.turnos_date_range, lunes.format(dateFormat), lunes.plusDays(5).format(dateFormat)))
                    }
                }
            }
            if (state.cargando) item { CircularProgressIndicator() }
            state.error?.let { error -> item {
                Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
                if (state.agenda == null) OutlinedButton(onClick = viewModel::cargar) { Text(stringResource(R.string.features_turnos_reintentar)) }
            } }
            state.mensaje?.let { message -> item { Text(message.asString(), color = MaterialTheme.colorScheme.primary) } }
            state.agenda?.dias?.forEachIndexed { index, day ->
                val nombre = listOf(R.string.features_turnos_lunes, R.string.features_turnos_martes, R.string.features_turnos_miercoles, R.string.features_turnos_jueves, R.string.features_turnos_viernes, R.string.features_turnos_sabado)[index]
                item(key = nombre) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(nombre), style = MaterialTheme.typography.titleMedium)
                                    Text(lunes.plusDays(index.toLong()).format(dateFormat),
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(if (day.habilitado) stringResource(R.string.features_turnos_abierto) else stringResource(R.string.features_turnos_cerrado))
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(checked = day.habilitado, onCheckedChange = { viewModel.habilitar(index, it) },
                                    enabled = !state.guardando)
                            }
                            if (day.habilitado) {
                                day.franjas.forEachIndexed { span, franja ->
                                    HorizontalDivider()
                                    Text(stringResource(R.string.turnos_time_range_number, span + 1), style = MaterialTheme.typography.labelLarge)
                                    FranjaHorariaFields(
                                        inicio = franja.inicio, fin = franja.fin,
                                        onInicioChange = { viewModel.hora(index, span, true, it) },
                                        onFinChange = { viewModel.hora(index, span, false, it) },
                                        enabled = !state.guardando,
                                        superpuesta = day.franjas.withIndex().any { (other, item) ->
                                            runCatching {
                                                val start = java.time.LocalTime.parse(franja.inicio)
                                                val end = java.time.LocalTime.parse(franja.fin)
                                                val a = java.time.LocalTime.parse(item.inicio)
                                                val b = java.time.LocalTime.parse(item.fin)
                                                other != span && start < end && a < b && start < b && a < end
                                            }.getOrDefault(false)
                                        }
                                    )
                                    TextButton(onClick = { viewModel.quitar(index, span) }, enabled = !state.guardando) {
                                        Text(stringResource(R.string.turnos_remove_time_range, span + 1))
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.agregar(index) }, enabled = !state.guardando,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.features_turnos_agregar_franja))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmarSalida) AlertDialog(onDismissRequest = { confirmarSalida = false },
        title = { Text(stringResource(R.string.features_turnos_salir_sin_guardar)) }, text = { Text(stringResource(R.string.features_turnos_tenes_cambios_pendientes_en_tu_agenda)) },
        confirmButton = { TextButton(onClick = { confirmarSalida = false; onBack() }) { Text(stringResource(R.string.features_turnos_salir_sin_guardar)) } },
        dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text(stringResource(R.string.features_turnos_seguir_editando)) } })
}
