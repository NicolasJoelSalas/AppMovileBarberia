package ar.edu.unaj.am.myapplication.features.cuenta.servicios

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.ServicioBarbero
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage
import ar.edu.unaj.am.myapplication.features.cuenta.R
import java.text.NumberFormat
import java.math.BigDecimal

@Composable
fun ServiciosRoute(viewModel: ServiciosViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var baja by remember { mutableStateOf<ServicioBarbero?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!state.cargando) viewModel.cargar() }
    BackHandler(enabled = state.guardando) { }
    Scaffold(topBar = {
        AppTopBar(stringResource(R.string.servicios_title), navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.guardando) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.features_cuenta_volver))
            }
        })
    }, bottomBar = {
        Surface(shadowElevation = 6.dp) {
            Button(onClick = { viewModel.editar() }, enabled = !state.cargando && !state.guardando,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.servicios_add))
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.servicios_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.servicios_duration_notice), Modifier.padding(16.dp))
                }
            }
            if (state.cargando || state.guardando) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.editor == null) state.error?.let { error -> item {
                Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::cargar, enabled = !state.guardando && !state.cargando) {
                    Text(stringResource(R.string.servicios_retry))
                }
            } }
            if (!state.cargando && state.servicios.isEmpty()) item { Text(stringResource(R.string.servicios_empty)) }
            items(state.servicios, key = { it.id }) { servicio ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(servicio.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.servicios_details,
                            NumberFormat.getCurrencyInstance().apply { currency = java.util.Currency.getInstance("ARS") }
                                .format(BigDecimal.valueOf(servicio.precioCentavos, 2)), servicio.duracionMinutos))
                        Text(stringResource(if (servicio.activo) R.string.servicios_active else R.string.servicios_inactive),
                            color = if (servicio.activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.editar(servicio) }, enabled = !state.guardando && !state.cargando) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.servicios_edit))
                            }
                            TextButton(onClick = { if (servicio.activo) baja = servicio else viewModel.estado(servicio) },
                                enabled = !state.guardando && !state.cargando) {
                                Text(stringResource(if (servicio.activo) R.string.servicios_deactivate else R.string.servicios_activate))
                            }
                        }
                    }
                }
            }
        }
    }
    baja?.let { servicio ->
        AlertDialog(onDismissRequest = { baja = null },
            title = { Text(stringResource(R.string.servicios_deactivate_title)) },
            text = { Text(stringResource(R.string.servicios_deactivate_body, servicio.nombre)) },
            confirmButton = { TextButton(onClick = { baja = null; viewModel.estado(servicio) }) {
                Text(stringResource(R.string.servicios_deactivate))
            } }, dismissButton = { TextButton(onClick = { baja = null }) { Text(stringResource(R.string.servicios_cancel)) } })
    }
    state.editor?.let { editor ->
        AlertDialog(onDismissRequest = viewModel::cerrarEditor,
            icon = { Icon(Icons.Outlined.ContentCut, contentDescription = null) },
            title = { Text(stringResource(if (editor.nuevo) R.string.servicios_add else R.string.servicios_edit_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(editor.nombre, viewModel::nombre, label = { Text(stringResource(R.string.servicios_name)) },
                        singleLine = true, enabled = !state.guardando, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editor.precio, viewModel::precio, label = { Text(stringResource(R.string.servicios_price)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        enabled = !state.guardando, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editor.duracion, viewModel::duracion, label = { Text(stringResource(R.string.servicios_duration)) },
                        supportingText = { Text(stringResource(R.string.servicios_duration_range)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                        enabled = !state.guardando, modifier = Modifier.fillMaxWidth())
                    state.error?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = {
                Button(onClick = viewModel::guardar, enabled = !state.guardando && editor.nombre.isNotBlank() &&
                    editor.precio.isNotBlank() && editor.duracion.isNotBlank()) {
                    Text(stringResource(if (state.guardando) R.string.servicios_saving else R.string.servicios_save))
                }
            }, dismissButton = { TextButton(onClick = viewModel::cerrarEditor, enabled = !state.guardando) {
                Text(stringResource(R.string.servicios_cancel))
            } })
    }
}
