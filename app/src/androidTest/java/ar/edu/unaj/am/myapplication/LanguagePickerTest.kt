package ar.edu.unaj.am.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.unaj.am.myapplication.core.domain.model.PerfilUsuario
import ar.edu.unaj.am.myapplication.core.ui.theme.AppLanguage
import ar.edu.unaj.am.myapplication.core.ui.theme.LanguagePreference
import ar.edu.unaj.am.myapplication.core.ui.theme.LocalLanguagePreference
import ar.edu.unaj.am.myapplication.features.cuenta.MiCuentaScreen
import ar.edu.unaj.am.myapplication.features.cuenta.MiCuentaUiState
import ar.edu.unaj.am.myapplication.features.cuenta.R as CuentaR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguagePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accountPickerSelectsAllOptionsAndCancelDoesNotChangePreference() {
        val selected = mutableStateOf(AppLanguage.SYSTEM)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(CuentaR.string.cuenta_idioma)
        val system = context.getString(CuentaR.string.cuenta_idioma_dispositivo)
        val cancel = context.getString(CuentaR.string.cuenta_idioma_cancelar)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLanguagePreference provides LanguagePreference(selected.value) {
                    selected.value = it
                }) {
                    MiCuentaScreen(
                        MiCuentaUiState(cargando = false, perfil = PerfilUsuario("Prueba", "", false)),
                        onNombreChange = {}, onGuardar = {}, onReintentar = {}, onLogout = {}
                    )
                }
            }
        }
        for ((label, language) in listOf("Español" to AppLanguage.SPANISH, "English" to AppLanguage.ENGLISH)) {
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText(system).assertDoesNotExist()
            compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
            compose.runOnIdle { assertEquals(language, selected.value) }
            compose.onNode(isDialog()).assertDoesNotExist()
        }
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText(cancel).performClick()
        compose.runOnIdle { assertEquals(AppLanguage.ENGLISH, selected.value) }
        compose.onNode(isDialog()).assertDoesNotExist()
    }
}
