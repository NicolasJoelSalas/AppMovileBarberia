package ar.edu.unaj.am.myapplication.features.cuenta

import ar.edu.unaj.am.myapplication.features.cuenta.R

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import ar.edu.unaj.am.myapplication.core.ui.theme.AppLanguage
import ar.edu.unaj.am.myapplication.core.ui.theme.LocalLanguagePreference
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import ar.edu.unaj.am.myapplication.core.ui.theme.LocalAppearance
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
    onUbicacionBarberia: () -> Unit = {},
    onServicios: () -> Unit = {}
) {
    var editando by rememberSaveable { mutableStateOf(false) }
    var eligiendoIdioma by rememberSaveable { mutableStateOf(false) }
    val language = LocalLanguagePreference.current
    val selectedLanguage = if (language.selected == AppLanguage.SYSTEM) {
        val locales = androidx.compose.ui.platform.LocalConfiguration.current.locales
        (0 until locales.size()).map { AppLanguage.fromTag(locales[it].language) }
            .firstOrNull { it != AppLanguage.SYSTEM } ?: AppLanguage.SPANISH
    } else language.selected
    if (eligiendoIdioma) {
        AlertDialog(
            onDismissRequest = { eligiendoIdioma = false },
            title = { Text(stringResource(R.string.cuenta_idioma)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(AppLanguage.SPANISH, AppLanguage.ENGLISH).forEach { option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = option == selectedLanguage,
                                    role = Role.RadioButton,
                                    onClick = {
                                        eligiendoIdioma = false
                                        language.onSelect(option)
                                    }
                                )
                                .heightIn(min = 56.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = option == selectedLanguage, onClick = null)
                            Text(stringResource(option.labelResource()))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { eligiendoIdioma = false }) {
                    Text(stringResource(R.string.cuenta_idioma_cancelar))
                }
            }
        )
    }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val volverAlMenu = {
        if (!uiState.guardando) {
            uiState.perfil?.let { onNombreChange(it.nombre) }
            editando = false
            focusManager.clearFocus()
        }
    }
    BackHandler(enabled = editando) { volverAlMenu() }
    Scaffold(topBar = {
        AppTopBar(title = if (editando) stringResource(R.string.features_cuenta_modificar_perfil)
                else stringResource(R.string.cuenta_titulo),
            navigationIcon = {
                if (editando) IconButton(onClick = { volverAlMenu() }, enabled = !uiState.guardando) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.features_cuenta_volver_a_mi_cuenta))
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
                    if (!editando) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
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
                                Text(if (perfil.esBarbero) stringResource(R.string.features_cuenta_cuenta_de_barbero) else stringResource(R.string.features_cuenta_cuenta_de_cliente),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (!editando) {
                        Column(Modifier.fillMaxWidth()) {
                            OpcionCuenta(Icons.Outlined.Edit, stringResource(R.string.features_cuenta_modificar_perfil),
                                stringResource(R.string.features_cuenta_actualiza_tus_datos_personales), onClick = { editando = true })
                            if (perfil.esBarbero) {
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
                                OpcionCuenta(Icons.Outlined.LocationOn, stringResource(R.string.features_cuenta_ubicacion_de_la_barberia),
                                    stringResource(R.string.features_cuenta_marca_en_el_mapa_donde_atendes), onClick = onUbicacionBarberia)
                                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
                                OpcionCuenta(Icons.Outlined.ContentCut, stringResource(R.string.servicios_title),
                                    stringResource(R.string.servicios_menu_description), onClick = onServicios)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
                            val appearance = LocalAppearance.current
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .toggleable(value = appearance.darkTheme, role = Role.Switch,
                                        onValueChange = appearance.onDarkThemeChange)
                                    .heightIn(min = 80.dp).padding(horizontal = 8.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(Icons.Outlined.DarkMode, contentDescription = null,
                                    modifier = Modifier.size(24.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.features_cuenta_modo_oscuro), style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text(if (appearance.darkTheme) stringResource(R.string.features_cuenta_activado) else stringResource(R.string.features_cuenta_desactivado_modo_claro),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant)
                                }
                                Switch(checked = appearance.darkTheme, onCheckedChange = null)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
                            OpcionCuenta(Icons.Outlined.Language, stringResource(R.string.cuenta_idioma),
                                stringResource(selectedLanguage.labelResource()),
                                onClick = { eligiendoIdioma = true })
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
                            OpcionCuenta(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.features_cuenta_cerrar_sesion),
                                stringResource(R.string.features_cuenta_salir_de_tu_cuenta), onClick = onLogout, destructive = true)
                        }
                    }
                    if (editando) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(shape = CircleShape, color = colors.onPrimaryContainer.copy(alpha = 0.12f)) {
                                    Icon(Icons.Outlined.Person, contentDescription = null,
                                        modifier = Modifier.padding(12.dp).size(26.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.features_cuenta_datos_personales), style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.features_cuenta_actualiza_el_nombre_de_tu_perfil), style = MaterialTheme.typography.bodyMedium)
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
                                                longitud == 0 -> stringResource(R.string.features_cuenta_ingresa_tu_nombre)
                                                longitud > 120 -> stringResource(R.string.features_cuenta_el_maximo_es_de_120_caracteres)
                                                else -> stringResource(R.string.features_cuenta_asi_apareceras_en_la_aplicacion)
                                            }, modifier = Modifier.weight(1f))
                                            Text(stringResource(R.string.cuenta_name_count, longitud))
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
                                            Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.features_cuenta_correo_de_solo_lectura),
                                                tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(stringResource(R.string.features_cuenta_el_correo_esta_asociado_a_tu_cuenta_y_no_se_puede_editar),
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
                                        Text(stringResource(R.string.features_cuenta_volver_a_mi_cuenta))
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
            uiState.mensaje?.let { Text(it.asString(), color = colors.primary) }

        }
    }
}

private fun AppLanguage.labelResource(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.cuenta_idioma_dispositivo
    AppLanguage.SPANISH -> R.string.cuenta_idioma_espanol
    AppLanguage.ENGLISH -> R.string.cuenta_idioma_ingles
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
