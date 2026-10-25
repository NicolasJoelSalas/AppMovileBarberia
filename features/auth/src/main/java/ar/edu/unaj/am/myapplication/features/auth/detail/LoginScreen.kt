package ar.edu.unaj.am.myapplication.features.auth.detail

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import ar.edu.unaj.am.myapplication.features.auth.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onGoogleLogin: () -> Unit,
    onRegister: (String, String, String) -> Unit,
    onToggleRegistration: () -> Unit,
    onRetryProfile: () -> Unit = {},
    needsProfileRetry: Boolean = false,
    isRegistering: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canSubmit = !isLoading && email.isNotBlank() && password.isNotBlank() &&
        (!isRegistering || confirmation.isNotBlank())
    val submit = {
        focusManager.clearFocus()
        if (canSubmit) {
            if (isRegistering) onRegister(email.trim(), password, confirmation)
            else onLogin(email.trim(), password)
        }
    }
    LaunchedEffect(isRegistering) {
        password = ""
        confirmation = ""
        passwordVisible = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)) {
                        Icon(Icons.Outlined.ContentCut, contentDescription = null,
                            modifier = Modifier.padding(10.dp).size(24.dp))
                    }
                    Text(stringResource(R.string.features_auth_barber_app), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(if (isRegistering) stringResource(R.string.features_auth_crea_tu_cuenta) else stringResource(R.string.features_auth_bienvenido),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (isRegistering) stringResource(R.string.features_auth_registrate_para_reservar_tu_proximo_corte)
                    else stringResource(R.string.features_auth_tu_barberia_en_un_solo_lugar_reserva_tu_turno_o_gestiona_tu_agenda_como_barbero),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) form@ {
                Text(if (isRegistering) stringResource(R.string.features_auth_datos_de_tu_cuenta) else stringResource(R.string.features_auth_iniciar_sesion),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))

                if (needsProfileRetry) {
                    Text(stringResource(R.string.features_auth_falta_completar_tu_perfil_tu_cuenta_ya_esta_creada))
                    if (errorMessage != null) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetryProfile, enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(if (isLoading) stringResource(R.string.features_auth_procesando) else stringResource(R.string.features_auth_reintentar))
                    }
                    return@form
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.features_auth_correo_electronico)) },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email, imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.features_auth_contrasena)) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) {
                            Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.features_auth_ocultar_contrasena) else stringResource(R.string.features_auth_mostrar_contrasena))
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isRegistering) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                        onDone = { submit() }),
                    enabled = !isLoading,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isRegistering) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.features_auth_confirmar_contrasena)) },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        enabled = !isLoading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.features_auth_usa_una_contrasena_de_al_menos_6_caracteres),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(16.dp))

                    Surface(color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = submit,
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        when {
                            isLoading -> stringResource(R.string.features_auth_procesando)
                            isRegistering -> stringResource(R.string.features_auth_crear_cuenta)
                            else -> stringResource(R.string.features_auth_ingresar)
                        }
                    )
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(stringResource(R.string.features_auth_o_continua_con), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = { focusManager.clearFocus(); onGoogleLogin() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White,
                        contentColor = Color(0xFF1F1F1F), disabledContainerColor = Color.White.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
                    border = BorderStroke(1.dp, Color(0xFF747775)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Image(painterResource(R.drawable.ic_google), contentDescription = null,
                        modifier = Modifier.size(20.dp), alpha = if (isLoading) 0.38f else 1f)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.features_auth_continuar_con_google), fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onToggleRegistration, enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (isRegistering) stringResource(R.string.features_auth_ya_tengo_cuenta_iniciar_sesion) else stringResource(R.string.features_auth_no_tengo_cuenta_registrarme),
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen(
            onLogin = { _, _ -> }, onGoogleLogin = {},
            onRegister = { _, _, _ -> }, onToggleRegistration = {}
        )
    }
}
