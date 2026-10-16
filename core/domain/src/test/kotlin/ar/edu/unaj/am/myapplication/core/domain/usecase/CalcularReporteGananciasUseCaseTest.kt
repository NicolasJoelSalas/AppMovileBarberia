package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CalcularReporteGananciasUseCaseTest {
    private val calcular = CalcularReporteGananciasUseCase(Clock.fixed(
        Instant.parse("2026-10-16T13:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires")))
    private fun turno(id: String, fecha: String, estado: String = "realizado", importe: Long? = 1250050) =
        Turno(id, "cliente", "barbero", "corte", Instant.parse(fecha).toEpochMilli(), null,
            estado, precioCobradoCentavos = importe)

    @Test fun `semanas usan lunes local excluyen pendientes y declaran importes faltantes`() {
        val reporte = calcular(listOf(
            turno("domingo", "2026-10-12T02:59:59Z"),
            turno("lunes", "2026-10-12T03:00:00Z"),
            turno("sin-precio", "2026-10-16T13:00:00Z", importe = null),
            turno("pendiente", "2026-10-16T13:00:00Z", "confirmado"),
            turno("proxima", "2026-10-19T03:00:00Z")), false, 0)
        assertEquals(LocalDate.of(2026, 10, 12), reporte.desde)
        assertEquals(1250050L, reporte.totalCentavos)
        assertEquals(1, reporte.sinImporte)
        assertEquals(2, reporte.realizados.size)
    }
    @Test fun `meses contemplan cambio de año y usan el importe historico`() {
        val reporte = calcular(listOf(turno("anterior", "2025-12-01T03:00:00Z", importe = 0),
            turno("ultimo", "2026-01-01T02:59:59Z", importe = 29),
            turno("enero", "2026-01-01T03:00:00Z")), true, -10)
        assertEquals(LocalDate.of(2025, 12, 1), reporte.desde)
        assertEquals(LocalDate.of(2025, 12, 31), reporte.hasta)
        assertEquals(29L, reporte.totalCentavos)
        assertEquals(2, reporte.realizados.size)
        assertEquals(0, reporte.sinImporte)
    }
}
