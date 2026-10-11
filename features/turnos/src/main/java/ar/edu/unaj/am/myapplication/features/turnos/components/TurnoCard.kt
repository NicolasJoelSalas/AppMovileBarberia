package ar.edu.unaj.am.myapplication.features.turnos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zonaBarberia = ZoneId.of("America/Argentina/Buenos_Aires")
private val horaFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR"))
private val fechaFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))

@Composable
fun TurnoCard(turno: Turno, modifier: Modifier = Modifier) {
    val inicio = turno.inicioMillis?.let { Instant.ofEpochMilli(it).atZone(zonaBarberia) }
    val fin = turno.finMillis?.let { Instant.ofEpochMilli(it).atZone(zonaBarberia) }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = turno.clienteNombre.ifBlank { "Cliente sin nombre" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = inicio?.format(fechaFormatter) ?: "Fecha sin configurar",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Horario: ${inicio?.format(horaFormatter) ?: "--:--"} a ${fin?.format(horaFormatter) ?: "--:--"}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = turno.servicioNombre.ifBlank { "Servicio sin asignar" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
