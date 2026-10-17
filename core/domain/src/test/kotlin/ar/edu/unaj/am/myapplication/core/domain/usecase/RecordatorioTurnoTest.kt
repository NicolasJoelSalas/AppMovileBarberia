package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordatorioTurnoTest {
    private fun millis(date: String) = Instant.parse(date).toEpochMilli()
    private fun turno(inicio: Long?, estado: String = "Confirmado") = Turno(
        "turno", "cliente", "barbero", "corte", inicio, null, estado)

    @Test fun `programa una hora antes en la fecha de la proxima semana`() {
        assertEquals(millis("2026-10-24T12:00:00Z"), instanteRecordatorio(
            turno(millis("2026-10-24T13:00:00Z")), millis("2026-10-16T10:00:00Z")))
    }

    @Test fun `cruza medianoche y ano sin cambiar el instante por zona horaria`() {
        assertEquals(millis("2026-12-31T23:30:00Z"), instanteRecordatorio(
            turno(millis("2027-01-01T00:30:00Z")), millis("2026-12-31T20:00:00Z")))
    }

    @Test fun `rechaza recordatorios vencidos aunque el turno aun sea futuro`() {
        val now = millis("2026-10-16T12:00:00Z")
        assertNull(instanteRecordatorio(turno(now + 3_600_000L), now))
        assertNull(instanteRecordatorio(turno(now + 1_800_000L), now))
        assertNull(instanteRecordatorio(turno(now - 1), now))
    }

    @Test fun `rechaza fechas ausentes y estados no reservados`() {
        val now = millis("2026-10-16T12:00:00Z")
        assertNull(instanteRecordatorio(turno(null), now))
        listOf("Cancelado", "Realizado", "Completado", "Sin estado").forEach {
            assertNull(instanteRecordatorio(turno(now + 7_200_000L, it), now))
        }
        assertEquals(now + 3_600_000L, instanteRecordatorio(turno(now + 7_200_000L, "Pendiente"), now))
    }
}
