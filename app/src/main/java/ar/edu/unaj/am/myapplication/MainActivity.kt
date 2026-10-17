package ar.edu.unaj.am.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.unaj.am.myapplication.core.ui.theme.MyApplicationTheme
import ar.edu.unaj.am.myapplication.features.auth.detail.LoginRoute
import ar.edu.unaj.am.myapplication.features.auth.detail.LoginViewModel
import ar.edu.unaj.am.myapplication.ui.AppShell
import ar.edu.unaj.am.myapplication.ui.AuthenticatedSessionViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ComposeAcademyApp).container

        setContent {
            val sessionOwner: AuthenticatedSessionViewModel = viewModel()
            val loginViewModel: LoginViewModel = viewModel {
                LoginViewModel(
                    login = container.login,
                    loginWithGoogle = container.loginWithGoogle,
                    register = container.register,
                    ensureClienteProfile = container.ensureClienteProfile,
                    logout = container.logout,
                    initiallyLoggedIn = container.isLoggedIn()
                )
            }

            val loginState by loginViewModel.uiState
                .collectAsStateWithLifecycle()

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (loginState.isAuthenticated) {
                        // Cada sesión tiene sus propios ViewModels de navegación y datos.
                        CompositionLocalProvider(LocalViewModelStoreOwner provides sessionOwner) {
                            AppShell(container = container, onLogout = {
                                ar.edu.unaj.am.myapplication.reminders.TurnoReminderScheduler(applicationContext).cancelAll()
                                loginViewModel.onLogout()
                                sessionOwner.clearSession()
                            })
                        }
                    } else {
                        // Google Services generates this resource when the JSON contains a web OAuth client.
                        val clientIdResource = resources.getIdentifier(
                            "default_web_client_id", "string", packageName
                        )
                        LoginRoute(
                            viewModel = loginViewModel,
                            googleClientId = if (clientIdResource != 0) getString(clientIdResource) else ""
                        )
                    }
                }
            }
        }
    }
}
