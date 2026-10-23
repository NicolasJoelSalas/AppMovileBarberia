package ar.edu.unaj.am.myapplication.features.turnos.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.time.LocalTime
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AgendaInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun FranjaHorariaFields(
    inicio: String,
    fin: String,
    onInicioChange: (String) -> Unit,
    onFinChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    superpuesta: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HoraField(inicio, onInicioChange, "Inicio", enabled, Modifier.weight(1f))
        HoraField(fin, onFinChange, "Fin", enabled, Modifier.weight(1f))
    }
    val start = runCatching { LocalTime.parse(inicio) }.getOrNull()
    val end = runCatching { LocalTime.parse(fin) }.getOrNull()
    val error = when {
        start == null || end == null -> "Elegí la hora de inicio y de fin."
        end.toSecondOfDay() - start.toSecondOfDay() < 40 * 60 -> "La franja debe durar al menos 40 minutos."
        superpuesta -> "Este horario se superpone con otra franja."
        else -> null
    }
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoraField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { abierto = true }, enabled = enabled,
        modifier = modifier.heightIn(min = 76.dp), shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.ifBlank { "Elegir hora" }, style = MaterialTheme.typography.titleMedium)
        }
    }
    if (abierto && enabled) {
        val hora = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
        val picker = rememberTimePickerState(initialHour = hora.hour,
            initialMinute = hora.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { abierto = false },
            title = { Text("Elegir hora de ${label.lowercase()}") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(state = picker)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(String.format(Locale.ROOT, "%02d:%02d", picker.hour, picker.minute))
                    abierto = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } })
    }
}
