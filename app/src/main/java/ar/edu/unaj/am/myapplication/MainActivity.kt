package ar.edu.unaj.am.myapplication

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import dagger.Lazy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ar.edu.unaj.am.myapplication.session.GestorSesion

import ar.edu.unaj.am.myapplication.R

import androidx.compose.ui.res.stringResource

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.edu.unaj.am.myapplication.core.ui.theme.AppLanguage
import ar.edu.unaj.am.myapplication.core.ui.theme.LanguagePreference
import ar.edu.unaj.am.myapplication.core.ui.theme.LocalLanguagePreference
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

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var apariencia: ar.edu.unaj.am.myapplication.core.domain.repository.AparienciaRepository

    @Inject lateinit var sessionProvider: Lazy<GestorSesion>

    @Inject lateinit var ubicacionDispositivo: ar.edu.unaj.am.myapplication.core.domain.repository.UbicacionDispositivoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            var language by remember {
                mutableStateOf(AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.language))
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                language = AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.language)
            }
            val systemDarkTheme = isSystemInDarkTheme()
            var darkThemePreference by remember {
                mutableStateOf(apariencia.temaOscuro)
            }
            val darkTheme = darkThemePreference ?: systemDarkTheme
            SideEffect {
                val barStyle = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            val sessionOwner: AuthenticatedSessionViewModel = viewModel()
            sessionOwner.defaultViewModelProviderFactory = defaultViewModelProviderFactory
            sessionOwner.defaultViewModelCreationExtras = defaultViewModelCreationExtras
            var cerrandoSesion by remember { mutableStateOf(false) }
            val loginViewModel: LoginViewModel = hiltViewModel()

            val loginState by loginViewModel.uiState
                .collectAsStateWithLifecycle()

            CompositionLocalProvider(
                ar.edu.unaj.am.myapplication.core.ui.maps.LocalUbicacionDispositivo provides ubicacionDispositivo,
                LocalLanguagePreference provides LanguagePreference(language) { selected ->
                    language = selected
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected.tag))
                },
                LocalAppearance provides Appearance(darkTheme) { dark ->
                apariencia.temaOscuro = dark
                darkThemePreference = dark
            }) {
            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (cerrandoSesion) {
                        ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent(message = stringResource(R.string.app_cerrando_sesion))
                    } else if (loginState.isAuthenticated) {
                        // Cada sesión tiene sus propios ViewModels de navegación y datos.
                        CompositionLocalProvider(LocalViewModelStoreOwner provides sessionOwner) {
                            AppShell(onLogout = {
                                if (!cerrandoSesion) {
                                    cerrandoSesion = true
                                    val uid = sessionProvider.get().clienteActualId()
                                    lifecycleScope.launch {
                                        try {
                                            sessionOwner.clearSession()
                                            if (uid != null) sessionProvider.get().limpiarSesion(uid)
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(applicationContext,
                                                getString(R.string.app_error_limpiar_copia),
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
