package ar.edu.unaj.am.myapplication.core.ui.maps

import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuscarUbicacionTest {
    private fun punto(instante: Long = 1_000_000, precision: Float = 20f) =
        PosicionDispositivo(Coordenadas(-34.8, -58.3), precision, instante)

    @Test fun `usa red aunque el GPS no responda y cancela la espera satelital`() = runTest {
        var gpsCancelado = false
        val resultado = buscarUbicacion(listOf("gps", "network"), { null }, { proveedor ->
            if (proveedor == "gps") {
                try { awaitCancellation() } finally { gpsCancelado = true }
            } else { delay(100); punto() }
        }, { 1_000_000 + testScheduler.currentTime })
        assertEquals(punto(), resultado)
        assertTrue(gpsCancelado)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test fun `un proveedor falla pero el otro entrega ubicacion aproximada`() = runTest {
        val resultado = buscarUbicacion(listOf("fused", "network"), { null }, {
            if (it == "fused") throw IllegalArgumentException("No disponible")
            punto(precision = 400f)
        }, { 1_000_000 })
        assertEquals(400f, resultado!!.precisionMetros)
    }

    @Test fun `reutiliza una posicion precisa muy reciente sin encender sensores`() = runTest {
        val resultado = buscarUbicacion(listOf("gps"), { punto() }, {
            fail("No debería pedir otra posición"); null
        }, { 1_005_000 })
        assertEquals(punto(), resultado)
    }

    @Test fun `al agotar espera permite cache reciente pero rechaza cache antigua`() = runTest {
        suspend fun buscar(instante: Long) = buscarUbicacion(listOf("network"), { punto(instante, 400f) }, {
            awaitCancellation()
        }, { 1_000_000 + testScheduler.currentTime }, esperaMillis = 1000)
        assertNotNull(buscar(950_000))
        assertNull(buscar(100_000))
    }

    @Test fun `una respuesta invalida no oculta otra valida`() = runTest {
        val resultado = buscarUbicacion(listOf("gps", "network"), { null }, {
            if (it == "gps") punto().copy(coordenadas = Coordenadas(Double.NaN, 0.0))
            else { delay(10); punto() }
        }, { 1_000_000 })
        assertEquals(punto(), resultado)
    }

    @Test fun `cancelar la pantalla cancela todos los proveedores sin entregar resultados`() = runTest {
        var cancelados = 0
        val trabajo = launch {
            buscarUbicacion(listOf("gps", "network"), { null }, {
                try { awaitCancellation() } finally { cancelados++ }
            }, { 1_000_000 })
            fail("No debe devolver una posición después de cancelar")
        }
        runCurrent()
        trabajo.cancelAndJoin()
        assertEquals(2, cancelados)
    }
}
