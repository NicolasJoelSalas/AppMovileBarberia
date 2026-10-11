package ar.edu.unaj.am.myapplication.core.data.repository

import org.junit.Assert.*
import org.junit.Test

class PrecioCentavosTest {
    @Test fun `preserva pesos y decimales sin errores de coma flotante`() {
        assertEquals(1250000L, precioCentavos(12500L))
        assertEquals(1250050L, precioCentavos(12500.50))
        assertEquals(29L, precioCentavos(0.29))
        assertEquals(0L, precioCentavos(0))
    }
    @Test fun `rechaza importes faltantes negativos texto y precision no soportada`() {
        listOf(null, "12500", -1, Double.NaN, Double.POSITIVE_INFINITY, 12.345, 1000000001L)
            .forEach { assertNull(precioCentavos(it)) }
    }
}
