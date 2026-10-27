package ar.edu.unaj.am.myapplication

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import android.os.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationLanguageTest {
    @Test fun appLocaleChangesSurviveRecreationAndCanReturnToSystem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var original = LocaleListCompat.getEmptyLocaleList()
            scenario.onActivity { original = AppCompatDelegate.getApplicationLocales() }
            try {
                for ((tag, expected) in listOf("en" to "My account", "es" to "Mi cuenta")) {
                    scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                    awaitLanguage(tag, expected)
                }
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
                awaitLanguage("", null)
            } finally {
                instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(original) }
                instrumentation.waitForIdleSync()
            }
        }
    }

    // LocaleManager recreates activities asynchronously. Wait for the replacement instead
    // of racing its recreation with another ActivityScenario.recreate() request.
    private fun awaitLanguage(tag: String, expected: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 10_000
        var stableSince = 0L
        var previous: MainActivity? = null
        var observado = "Sin actividad"
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            instrumentation.runOnMainSync {
                val current = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                observado = "locales=${AppCompatDelegate.getApplicationLocales().toLanguageTags()}, activity=${current?.javaClass?.simpleName}, text=${current?.getString(R.string.tab_mi_cuenta)}"
                matched = current != null && !current.isDestroyed && !current.isFinishing &&
                    AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag &&
                    (expected == null || current.getString(R.string.tab_mi_cuenta) == expected)
                if (!matched || current !== previous) stableSince = SystemClock.uptimeMillis()
                previous = current
            }
            if (matched && SystemClock.uptimeMillis() - stableSince >= 500) return
            SystemClock.sleep(50)
        }
        assertTrue("No resumed activity with language $tag and text $expected; $observado", false)
    }
}
