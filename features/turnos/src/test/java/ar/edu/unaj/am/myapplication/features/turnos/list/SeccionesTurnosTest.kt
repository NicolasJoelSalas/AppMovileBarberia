package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import org.junit.Assert.assertEquals
import org.junit.Test

class SeccionesTurnosTest {
    private fun turno(id: String, estado: String, inicio: Long?) =
        Turno(id, "cliente", "barbero", "servicio", inicio, null, estado)

    @Test fun `sections follow requested priority and sort by proximity including past appointments`() {
        val grupos = seccionesTurnos(listOf(
            turno("completado-viejo", "realizado", 100),
            turno("cancelado-lejos", "cancelada", 900),
            turno("pendiente-lejos", "pendiente", 800),
            turno("sin-fecha", "confirmado", null),
            turno("completado-reciente", "Completado", 490),
            turno("cancelado-cerca", "Cancelado", 480),
            turno("pendiente-cerca", "Confirmado", 510)
        ), 500)
        assertEquals(listOf(SeccionTurnos.PENDIENTES, SeccionTurnos.CANCELADOS, SeccionTurnos.COMPLETADOS), grupos.keys.toList())
        assertEquals(listOf("pendiente-cerca", "pendiente-lejos", "sin-fecha"), grupos.getValue(SeccionTurnos.PENDIENTES).map { it.id })
        assertEquals(listOf("cancelado-cerca", "cancelado-lejos"), grupos.getValue(SeccionTurnos.CANCELADOS).map { it.id })
        assertEquals(listOf("completado-reciente", "completado-viejo"), grupos.getValue(SeccionTurnos.COMPLETADOS).map { it.id })
    }

    @Test fun `empty sections are omitted and unknown states remain visible`() {
        assertEquals(emptyMap<SeccionTurnos, List<Turno>>(), seccionesTurnos(emptyList(), 500))
        assertEquals(listOf(SeccionTurnos.OTROS), seccionesTurnos(listOf(turno("otro", "otro", 600)), 500).keys.toList())
    }
}
