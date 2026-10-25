package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.features.turnos.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormat: DateTimeFormatter
    @androidx.compose.runtime.Composable get() = DateTimeFormatter.ofPattern("EEEE d MMMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ReservasRoute(viewModel: ReservasViewModel,
    turnosCliente: @Composable () -> Unit,
    agendaBarbero: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    if (state.catalogo?.esBarbero == true) agendaBarbero()
    else if (state.catalogo != null) turnosCliente()
    else if (error != null) ar.edu.unaj.am.myapplication.core.ui.components.ErrorContent(
        title = stringResource(R.string.features_turnos_no_se_pudieron_cargar_tus_turnos), message = error.asMessage(), onRetry = viewModel::cargar)
    else ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent(message = stringResource(R.string.features_turnos_cargando_turnos))
}

@Composable
fun ReservarTurnoRoute(viewModel: ReservasViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var eligiendoHorario by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.reservaConfirmada) {
        if (state.reservaConfirmada) onBack()
    }
    androidx.activity.compose.BackHandler(enabled = state.reservando || eligiendoHorario) {
        if (!state.reservando) eligiendoHorario = false
    }
    val zone = ZoneId.of(state.semana?.zonaHoraria ?: "America/Argentina/Buenos_Aires")
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.catalogo != null && !state.loading) viewModel.refrescar()
    }
    LaunchedEffect(state.semana?.inicioSemanaMillis, zone) {
        if (state.semana != null) {
            while (true) {
                val now = java.time.ZonedDateTime.now(zone)
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
                delay(java.time.Duration.between(now, midnight).toMillis().coerceAtLeast(1000))
                viewModel.refrescar()
            }
        }
    }
    if (eligiendoHorario) {
        HorariosReservaScreen(state, viewModel, onBack = { eligiendoHorario = false })
    } else {
        ReservasScreen(state, viewModel, onBack, onElegirHorario = {
            eligiendoHorario = true
            viewModel.refrescar()
        })
    }
    ConfirmarReservaDialog(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservasScreen(state: ReservasUiState, vm: ReservasViewModel, onBack: () -> Unit,
    onElegirHorario: () -> Unit) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.referencia(uri.toString()) }
    Scaffold(topBar = { AppTopBar(title = if (state.turnoId != null) stringResource(R.string.features_turnos_modificar_turno) else stringResource(R.string.features_turnos_reservar_turno), navigationIcon = {
        IconButton(onClick = onBack, enabled = !state.reservando) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.features_turnos_volver))
        }
    }) }, bottomBar = {
        Surface(shadowElevation = 4.dp) {
            Button(onClick = onElegirHorario,
                enabled = state.catalogo != null && state.catalogo.sinConexion != true &&
                    state.barberoId.isNotBlank() && state.servicioId.isNotBlank() && !state.reservando,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Text(stringResource(R.string.turnos_choose_datetime))
            }
        }
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.catalogo?.sinConexion == true) {
                item {
                    Text(stringResource(R.string.features_turnos_necesitas_conexion_para_consultar_disponibilidad_y_confirmar_cambios_tus_turnos_guardados_siguen_disponibles_en_mis_turnos))
                    TextButton(onClick = vm::cargar) { Text(stringResource(R.string.features_turnos_reintentar_conexion)) }
                }
            }
            state.catalogo?.let { catalogo ->
                val serviciosDisponibles = catalogo.servicios.filter {
                    state.barberoId.isNotBlank() && it.barberoId == state.barberoId
                }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.features_turnos_elegi_tu_turno), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.features_turnos_selecciona_barbero_servicio_y_un_horario_disponible_de_lunes_a_sabado),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Selector(stringResource(R.string.features_turnos_barbero), state.barberoId, catalogo.barberos.map { it.id to it.nombre },
                                !state.reservando, Icons.Outlined.Person, vm::barbero)
                            Selector(stringResource(R.string.features_turnos_servicio), state.servicioId,
                                serviciosDisponibles.map { it.id to stringResource(R.string.turnos_service_duration, it.nombre, it.duracion) },
                                !state.reservando && state.barberoId.isNotBlank(), Icons.Outlined.ContentCut, vm::servicio,
                                detalles = serviciosDisponibles.associate { it.id to precioServicio(it.precioCentavos) })
                            if (catalogo.barberos.isEmpty()) Text(stringResource(R.string.features_turnos_no_hay_barberos_habilitados))
                            if (state.barberoId.isNotBlank() && serviciosDisponibles.isEmpty()) Text(stringResource(R.string.features_turnos_no_hay_servicios_habilitados_de_40_minutos))
                        }
                    }
                }
            }
            if (state.loading) item { CircularProgressIndicator() }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.turnos_reference_optional), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.turnos_reference_hint))
                        state.referencia?.let { ReferenciaCorteImagen(it) }
                        OutlinedButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.reservando) {
                            Text(stringResource(if (state.referencia == null) R.string.turnos_reference_add else R.string.turnos_reference_change))
                        }
                        if (state.referencia != null) TextButton(onClick = { vm.referencia(null) }, enabled = !state.reservando) {
                            Text(stringResource(R.string.turnos_reference_remove))
                        }
                    }
                }
            }
            state.mensaje?.let { message -> item { Text(message.asString(), color = MaterialTheme.colorScheme.primary) } }
            state.error?.let { error ->
                item {
                    Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { if (state.catalogo == null) vm.cargar() else vm.refrescar() },
                        enabled = !state.reservando && !state.loading) { Text(stringResource(R.string.features_turnos_actualizar)) }
                }
            }

        }
    }
}

@Composable
private fun ConfirmarReservaDialog(state: ReservasUiState, vm: ReservasViewModel) {
    val zone = ZoneId.of(state.semana?.zonaHoraria ?: "America/Argentina/Buenos_Aires")
    state.selected?.let { slot ->
        val start = Instant.ofEpochMilli(slot.inicioMillis).atZone(zone)
        AlertDialog(
            onDismissRequest = { if (!state.reservando) vm.seleccionar(null) },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = { Text(if (state.turnoId != null) stringResource(R.string.features_turnos_confirmar_modificacion) else stringResource(R.string.features_turnos_confirmar_reserva)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(start.format(dayFormat).replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(start.format(hourFormat) + " – " + Instant.ofEpochMilli(slot.finMillis).atZone(zone).format(hourFormat),
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    ResumenReservaDato(Icons.Outlined.Person, stringResource(R.string.features_turnos_barbero),
                        state.catalogo?.barberos?.find { it.id == state.barberoId }?.nombre.orEmpty())
                    ResumenReservaDato(Icons.Outlined.ContentCut, stringResource(R.string.features_turnos_servicio),
                        state.catalogo?.servicios?.find { it.id == state.servicioId }?.nombre.orEmpty())
                    ResumenReservaDato(Icons.Outlined.Payments, stringResource(R.string.turnos_service_price),
                        precioServicio(state.catalogo?.servicios?.find { it.id == state.servicioId }?.precioCentavos))
                    HorizontalDivider()
                    ResumenReservaDato(Icons.Outlined.Image, stringResource(R.string.turnos_reference_title),
                        stringResource(if (state.referencia == null) R.string.turnos_reference_none else R.string.turnos_reference_attached))
                    OutlinedTextField(value = state.nombre, onValueChange = vm::nombre,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        label = { Text(stringResource(R.string.features_turnos_tu_nombre)) }, singleLine = true, enabled = !state.reservando)
                    state.error?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = vm::confirmar,
                    enabled = !state.reservando && state.nombre.isNotBlank() && state.nombre.length <= 120) {
                    if (state.reservando) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.reservando) stringResource(R.string.features_turnos_reservando) else if (state.turnoId != null) stringResource(R.string.features_turnos_guardar_cambios) else stringResource(R.string.features_turnos_confirmar))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.seleccionar(null) }, enabled = !state.reservando) { Text(stringResource(R.string.features_turnos_volver)) }
            }
        )
    }
}

@Composable
private fun ResumenReservaDato(icon: ImageVector, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Selector(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean,
    icon: ImageVector, select: (String) -> Unit, hint: String = stringResource(R.string.features_turnos_seleccionar),
    detalles: Map<String, String> = emptyMap()) {
    var expanded by remember { mutableStateOf(false) }
    val canSelect = enabled && options.isNotEmpty()
    val optionTextStyle = MaterialTheme.typography.bodyLarge
    val colors = MaterialTheme.colorScheme
    ExposedDropdownMenuBox(expanded = expanded && canSelect,
        onExpandedChange = { if (canSelect) expanded = it }) {
        OutlinedTextField(
            value = options.find { it.first == value }?.second?.let { nombre ->
                detalles[value]?.let { precio -> "$nombre\n$precio" } ?: nombre
            } ?: if (value.isEmpty()) hint else "",
            onValueChange = {}, readOnly = true, enabled = canSelect,
            textStyle = optionTextStyle.copy(
                textAlign = if (detalles[value] != null) TextAlign.Start else TextAlign.Center,
                fontWeight = if (detalles[value] != null) FontWeight.SemiBold else FontWeight.Normal),
            minLines = if (detalles[value] != null) 2 else 1,
            maxLines = if (detalles[value] != null) 3 else 1,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = canSelect).fillMaxWidth(),
            label = { Text(label) }, placeholder = {
                Text(hint, modifier = Modifier.fillMaxWidth(),
                    style = optionTextStyle, textAlign = TextAlign.Center)
            },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canSelect) },
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded && canSelect, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, colors.outline)) {
            options.forEach { option ->
                val selected = option.first == value
                DropdownMenuItem(text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(option.second, style = optionTextStyle,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.onSecondaryContainer else colors.onSurface)
                        detalles[option.first]?.let { detalle ->
                            Text(detalle, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
                        }
                    }
                }, leadingIcon = {
                    Icon(icon, contentDescription = null,
                        tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
                }, trailingIcon = {
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = null,
                        tint = colors.onSecondaryContainer)
                }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
                        RoundedCornerShape(10.dp)), onClick = {
                    expanded = false
                    select(option.first)
                })
            }
        }
    }
}

@Composable
private fun precioServicio(centavos: Long?): String {
    if (centavos == null) return stringResource(R.string.turnos_price_unavailable)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return java.text.NumberFormat.getCurrencyInstance(locale).apply {
        currency = java.util.Currency.getInstance("ARS")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(java.math.BigDecimal.valueOf(centavos, 2))
}
