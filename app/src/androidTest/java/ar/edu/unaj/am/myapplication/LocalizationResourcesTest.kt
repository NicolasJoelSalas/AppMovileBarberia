package ar.edu.unaj.am.myapplication

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import ar.edu.unaj.am.myapplication.features.auth.R as AuthR
import ar.edu.unaj.am.myapplication.features.turnos.R as TurnosR
import ar.edu.unaj.am.myapplication.features.cuenta.R as CuentaR
import ar.edu.unaj.am.myapplication.core.ui.R as UiR

/** Uses Android's actual resource selection without changing the device's language. */
class LocalizationResourcesTest {
    private fun resources(language: String) = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language))
        }).resources
    }

    @Test fun regionalLanguagesResolveAcrossModules() {
        val english = resources("en-US")
        assertEquals("My account", english.getString(R.string.tab_mi_cuenta))
        assertEquals("Welcome", english.getString(AuthR.string.features_auth_bienvenido))
        assertEquals("Book appointment", english.getString(TurnosR.string.features_turnos_reservar_turno))
        assertEquals("Changes saved.", english.getString(CuentaR.string.cuenta_saved))
        assertEquals("Passwords do not match.", english.getString(UiR.string.error_password_confirmation))
        val spanish = resources("es-AR")
        assertEquals("Mi cuenta", spanish.getString(R.string.tab_mi_cuenta))
        assertEquals("Bienvenido", spanish.getString(AuthR.string.features_auth_bienvenido))
        assertEquals("Las contraseñas no coinciden.", spanish.getString(UiR.string.error_password_confirmation))
        assertEquals("Mi cuenta", resources("es-AR,en-US").getString(R.string.tab_mi_cuenta))
    }

    @Test fun unsupportedLanguageUsesSpanishDefault() {
        assertEquals("Mi cuenta", resources("fr-FR").getString(R.string.tab_mi_cuenta))
    }

    @Test fun formattedMessagesAndPluralsPreserveArguments() {
        val english = resources("en")
        assertEquals("Your appointment is at 14:30.", english.getString(R.string.reminder_time, "14:30"))
        assertEquals("1 pending appointment", english.getQuantityString(R.plurals.app_pending_appointments, 1, 1))
        assertEquals("2 pending appointments", english.getQuantityString(R.plurals.app_pending_appointments, 2, 2))
        val spanish = resources("es")
        assertEquals("Tu turno es a las 14:30.", spanish.getString(R.string.reminder_time, "14:30"))
        assertEquals("1 turno pendiente", spanish.getQuantityString(R.plurals.app_pending_appointments, 1, 1))
        assertEquals("2 turnos pendientes", spanish.getQuantityString(R.plurals.app_pending_appointments, 2, 2))
    }
}
