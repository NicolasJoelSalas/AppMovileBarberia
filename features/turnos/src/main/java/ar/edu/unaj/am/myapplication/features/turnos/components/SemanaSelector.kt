package ar.edu.unaj.am.myapplication.features.turnos.components

import ar.edu.unaj.am.myapplication.features.turnos.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SemanaSelector(semanaOffset: Int, onSemanaChange: (Int) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(stringResource(R.string.features_turnos_esta_semana), stringResource(R.string.features_turnos_proxima_semana)).forEachIndexed { index, titulo ->
                val selected = semanaOffset == index
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
                    contentColor = (if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                        .copy(alpha = if (enabled) 1f else 0.38f)) {
                    Row(Modifier.selectable(selected = selected, enabled = enabled,
                        role = Role.Tab, onClick = { onSemanaChange(index) })
                        .heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        Icon(if (index == 0) Icons.Outlined.CalendarToday else Icons.Outlined.DateRange,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(titulo, style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}
