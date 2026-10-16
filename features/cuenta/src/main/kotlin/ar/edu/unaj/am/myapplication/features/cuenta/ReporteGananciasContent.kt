package ar.edu.unaj.am.myapplication.features.cuenta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun dinero(centavos: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"))
    .format(BigDecimal.valueOf(centavos, 2))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReporteGananciasContent(state: ReporteGananciasUiState, onPeriodo: (Boolean) -> Unit,
    onMover: (Int) -> Unit, onReintentar: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = Locale.forLanguageTag("es-AR")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Reporte de ganancias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Consultá tus ingresos y servicios realizados.", style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant)
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { index, mensual ->
            SegmentedButton(selected = state.mensual == mensual, onClick = { onPeriodo(mensual) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer),
                label = { Text(if (mensual) "Mensual" else "Semanal") })
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMover(-1) }) {
                Icon(Icons.Outlined.ChevronLeft,
                    contentDescription = if (state.mensual) "Mes anterior" else "Semana anterior")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val titulo = state.reporte?.let {
                    if (state.mensual) it.desde.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                        .replaceFirstChar { letra -> letra.titlecase(locale) }
                    else "Semana del ${it.desde.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
                } ?: if (state.mensual) "Período mensual" else "Período semanal"
                Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
                TextButton(onClick = { onPeriodo(state.mensual) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(if (state.offset == 0) "Período actual" else if (state.mensual) "Volver a este mes" else "Volver a esta semana",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = { onMover(1) }, enabled = state.offset < 0) {
                Icon(Icons.Outlined.ChevronRight,
                    contentDescription = if (state.mensual) "Mes siguiente" else "Semana siguiente")
            }
        }
    }
    if (state.cargando) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }
    state.error?.let {
        Text(it.asMessage(), color = colors.error)
        OutlinedButton(onClick = onReintentar) { Text("Reintentar") }
    }
    state.reporte?.let { reporte ->
        val formato = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (state.mensual) Icons.Outlined.CalendarMonth else Icons.Outlined.DateRange,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(if (state.mensual) "Ingresos del mes" else "Ingresos de la semana",
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(dinero(reporte.totalCentavos), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
                Text("Pesos argentinos (ARS)", style = MaterialTheme.typography.labelMedium)
                HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = 0.2f))
                Text("${reporte.realizados.size} servicios realizados", style = MaterialTheme.typography.titleSmall)
                Text("${reporte.desde.format(formato)} al ${reporte.hasta.format(formato)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("El total suma los importes registrados de servicios realizados, según la fecha del turno.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (reporte.sinImporte > 0) Surface(color = colors.errorContainer,
            contentColor = colors.onErrorContainer, shape = RoundedCornerShape(12.dp)) {
            Text("${reporte.sinImporte} servicios sin importe registrado. El total es parcial.",
                modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Detalle de servicios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${reporte.realizados.size}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            if (reporte.realizados.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(32.dp),
                        tint = colors.primary)
                    Text("Sin servicios realizados", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("Los servicios que marques como realizados aparecerán en el período correspondiente.",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                }
            } else reporte.realizados.forEachIndexed { index, turno ->
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(turno.clienteNombre.ifBlank { "Cliente sin nombre" },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(turno.servicioNombre.ifBlank { "Servicio sin asignar" },
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(turno.inicioMillis?.let { Instant.ofEpochMilli(it)
                            .atZone(ZoneId.of("America/Argentina/Buenos_Aires")).format(
                                DateTimeFormatter.ofPattern("d MMM · HH:mm", locale)) } ?: "Sin fecha",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant)
                        Text(turno.precioCobradoCentavos?.takeIf { it >= 0 }?.let(::dinero) ?: "Sin importe",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = colors.primary)
                    }
                }
                if (index < reporte.realizados.lastIndex) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            }
        }
    }
}

