package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltrarTurnosPorSemanaUseCaseTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private fun turno(id: String, instant: String?) = Turno(
        id = id, clienteId = "cliente", barberoId = "barbero", servicioId = "corte",
        inicioMillis = instant?.let { Instant.parse(it).toEpochMilli() }, finMillis = null,
        estado = "confirmado")

    @Test fun `weeks use local Monday inclusive and next Monday exclusive`() {
        val filter = FiltrarTurnosPorSemanaUseCase(Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), zone))
        val turnos = listOf(
            turno("anterior", "2026-10-05T02:59:59Z"),
            turno("lunes", "2026-10-05T03:00:00Z"),
            turno("domingo", "2026-10-12T02:59:59Z"),
            turno("proxima", "2026-10-12T03:00:00Z"),
            turno("fuera", "2026-10-19T03:00:00Z"),
            turno("sin-fecha", null))
        assertEquals(listOf("lunes", "domingo"), filter(turnos, 0).map { it.id })
        assertEquals(listOf("proxima"), filter(turnos, 1).map { it.id })
    }

    @Test fun `new Monday changes current week including across years`() {
        val filter = FiltrarTurnosPorSemanaUseCase(Clock.fixed(Instant.parse("2026-12-28T03:00:00Z"), zone))
        val turnos = listOf(turno("enero", "2027-01-02T15:00:00Z"),
            turno("siguiente", "2027-01-04T03:00:00Z"))
        assertEquals(listOf("enero"), filter(turnos, 0).map { it.id })
        assertEquals(listOf("siguiente"), filter(turnos, 1).map { it.id })
    }
}
