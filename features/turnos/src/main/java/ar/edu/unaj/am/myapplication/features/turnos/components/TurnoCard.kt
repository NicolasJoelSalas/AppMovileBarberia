package ar.edu.unaj.am.myapplication.features.turnos.components

import ar.edu.unaj.am.myapplication.features.turnos.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val horaFormatter: DateTimeFormatter
    @androidx.compose.runtime.Composable get() = DateTimeFormatter.ofPattern("HH:mm", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
private val fechaFormatter: DateTimeFormatter
    @androidx.compose.runtime.Composable get() = DateTimeFormatter.ofPattern("EEEE d MMMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])

@Composable
fun TurnoCard(turno: Turno, modifier: Modifier = Modifier, acciones: AccionesTurno = AccionesTurno(),
    onMarcarRealizado: (() -> Unit)? = null, habilitado: Boolean = false,
    completando: Boolean = false, onRecordatorio: (() -> Unit)? = null,
    recordatorioActivo: Boolean = false,
    onComoLlegar: (() -> Unit)? = null, onCancelar: (() -> Unit)? = null,
    onModificar: (() -> Unit)? = null, operando: Boolean = false) {
    val zonaDispositivo = ZoneId.systemDefault()
    val inicio = turno.inicioMillis?.let { Instant.ofEpochMilli(it).atZone(zonaDispositivo) }
    val fin = turno.finMillis?.let { Instant.ofEpochMilli(it).atZone(zonaDispositivo) }
    val realizado = turno.estadoNormalizado == EstadoTurno.REALIZADO
    val colors = MaterialTheme.colorScheme
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.features_turnos_cliente), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(turno.clienteNombre.ifBlank { stringResource(R.string.features_turnos_cliente_sin_nombre) },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            DatoTurno(Icons.Outlined.CalendarToday,
                inicio?.format(fechaFormatter)?.replaceFirstChar { it.titlecase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) }
                    ?: stringResource(R.string.features_turnos_fecha_sin_configurar))
            Surface(color = colors.primaryContainer.copy(alpha = 0.45f),
                contentColor = colors.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.features_turnos_horario_del_turno), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.turnos_time_range, inicio?.format(horaFormatter) ?: "--:--", fin?.format(horaFormatter) ?: "--:--"),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            DatoTurno(Icons.Outlined.ContentCut, turno.servicioNombre.ifBlank { stringResource(R.string.features_turnos_servicio_sin_asignar) })
            turno.referenciaCorteUrl?.let {
                Text(stringResource(R.string.turnos_reference_title), style = MaterialTheme.typography.titleSmall)
                ar.edu.unaj.am.myapplication.features.turnos.list.ReferenciaCorteImagen(it)
            }
            onComoLlegar?.takeIf { acciones.permiteConsultarRuta }?.let { abrirRuta ->
                OutlinedButton(onClick = abrirRuta,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Directions, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.features_turnos_como_llegar))
                }
            }
            if (onRecordatorio != null && acciones.admiteRecordatorios) {
                OutlinedButton(onClick = onRecordatorio,
                    enabled = acciones.recordatorioHabilitado || recordatorioActivo,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (recordatorioActivo) stringResource(R.string.features_turnos_desactivar_avisos) else stringResource(R.string.features_turnos_activar_avisos_del_turno))
                }
                Text(when {
                    recordatorioActivo -> stringResource(R.string.features_turnos_avisos_automaticos_un_dia_y_una_hora_antes_hora_local_solo_se_envian_los_que_aun_no_pasaron)
                    turno.inicioMillis == null -> stringResource(R.string.features_turnos_el_turno_necesita_una_fecha_y_hora_de_inicio)
                    !acciones.recordatorioHabilitado -> stringResource(R.string.features_turnos_la_hora_del_recordatorio_ya_paso)
                    else -> stringResource(R.string.features_turnos_avisos_un_dia_antes_y_una_hora_antes_habilita_los_permisos_para_recibirlos)
                }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (acciones.editable) {
                onModificar?.let { OutlinedButton(onClick = it, enabled = !operando, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.features_turnos_modificar_turno)) } }
                onCancelar?.let { OutlinedButton(onClick = it, enabled = !operando,
                    border = androidx.compose.foundation.BorderStroke(1.dp,
                        if (operando) colors.error.copy(alpha = 0.38f) else colors.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                    modifier = Modifier.fillMaxWidth()) { Text(if (operando) stringResource(R.string.features_turnos_guardando) else stringResource(R.string.features_turnos_cancelar_turno)) } }
            }
            if (turno.estadoNormalizado == EstadoTurno.CANCELADO) Text(stringResource(R.string.features_turnos_cancelado), color = colors.error)
            if (realizado) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary)
                    Text(stringResource(R.string.features_turnos_realizado), color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            } else if (onMarcarRealizado != null && acciones.admiteFinalizacion) {
                Button(onClick = onMarcarRealizado, enabled = habilitado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (completando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (completando) stringResource(R.string.features_turnos_guardando) else stringResource(R.string.features_turnos_marcar_realizado))
                }
                if (turno.inicioMillis == null) Text(stringResource(R.string.features_turnos_el_turno_necesita_una_fecha_y_hora_de_inicio),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                else if (!habilitado && !completando) Text(stringResource(R.string.features_turnos_disponible_desde_la_hora_de_inicio_del_turno),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DatoTurno(icon: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}
