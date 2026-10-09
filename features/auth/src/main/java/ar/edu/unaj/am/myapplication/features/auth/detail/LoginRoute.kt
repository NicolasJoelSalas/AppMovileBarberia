package ar.edu.unaj.am.myapplication.features.auth.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.ui.components.asMessage

@Composable
fun LoginRoute(viewModel: LoginViewModel, googleClientId: String) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LoginScreen(
        onLogin = viewModel::onLogin,
        onRegister = viewModel::onRegister,
        onToggleRegistration = viewModel::onToggleRegistration,
        onRetryProfile = viewModel::onRetryProfile,
        needsProfileRetry = uiState.needsProfileRetry,
        isRegistering = uiState.isRegistering,
        onGoogleLogin = {
            if (googleClientId.isBlank()) {
                viewModel.onGoogleConfigurationMissing()
            } else {
                viewModel.onGoogleLogin {
                    val option = GetSignInWithGoogleOption.Builder(googleClientId).build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build()
                    try {
                        val credential = credentialManager.getCredential(context, request).credential
                        check(
                            credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        )
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } catch (e: GetCredentialCancellationException) {
                        null
                    }
                }
            }
        },
        isLoading = uiState.isLoading,
        errorMessage = uiState.error?.asMessage()
    )
}
