package ar.edu.unaj.am.myapplication.features.cuenta

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.ui.components.AppTopBar
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiCuentaScreen(
    uiState: MiCuentaUiState,
    onNombreChange: (String) -> Unit,
    onGuardar: () -> Unit,
    onReintentar: () -> Unit,
    onLogout: () -> Unit,
    reporteState: ReporteGananciasUiState,
    onPeriodo: (Boolean) -> Unit,
    onMover: (Int) -> Unit,
    onReintentarReporte: () -> Unit
) {
    var verReportes by rememberSaveable { mutableStateOf(false) }
    var editando by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val volverAlMenu = {
        if (!uiState.guardando) {
            uiState.perfil?.let { onNombreChange(it.nombre) }
            verReportes = false
            editando = false
            focusManager.clearFocus()
        }
    }
    BackHandler(enabled = verReportes || editando) { volverAlMenu() }
    Scaffold(topBar = {
        AppTopBar(title = if (verReportes) "Ganancias" else if (editando) "Modificar perfil"
                else stringResource(R.string.cuenta_titulo),
            navigationIcon = {
                if (verReportes || editando) IconButton(onClick = { volverAlMenu() }, enabled = !uiState.guardando) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver a Mi cuenta")
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when {
                uiState.cargando -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.cuenta_cargando))
                }
                uiState.perfil != null -> {
                    val perfil = uiState.perfil
                    if (!verReportes && !editando) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(shape = CircleShape, color = colors.onPrimaryContainer.copy(alpha = 0.12f)) {
                                Icon(Icons.Outlined.Person, contentDescription = null,
                                    modifier = Modifier.padding(14.dp).size(28.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(perfil.nombre, style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold)
                                Text(if (perfil.esBarbero) "Cuenta de barbero" else "Cuenta de cliente",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (!verReportes && !editando) {
                        Column(Modifier.fillMaxWidth()) {
                            OpcionCuenta(Icons.Outlined.Edit, "Modificar perfil",
                                "Actualizá tus datos personales", onClick = { editando = true })
                            if (perfil.esBarbero) OpcionCuenta(Icons.Outlined.BarChart, "Reporte de ganancias",
                                "Consultá tus ingresos semanales y mensuales", onClick = { verReportes = true })
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
                            OpcionCuenta(Icons.AutoMirrored.Outlined.Logout, "Cerrar sesión",
                                "Salir de tu cuenta", onClick = onLogout, destructive = true)
                        }
                    }
                    if (verReportes && perfil.esBarbero) {
                        ReporteGananciasContent(reporteState, onPeriodo, onMover, onReintentarReporte)
                    } else if (editando) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(shape = CircleShape, color = colors.onPrimaryContainer.copy(alpha = 0.12f)) {
                                    Icon(Icons.Outlined.Person, contentDescription = null,
                                        modifier = Modifier.padding(12.dp).size(26.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Datos personales", style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text("Actualizá el nombre de tu perfil.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                val longitud = uiState.nombre.trim().length
                                val nombreInvalido = longitud == 0 || longitud > 120
                                OutlinedTextField(value = uiState.nombre, onValueChange = onNombreChange,
                                    modifier = Modifier.fillMaxWidth(), enabled = !uiState.guardando,
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text(stringResource(R.string.cuenta_nombre)) }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                    isError = nombreInvalido,
                                    supportingText = {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(when {
                                                longitud == 0 -> "Ingresá tu nombre."
                                                longitud > 120 -> "El máximo es de 120 caracteres."
                                                else -> "Así aparecerás en la aplicación."
                                            }, modifier = Modifier.weight(1f))
                                            Text("$longitud/120")
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.cuenta_correo), style = MaterialTheme.typography.labelLarge,
                                        color = colors.onSurfaceVariant)
                                    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHigh) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(Icons.Outlined.Email, contentDescription = null,
                                                tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Text(perfil.correo.ifEmpty { stringResource(R.string.cuenta_sin_correo) },
                                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                            Icon(Icons.Outlined.Lock, contentDescription = "Correo de solo lectura",
                                                tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text("El correo está asociado a tu cuenta y no se puede editar.",
                                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                HorizontalDivider(color = colors.outlineVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { focusManager.clearFocus(); onGuardar() },
                                        enabled = uiState.puedeGuardar,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer,
                                            contentColor = colors.onPrimaryContainer)) {
                                        if (uiState.guardando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(if (uiState.guardando) R.string.cuenta_guardando else R.string.cuenta_guardar))
                                    }
                                    OutlinedButton(onClick = { volverAlMenu() }, enabled = !uiState.guardando,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(14.dp)) {
                                        Text("Volver a Mi cuenta")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            uiState.error?.let { Text(it.asMessage(), color = colors.error) }
            if (!uiState.cargando && uiState.perfil == null) {
                Button(onClick = onReintentar) { Text(stringResource(R.string.cuenta_reintentar)) }
            }
            uiState.mensaje?.let { Text(it, color = colors.primary) }

        }
    }
}

@Composable
private fun OpcionCuenta(icon: ImageVector, titulo: String, descripcion: String,
    onClick: () -> Unit, destructive: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val color = if (destructive) colors.error else colors.onSurface
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 80.dp)
        .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = color,
                fontWeight = FontWeight.Medium)
            Text(descripcion, style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
