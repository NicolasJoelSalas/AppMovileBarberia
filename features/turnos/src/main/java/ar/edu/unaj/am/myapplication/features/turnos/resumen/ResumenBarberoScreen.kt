package ar.edu.unaj.am.myapplication.features.turnos.resumen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.ui.components.*
import ar.edu.unaj.am.myapplication.features.turnos.components.TurnoCard
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private fun dinero(centavos: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"))
    .format(BigDecimal.valueOf(centavos, 2))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenBarberoRoute(viewModel: ResumenBarberoViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmarId by rememberSaveable { mutableStateOf<String?>(null) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reintentar() }
    LaunchedEffect(Unit) {
        while (true) {
            ahora = System.currentTimeMillis()
            viewModel.actualizarSemana()
            delay(30_000)
        }
    }
    val confirmar = state.resumen?.pendientes?.find { it.id == confirmarId }
    if (confirmar != null) {
        AlertDialog(onDismissRequest = { confirmarId = null }, title = { Text("Completar turno") },
            text = { Text("¿Realizaste el servicio de ${confirmar.clienteNombre}? Se registrarán ${dinero(confirmar.precioServicioCentavos ?: 0)} en esta semana.") },
            confirmButton = { TextButton(onClick = {
                confirmarId = null; viewModel.marcarRealizado(confirmar)
            }) { Text("Marcar realizado") } },
            dismissButton = { TextButton(onClick = { confirmarId = null }) { Text("Cancelar") } })
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Mi resumen", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        actions = { TextButton(onClick = viewModel::reintentar) { Text("Actualizar") }
            TextButton(onClick = onBack) { Text("Volver") } }) }) { padding ->
        when {
            state.cargando -> LoadingContent(message = "Cargando tu resumen…", modifier = Modifier.padding(padding))
            state.error != null -> ErrorContent(title = "No se pudo cargar tu resumen",
                message = state.error!!.asMessage(), onRetry = viewModel::reintentar,
                modifier = Modifier.padding(padding))
            else -> state.resumen?.let { resumen ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { viewModel.semana(state.semanaOffset - 1) }) { Text("Anterior") }
                            TextButton(onClick = { viewModel.semana(0) }) { Text("Esta semana") }
                            TextButton(enabled = state.semanaOffset < 1,
                                onClick = { viewModel.semana(state.semanaOffset + 1) }) { Text("Siguiente") }
                        }
                        val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        Text("${resumen.inicioSemana.format(formato)} al ${resumen.inicioSemana.plusDays(6).format(formato)}",
                            style = MaterialTheme.typography.titleMedium)
                    }
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Ganancias registradas", style = MaterialTheme.typography.titleMedium)
                                Text(dinero(resumen.gananciasCentavos), style = MaterialTheme.typography.headlineMedium)
                                Text("Pendientes: ${resumen.pendientes.size} · Realizados: ${resumen.realizados.size}")
                                Text("Según la fecha del turno. Solo servicios realizados.", style = MaterialTheme.typography.bodySmall)
                                if (resumen.realizadosSinPrecio > 0) Text("${resumen.realizadosSinPrecio} realizados sin importe registrado; el total es parcial.")
                            }
                        }
                    }
                    state.mensaje?.let { mensaje -> item { Text(mensaje) } }
                    item { Text("Por realizar", style = MaterialTheme.typography.titleLarge) }
                    if (resumen.pendientes.isEmpty()) item { Text("No tenés turnos pendientes esta semana.") }
                    items(resumen.pendientes, key = { "pendiente-${it.id}" }) { turno ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TurnoCard(turno)
                            turno.precioServicioCentavos?.let { Text("Precio del servicio: ${dinero(it)}") }
                            val finalizo = turno.finMillis?.let { it <= ahora } == true
                            Button(modifier = Modifier.fillMaxWidth(),
                                enabled = finalizo && turno.precioServicioCentavos != null && state.completandoId == null,
                                onClick = { confirmarId = turno.id }) {
                                Text(if (state.completandoId == turno.id) "Guardando…" else "Marcar realizado")
                            }
                            if (turno.precioServicioCentavos == null) Text("Falta un Precio numérico válido en el servicio.")
                            else if (!finalizo) Text("Podés completarlo al finalizar su horario.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { Text("Realizados", style = MaterialTheme.typography.titleLarge) }
                    if (resumen.realizados.isEmpty()) item { Text("Todavía no registraste servicios realizados esta semana.") }
                    items(resumen.realizados, key = { "realizado-${it.id}" }) { turno ->
                        Column {
                            TurnoCard(turno)
                            Text(turno.precioCobradoCentavos?.let { "Importe registrado: ${dinero(it)}" }
                                ?: "Sin importe histórico registrado")
                        }
                    }
                }
            }
        }
    }
}
