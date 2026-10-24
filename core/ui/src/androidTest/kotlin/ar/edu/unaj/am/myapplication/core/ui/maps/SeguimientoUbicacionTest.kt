package ar.edu.unaj.am.myapplication.core.ui.maps

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SeguimientoUbicacionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun detieneSensoresAlSalirYPausaElSeguimientoConOrigenManual() {
        val activo = mutableStateOf(true)
        val suscripciones = AtomicInteger(0)
        val recibidas = AtomicInteger(0)
        val fuente = callbackFlow {
            suscripciones.incrementAndGet()
            trySend(PosicionDispositivo(Coordenadas(-34.8, -58.3), 10f, 1000))
            awaitClose { suscripciones.decrementAndGet() }
        }
        compose.setContent {
            ObservarPosicionesEnPrimerPlano(activo.value, { fuente },
                onUbicacion = { recibidas.incrementAndGet() }, onAviso = {})
        }
        compose.waitUntil(5_000) { suscripciones.get() == 1 && recibidas.get() == 1 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // No llamar waitForIdle sobre una Activity detenida.
        val limite = System.currentTimeMillis() + 5_000
        while (suscripciones.get() != 0 && System.currentTimeMillis() < limite) Thread.sleep(20)
        assertEquals(0, suscripciones.get())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { suscripciones.get() == 1 && recibidas.get() == 2 }
        compose.runOnIdle { activo.value = false }
        compose.waitUntil(5_000) { suscripciones.get() == 0 }
        assertEquals(2, recibidas.get())
        compose.runOnIdle { activo.value = true }
        compose.waitUntil(5_000) { suscripciones.get() == 1 && recibidas.get() == 3 }
        compose.activityRule.scenario.close()
        assertEquals(0, suscripciones.get())
    }
}
