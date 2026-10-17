package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordatorioTurnoTest {
    private val zonaHorariaArgentina = java.time.ZoneId.of("America/Argentina/Buenos_Aires")
    private fun millis(date: String) = Instant.parse(date).toEpochMilli()
    private fun turno(inicio: Long?, estado: String = "Confirmado") = Turno(
        "turno", "cliente", "barbero", "corte", inicio, null, estado)

    @Test fun `programa dos avisos UTC un dia y una hora antes`() {
        assertEquals(listOf(millis("2026-10-23T13:00:00Z"), millis("2026-10-24T12:00:00Z")),
            instantesRecordatorios(turno(millis("2026-10-24T13:00:00Z")), millis("2026-10-16T10:00:00Z")))
    }

    @Test fun `los avisos conservan su instante UTC en otros paises y cambios de horario de verano`() {
        val inicio = millis("2026-03-29T08:00:00Z")
        val avisos = instantesRecordatorios(turno(inicio), millis("2026-03-27T00:00:00Z"))
        listOf("America/Argentina/Buenos_Aires", "Europe/Madrid", "Asia/Tokyo").forEach { nombre ->
            val zona = java.time.ZoneId.of(nombre)
            val local = Instant.ofEpochMilli(inicio).atZone(zona)
            assertEquals(avisos, instantesRecordatorios(turno(local.toInstant().toEpochMilli()),
                millis("2026-03-27T00:00:00Z")))
        }
        assertEquals(86_400_000L, inicio - avisos.first())
        assertEquals(3_600_000L, inicio - avisos.last())
    }

    @Test fun `si falta menos de un dia conserva solo el aviso futuro`() {
        val inicio = millis("2026-10-24T13:00:00Z")
        assertEquals(listOf(inicio - 3_600_000L), instantesRecordatorios(turno(inicio), inicio - 86_400_000L))
        assertEquals(emptyList<Long>(), instantesRecordatorios(turno(inicio), inicio - 3_600_000L))
        assertEquals(emptyList<Long>(), instantesRecordatorios(turno(inicio, "Cancelado"), inicio - 172_800_000L))
        assertEquals(emptyList<Long>(), instantesRecordatorios(turno(null), inicio))
    }

    @Test fun `medianoche argentina cruza el dia y ano con instantes universales`() {
        val inicio = java.time.LocalDateTime.parse("2027-01-01T00:30:00")
            .atZone(zonaHorariaArgentina).toInstant().toEpochMilli()
        assertEquals(millis("2027-01-01T03:30:00Z"), inicio)
        assertEquals(listOf(millis("2026-12-31T03:30:00Z"), millis("2027-01-01T02:30:00Z")),
            instantesRecordatorios(turno(inicio), millis("2026-12-30T00:00:00Z")))
        val horaArgentina = Instant.ofEpochMilli(inicio - 3_600_000L).atZone(zonaHorariaArgentina)
        assertEquals("2026-12-31T23:30", horaArgentina.toLocalDateTime().toString())
    }

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
