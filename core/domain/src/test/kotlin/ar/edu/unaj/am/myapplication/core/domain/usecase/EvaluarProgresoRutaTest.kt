package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class EvaluarProgresoRutaTest {
    private val ruta = RutaBarberia(listOf(Coordenadas(0.0, 0.0), Coordenadas(0.0, 0.02)), 2000.0, 200.0)

    @Test fun `calcula distancia al segmento y progreso aunque no haya vertices cercanos`() {
        val progreso = evaluarProgresoRuta(Coordenadas(0.0, 0.01), ruta)
        assertEquals(0.0, progreso.desvioMetros, 0.01)
        assertEquals(1000.0, progreso.distanciaRestanteMetros, 0.01)
    }

    @Test fun `detecta desvio lateral sin confundirlo con distancia al destino`() {
        val progreso = evaluarProgresoRuta(Coordenadas(0.001, 0.01), ruta)
        assertEquals(111.2, progreso.desvioMetros, 0.2)
        assertEquals(1000.0, progreso.distanciaRestanteMetros, 0.01)
    }

    @Test fun `extremos y segmentos repetidos no generan NaN`() {
        assertEquals(0.0, evaluarProgresoRuta(Coordenadas(0.0, 0.02), ruta).distanciaRestanteMetros, 0.01)
        assertEquals(2000.0, evaluarProgresoRuta(Coordenadas(0.0, -0.001), ruta).distanciaRestanteMetros, 0.01)
        val repetida = ruta.copy(puntos = listOf(ruta.puntos.first()) + ruta.puntos)
        assertEquals(1000.0, evaluarProgresoRuta(Coordenadas(0.0, 0.01), repetida).distanciaRestanteMetros, 0.01)
    }
}
