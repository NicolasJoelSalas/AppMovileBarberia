package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.features.turnos.R

import androidx.compose.ui.res.stringResource

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.features.turnos.components.SemanaSelector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormat: DateTimeFormatter
    @Composable get() = DateTimeFormatter.ofPattern("EEEE d MMMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun HorariosReservaScreen(state: ReservasUiState, vm: ReservasViewModel, onBack: () -> Unit) {
    val zone = ZoneId.of(state.semana?.zonaHoraria ?: "America/Argentina/Buenos_Aires")
    Scaffold(topBar = {
        AppTopBar(title = stringResource(R.string.turnos_choose_datetime), navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.reservando) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.features_turnos_volver))
            }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.inicioOriginal?.let { inicio ->
                item {
                    val fecha = Instant.ofEpochMilli(inicio).atZone(zone)
                    Text(stringResource(R.string.turnos_current_time, fecha.format(dayFormat), fecha.format(hourFormat)))
                    if (state.barberoId == state.barberoOriginal) {
                        OutlinedButton(onClick = { vm.seleccionar(ar.edu.unaj.am.myapplication.core.domain.model.HorarioDisponible(inicio, inicio + 2400000)) },
                            enabled = !state.loading && !state.reservando && state.servicioId.isNotBlank()) {
                            Text(stringResource(R.string.features_turnos_conservar_horario_actual))
                        }
                    }
                }
            }
            item {
                SemanaSelector(semanaOffset = state.semanaOffset, onSemanaChange = vm::semana,
                    enabled = !state.reservando)
            }
            if (state.loading) item { CircularProgressIndicator() }
            state.error?.let { error ->
                item {
                    Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = vm::refrescar, enabled = !state.loading && !state.reservando) {
                        Text(stringResource(R.string.features_turnos_actualizar))
                    }
                }
            }
            state.semana?.let { semana ->
                if (!state.loading && semana.horarios.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Schedule, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text(stringResource(R.string.turnos_week_empty_title),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.turnos_week_empty_body),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { vm.semana(1 - state.semanaOffset) }, enabled = !state.reservando) {
                                    Text(stringResource(if (state.semanaOffset == 0) R.string.turnos_see_next_week else R.string.turnos_see_this_week))
                                }
                            }
                        }
                    }
                }
                val monday = Instant.ofEpochMilli(semana.inicioSemanaMillis).atZone(zone).toLocalDate()
                for (offset in 0L..5L) {
                    val day = monday.plusDays(offset)
                    val slots = semana.horarios.filter {
                        Instant.ofEpochMilli(it.inicioMillis).atZone(zone).toLocalDate() == day
                    }
                    if (slots.isEmpty()) continue
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
            }
        }
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
                        0 -> stringResource(R.string.features_turnos_sin_horarios_disponibles)
                        1 -> stringResource(R.string.features_turnos_1_horario_disponible)
                        else -> stringResource(R.string.turnos_available_times, slots.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available) colors.primary else colors.onSurfaceVariant
                )
            }
            if (available) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.features_turnos_ocultar_horarios) else stringResource(R.string.features_turnos_ver_horarios)
                )
            }
        }
        AnimatedVisibility(visible = expanded && available) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = colors.outlineVariant)
                Text(stringResource(R.string.features_turnos_elegi_un_horario), style = MaterialTheme.typography.labelMedium,
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
                                    Text(stringResource(R.string.turnos_until, Instant.ofEpochMilli(slot.finMillis).atZone(zone).format(hourFormat)),
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

