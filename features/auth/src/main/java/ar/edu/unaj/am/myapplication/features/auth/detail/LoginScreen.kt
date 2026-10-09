package ar.edu.unaj.am.myapplication.features.auth.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    LaunchedEffect(isRegistering) {
        password = ""
        confirmation = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRegistering) "Crear cuenta" else "Iniciar sesión",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        if (needsProfileRetry) {
            Text("Falta completar tu perfil. Tu cuenta ya está creada.")
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetryProfile, enabled = !isLoading) {
                Text(if (isLoading) "Procesando…" else "Reintentar")
            }
            return@Column
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            enabled = !isLoading,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            enabled = !isLoading,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (isRegistering) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text("Confirmar contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Usá una contraseña de al menos 6 caracteres.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (isRegistering) onRegister(email.trim(), password, confirmation)
                else onLogin(email.trim(), password)
            },
            enabled = !isLoading &&
                    email.isNotBlank() &&
                    password.isNotBlank() && (!isRegistering || confirmation.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    isLoading -> "Procesando…"
                    isRegistering -> "Crear cuenta"
                    else -> "Ingresar"
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onGoogleLogin,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continuar con Google")
        }

        TextButton(onClick = onToggleRegistration, enabled = !isLoading) {
            Text(if (isRegistering) "Ya tengo cuenta · Iniciar sesión" else "No tengo cuenta · Registrarme")
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
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
