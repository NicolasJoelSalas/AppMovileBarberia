package ar.edu.unaj.am.myapplication.features.cuenta

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiCuentaScreen(
    uiState: MiCuentaUiState,
    onNombreChange: (String) -> Unit,
    onGuardar: () -> Unit,
    onReintentar: () -> Unit,
    onLogout: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.cuenta_titulo)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                uiState.cargando -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.cuenta_cargando))
                }
                uiState.perfil != null -> {
                    val nombreInvalido = uiState.nombre.trim().isEmpty() || uiState.nombre.trim().length > 120
                    OutlinedTextField(
                        value = uiState.nombre, onValueChange = onNombreChange,
                        modifier = Modifier.fillMaxWidth(), enabled = !uiState.guardando,
                        label = { Text(stringResource(R.string.cuenta_nombre)) }, singleLine = true,
                        isError = nombreInvalido,
                        supportingText = { Text(stringResource(R.string.cuenta_nombre_ayuda)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Text(stringResource(R.string.cuenta_correo), style = MaterialTheme.typography.labelLarge)
                    Text(uiState.perfil.correo.ifEmpty { stringResource(R.string.cuenta_sin_correo) })
                    Text(stringResource(R.string.cuenta_correo_ayuda), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (uiState.perfil.esBarbero) R.string.cuenta_barbero else R.string.cuenta_cliente))
                    Button(onClick = { focusManager.clearFocus(); onGuardar() },
                        enabled = uiState.puedeGuardar, modifier = Modifier.fillMaxWidth()) {
                        if (uiState.guardando) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (uiState.guardando) R.string.cuenta_guardando else R.string.cuenta_guardar))
                    }
                }
            }
            uiState.error?.let { Text(it.asMessage(), color = MaterialTheme.colorScheme.error) }
            if (!uiState.cargando && uiState.perfil == null) {
                Button(onClick = onReintentar) { Text(stringResource(R.string.cuenta_reintentar)) }
            }
            uiState.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            HorizontalDivider()
            TextButton(onClick = onLogout, enabled = !uiState.guardando, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.cuenta_cerrar_sesion))
            }
        }
    }
}
