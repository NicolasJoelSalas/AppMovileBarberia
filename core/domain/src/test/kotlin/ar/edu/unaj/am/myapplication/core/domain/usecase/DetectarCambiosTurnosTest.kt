package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectarCambiosTurnosTest {
    private val turno = Turno("t1", "cliente", "barbero", "servicio", 1000L, 2000L, "confirmado")
    private val base = mapOf(turno.id to firmaTurno(turno))
    @Test fun `detecta alta modificacion cancelacion y desaparicion`() {
        assertEquals("alta", detectarCambiosTurnos(emptyMap(), listOf(turno)).single().tipo)
        assertEquals("modificacion", detectarCambiosTurnos(base, listOf(turno.copy(inicioMillis = 3000L))).single().tipo)
        assertEquals("baja", detectarCambiosTurnos(base, listOf(turno.copy(estado = "Cancelado"))).single().tipo)
        assertEquals("baja", detectarCambiosTurnos(base, emptyList()).single().tipo)
    }
    @Test fun `ignora nombres auxiliares y datos contables`() {
        assertTrue(detectarCambiosTurnos(base, listOf(turno.copy(clienteNombre = "Nombre", servicioNombre = "Corte", precioCobradoCentavos = 100))).isEmpty())
        assertTrue(detectarCambiosTurnos(base, listOf(turno.copy(estado = "Confirmado"))).isEmpty())
    }
    @Test fun `imagen y servicio son cambios relevantes`() {
        assertEquals("modificacion", detectarCambiosTurnos(base, listOf(turno.copy(referenciaCorteUrl = "https://imagen"))).single().tipo)
        assertEquals("modificacion", detectarCambiosTurnos(base, listOf(turno.copy(servicioId = "otro"))).single().tipo)
    }
}
