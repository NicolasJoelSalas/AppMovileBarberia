package ar.edu.unaj.am.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import ar.edu.unaj.am.myapplication.core.ui.theme.Appearance
import ar.edu.unaj.am.myapplication.core.ui.theme.LocalAppearance
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
            val preferences = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
            val systemDarkTheme = isSystemInDarkTheme()
            var darkThemePreference by remember {
                mutableStateOf(if (preferences.contains("dark_theme"))
                    preferences.getBoolean("dark_theme", false) else null)
            }
            val darkTheme = darkThemePreference ?: systemDarkTheme
            SideEffect {
                val barStyle = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            val sessionOwner: AuthenticatedSessionViewModel = viewModel()
            var cerrandoSesion by remember { mutableStateOf(false) }
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

            CompositionLocalProvider(LocalAppearance provides Appearance(darkTheme) { dark ->
                preferences.edit().putBoolean("dark_theme", dark).apply()
                darkThemePreference = dark
            }) {
            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (loginState.isAuthenticated) {
                        // Cada sesión tiene sus propios ViewModels de navegación y datos.
                        CompositionLocalProvider(LocalViewModelStoreOwner provides sessionOwner) {
                            AppShell(container = container, onLogout = {
                                if (!cerrandoSesion) {
                                    cerrandoSesion = true
                                    val uid = container.clienteActualId()
                                    lifecycleScope.launch {
                                        try {
                                            sessionOwner.clearSession()
                                            if (uid != null) container.limpiarSesionCliente(uid)
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(applicationContext,
                                                "No se pudo limpiar la copia local. Los datos siguen separados por cuenta.",
                                                android.widget.Toast.LENGTH_LONG).show()
                                        } finally {
                                            ar.edu.unaj.am.myapplication.reminders.TurnoReminderScheduler(applicationContext).cancelAll()
                                            loginViewModel.onLogout()
                                            cerrandoSesion = false
                                        }
                                    }
                                }
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
}
