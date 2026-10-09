package ar.edu.unaj.am.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.unaj.am.myapplication.core.ui.theme.MyApplicationTheme
import ar.edu.unaj.am.myapplication.features.auth.detail.LoginRoute
import ar.edu.unaj.am.myapplication.features.auth.detail.LoginViewModel
import ar.edu.unaj.am.myapplication.ui.AppShell

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ComposeAcademyApp).container

        setContent {
            val loginViewModel: LoginViewModel = viewModel {
                LoginViewModel(
                    login = container.login,
                    loginWithGoogle = container.loginWithGoogle,
                    register = container.register,
                    ensureClienteProfile = container.ensureClienteProfile,
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
                        AppShell(container = container)
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
