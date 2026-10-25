package ar.edu.unaj.am.myapplication.features.cuenta

import ar.edu.unaj.am.myapplication.features.cuenta.R

import androidx.compose.ui.res.stringResource

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

private fun dinero(centavos: Long, locale: Locale): String = NumberFormat.getCurrencyInstance(locale)
    .apply { currency = java.util.Currency.getInstance("ARS") }
    .format(BigDecimal.valueOf(centavos, 2))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReporteGananciasContent(state: ReporteGananciasUiState, onPeriodo: (Boolean) -> Unit,
    onMover: (Int) -> Unit, onReintentar: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.features_cuenta_reporte_de_ganancias), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.features_cuenta_consulta_tus_ingresos_y_servicios_realizados), style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant)
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { index, mensual ->
            SegmentedButton(selected = state.mensual == mensual, onClick = { onPeriodo(mensual) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer),
                label = { Text(if (mensual) stringResource(R.string.features_cuenta_mensual) else stringResource(R.string.features_cuenta_semanal)) })
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMover(-1) }) {
                Icon(Icons.Outlined.ChevronLeft,
                    contentDescription = if (state.mensual) stringResource(R.string.features_cuenta_mes_anterior) else stringResource(R.string.features_cuenta_semana_anterior))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val titulo = state.reporte?.let {
                    if (state.mensual) it.desde.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                        .replaceFirstChar { letra -> letra.titlecase(locale) }
                    else stringResource(R.string.cuenta_week_of, it.desde.format(DateTimeFormatter.ofPattern("d MMM", locale)))
                } ?: if (state.mensual) stringResource(R.string.features_cuenta_periodo_mensual) else stringResource(R.string.features_cuenta_periodo_semanal)
                Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
                TextButton(onClick = { onPeriodo(state.mensual) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(if (state.offset == 0) stringResource(R.string.features_cuenta_periodo_actual) else if (state.mensual) stringResource(R.string.features_cuenta_volver_a_este_mes) else stringResource(R.string.features_cuenta_volver_a_esta_semana),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = { onMover(1) }, enabled = state.offset < 0) {
                Icon(Icons.Outlined.ChevronRight,
                    contentDescription = if (state.mensual) stringResource(R.string.features_cuenta_mes_siguiente) else stringResource(R.string.features_cuenta_semana_siguiente))
            }
        }
    }
    if (state.cargando) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }
    state.error?.let {
        Text(if (state.reporte != null) stringResource(R.string.cuenta_cached, it.asMessage()) else it.asMessage(), color = colors.error)
        OutlinedButton(onClick = onReintentar) { Text(stringResource(R.string.features_cuenta_reintentar)) }
    }
    state.reporte?.let { reporte ->
        val formato = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (state.mensual) Icons.Outlined.CalendarMonth else Icons.Outlined.DateRange,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(if (state.mensual) stringResource(R.string.features_cuenta_ingresos_del_mes) else stringResource(R.string.features_cuenta_ingresos_de_la_semana),
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(dinero(reporte.totalCentavos, locale), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.features_cuenta_pesos_argentinos_ars), style = MaterialTheme.typography.labelMedium)
                HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = 0.2f))
                Text(stringResource(R.string.cuenta_completed_services, reporte.realizados.size), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.cuenta_date_range, reporte.desde.format(formato), reporte.hasta.format(formato)),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.features_cuenta_el_total_suma_los_importes_registrados_de_servicios_realizados_segun_la_fecha_del_turno),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (reporte.sinImporte > 0) Surface(color = colors.errorContainer,
            contentColor = colors.onErrorContainer, shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.cuenta_missing_amounts, reporte.sinImporte),
                modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.features_cuenta_detalle_de_servicios), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${reporte.realizados.size}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            if (reporte.realizados.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(32.dp),
                        tint = colors.primary)
                    Text(stringResource(R.string.features_cuenta_sin_servicios_realizados), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.features_cuenta_los_servicios_que_marques_como_realizados_apareceran_en_el_periodo_correspondiente),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                }
            } else reporte.realizados.forEachIndexed { index, turno ->
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(turno.clienteNombre.ifBlank { stringResource(R.string.features_cuenta_cliente_sin_nombre) },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(turno.servicioNombre.ifBlank { stringResource(R.string.features_cuenta_servicio_sin_asignar) },
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(turno.inicioMillis?.let { Instant.ofEpochMilli(it)
                            .atZone(ZoneId.of("America/Argentina/Buenos_Aires")).format(
                                DateTimeFormatter.ofPattern("d MMM · HH:mm", locale)) } ?: stringResource(R.string.features_cuenta_sin_fecha),
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant)
                        Text(turno.precioCobradoCentavos?.takeIf { it >= 0 }?.let { dinero(it, locale) } ?: stringResource(R.string.features_cuenta_sin_importe),
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

