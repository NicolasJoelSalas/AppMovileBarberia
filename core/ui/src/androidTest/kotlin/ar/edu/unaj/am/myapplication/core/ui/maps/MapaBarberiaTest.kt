package ar.edu.unaj.am.myapplication.core.ui.maps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicBoolean
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng

/** Prueba del mapa nativo real: carga del estilo, selección y actualización de overlays. */
class MapaBarberiaTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explorarNoEsInterrumpidoPorUbicacionYCentrarReactivaCamara() {
        val origen = mutableStateOf(Coordenadas(-34.8, -58.3))
        val centrar = mutableStateOf(true)
        val idle = AtomicBoolean(false)
        var map: MapLibreMap? = null
        compose.setContent {
            MaterialTheme {
                MapaBarberia(Coordenadas(-34.81, -58.31), Modifier.fillMaxSize(), origen = origen.value,
                    seguimientoHabilitado = true, centrarEnOrigen = centrar.value,
                    onExplorarMapa = { centrar.value = false })
            }
        }
        onView(isAssignableFrom(MapView::class.java)).check { view, error ->
            if (error != null) throw error
            (view as MapView).getMapAsync {
                map = it
                it.addOnCameraMoveStartedListener { idle.set(false) }
                it.addOnCameraIdleListener { idle.set(true) }
            }
        }
        compose.waitUntil(20_000) { idle.get() && map != null }
        onView(isAssignableFrom(MapView::class.java)).perform(swipeLeft())
        compose.waitUntil(5_000) { !centrar.value && idle.get() }
        var centro: LatLng? = null
        compose.runOnIdle { centro = map!!.cameraPosition.target }
        compose.runOnIdle { origen.value = Coordenadas(-34.805, -58.305) }
        compose.waitForIdle()
        // Esperar la animación del marcador: la cámara debe conservar la exploración.
        Thread.sleep(1100)
        compose.runOnIdle {
            assertEquals(centro!!.latitude, map!!.cameraPosition.target!!.latitude, 0.000001)
            assertEquals(centro!!.longitude, map!!.cameraPosition.target!!.longitude, 0.000001)
            idle.set(false)
            centrar.value = true
        }
        compose.waitUntil(5_000) { idle.get() }
        compose.runOnIdle {
            assertEquals(origen.value.latitud, map!!.cameraPosition.target!!.latitude, 0.000001)
            assertEquals(origen.value.longitud, map!!.cameraPosition.target!!.longitude, 0.000001)
        }
    }

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
