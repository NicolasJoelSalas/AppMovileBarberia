package ar.edu.unaj.am.myapplication.core.data.repository

import org.junit.Assert.*
import org.junit.Test

class ImporteCentavosTest {
    @Test fun `preserva decimales y rechaza precios invalidos`() {
        assertEquals(1250050L, importeCentavos(12500.50))
        assertEquals(29L, importeCentavos(0.29))
        assertEquals(0L, importeCentavos(0))
        listOf(null, "12500", -1, Double.NaN, 12.345, 1000000001L).forEach {
            assertNull(importeCentavos(it))
        }
    }
}
