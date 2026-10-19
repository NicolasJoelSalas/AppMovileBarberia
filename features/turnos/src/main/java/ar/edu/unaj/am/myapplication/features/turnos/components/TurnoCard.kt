package ar.edu.unaj.am.myapplication.features.turnos.components

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
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val horaFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR"))
private val fechaFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))

@Composable
fun TurnoCard(turno: Turno, modifier: Modifier = Modifier,
    onMarcarRealizado: (() -> Unit)? = null, habilitado: Boolean = false,
    completando: Boolean = false, onRecordatorio: (() -> Unit)? = null,
    recordatorioActivo: Boolean = false, recordatorioHabilitado: Boolean = false,
    onComoLlegar: (() -> Unit)? = null) {
    val zonaBarberia = ZoneId.systemDefault()
    val inicio = turno.inicioMillis?.let { Instant.ofEpochMilli(it).atZone(zonaBarberia) }
    val fin = turno.finMillis?.let { Instant.ofEpochMilli(it).atZone(zonaBarberia) }
    val realizado = turno.estado.lowercase() in listOf("realizado", "completado")
    val colors = MaterialTheme.colorScheme
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CLIENTE", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(turno.clienteNombre.ifBlank { "Cliente sin nombre" },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            DatoTurno(Icons.Outlined.CalendarToday,
                inicio?.format(fechaFormatter)?.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-AR")) }
                    ?: "Fecha sin configurar")
            Surface(color = colors.primaryContainer.copy(alpha = 0.45f),
                contentColor = colors.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Horario del turno", style = MaterialTheme.typography.labelMedium)
                        Text("${inicio?.format(horaFormatter) ?: "--:--"} a ${fin?.format(horaFormatter) ?: "--:--"}",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            DatoTurno(Icons.Outlined.ContentCut, turno.servicioNombre.ifBlank { "Servicio sin asignar" })
            onComoLlegar?.let { abrirRuta ->
                OutlinedButton(onClick = abrirRuta,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Directions, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cómo llegar")
                }
            }
            if (onRecordatorio != null && !realizado &&
                turno.estado.lowercase() in listOf("confirmado", "pendiente")) {
                OutlinedButton(onClick = onRecordatorio,
                    enabled = recordatorioHabilitado || recordatorioActivo,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (recordatorioActivo) "Desactivar avisos" else "Activar avisos del turno")
                }
                Text(when {
                    recordatorioActivo -> "Avisos automáticos un día y una hora antes (hora local). Solo se envían los que aún no pasaron."
                    turno.inicioMillis == null -> "El turno necesita una fecha y hora de inicio."
                    !recordatorioHabilitado -> "La hora del recordatorio ya pasó."
                    else -> "Avisos un día antes y una hora antes. Habilitá los permisos para recibirlos."
                }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (realizado) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary)
                    Text("Realizado", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            } else if (onMarcarRealizado != null && turno.estado.lowercase() in listOf("confirmado", "pendiente")) {
                Button(onClick = onMarcarRealizado, enabled = habilitado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (completando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (completando) "Guardando…" else "Marcar realizado")
                }
                if (turno.inicioMillis == null) Text("El turno necesita una fecha y hora de inicio.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                else if (!habilitado && !completando) Text("Disponible desde la hora de inicio del turno.",
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
