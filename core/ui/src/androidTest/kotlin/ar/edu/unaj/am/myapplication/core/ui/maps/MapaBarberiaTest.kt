package ar.edu.unaj.am.myapplication.core.ui.maps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicBoolean

/** Prueba del mapa nativo real: carga del estilo, selección y actualización de overlays. */
class MapaBarberiaTest {
    @get:Rule val compose = createComposeRule()

    @Test fun seleccionaPinYActualizaRecorrido() {
        val seleccionado = mutableStateOf<Coordenadas?>(null)
        val destino = mutableStateOf(Coordenadas(-34.8, -58.3))
        val puntos = mutableStateOf(emptyList<Coordenadas>())
        compose.setContent {
            MaterialTheme {
                MapaBarberia(destino.value, Modifier.fillMaxSize(), recorrido = puntos.value,
                    onSeleccionar = { seleccionado.value = it })
            }
        }
        val listo = AtomicBoolean(false)
        compose.waitUntil(timeoutMillis = 20_000) {
            onView(isAssignableFrom(MapView::class.java)).check { view, error ->
                if (error != null) throw error
                (view as MapView).getMapAsync { listo.set(it.style?.isFullyLoaded == true) }
            }
            listo.get()
        }
        onView(isAssignableFrom(MapView::class.java)).perform(click())
        compose.waitUntil(timeoutMillis = 5_000) { seleccionado.value != null }
        assertTrue(seleccionado.value!!.esValida)
        compose.runOnIdle {
            destino.value = seleccionado.value!!
            puntos.value = listOf(Coordenadas(-34.81, -58.31), destino.value)
        }
        compose.waitForIdle()
        // Captura del render nativo, también útil para verificar el dispositivo de páginas de 16 KB.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "mapa-prueba.png")
        file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
