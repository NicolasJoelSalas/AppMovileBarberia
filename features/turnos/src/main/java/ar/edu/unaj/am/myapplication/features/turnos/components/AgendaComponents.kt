package ar.edu.unaj.am.myapplication.features.turnos.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HoraField(inicio, onInicioChange, "Inicio", "09:00", enabled, Modifier.weight(1f))
        HoraField(fin, onFinChange, "Fin", "13:00", enabled, Modifier.weight(1f))
    }
}

@Composable
private fun HoraField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        label = { Text(label) }, placeholder = { Text(placeholder) },
        singleLine = true, enabled = enabled, modifier = modifier)
}
