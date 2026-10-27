package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PoliticaReservasTest {
    private val instante = Instant.parse("2026-10-26T01:00:00Z")
    private val politica = PoliticaReservas(Clock.fixed(instante, ZoneOffset.UTC))
    private val zona = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test fun `la semana depende de la zona del barbero y no del dispositivo`() {
        assertEquals(LocalDate.parse("2026-10-19"), politica.lunes(zona, 0))
        assertEquals(LocalDate.parse("2026-10-26"), politica.lunes(ZoneOffset.UTC, 0))
        assertEquals(LocalDate.parse("2026-10-26"), politica.lunes(zona, 1))
        assertEquals(1, politica.offsetOriginal(Instant.parse("2026-10-26T12:00:00Z").toEpochMilli(), zona))
    }
    @Test fun `solo admite esta semana y la siguiente`() {
        politica.validarSemana(LocalDate.parse("2026-10-19"), zona)
        politica.validarSemana(LocalDate.parse("2026-10-26"), zona)
        error(ErrorReason.SCHEDULE_WEEK_CHANGED) { politica.validarSemana(LocalDate.parse("2026-11-02"), zona) }
        error(ErrorReason.WEEK_SELECTION) { politica.lunes(zona, 2) }
    }
    @Test fun `admite servicios largos dentro de los limites`() {
        listOf(5,40,90,240).forEach { assertEquals(it, politica.duracion(it)) }
        listOf(0,4,241).forEach { duracion -> error(ErrorReason.SERVICE_FIELDS) { politica.duracion(duracion) } }
    }
    @Test fun `solo se editan pendientes futuros incluyendo el formato antiguo`() {
        listOf("pendiente","Confirmado","true").forEach { politica.validarEditable(it, instante.toEpochMilli()+60000) }
        listOf("cancelado","realizado","false").forEach { estado ->
            error(ErrorReason.BOOKING_NOT_EDITABLE) { politica.validarEditable(estado, instante.toEpochMilli()+60000) }
        }
        error(ErrorReason.BOOKING_NOT_EDITABLE) { politica.validarEditable("pendiente", instante.toEpochMilli()) }
    }
    @Test fun `solo se completan pendientes iniciados`() {
        politica.validarRealizable("true", instante.toEpochMilli())
        error(ErrorReason.APPOINTMENT_PENDING) { politica.validarRealizable("cancelado", instante.toEpochMilli()) }
        error(ErrorReason.APPOINTMENT_NOT_STARTED) { politica.validarRealizable("pendiente", null) }
        error(ErrorReason.APPOINTMENT_NOT_STARTED) { politica.validarRealizable("pendiente", instante.toEpochMilli()+1) }
    }
    @Test fun `republicar cancela futuros excluidos y conserva realizados y en curso`() {
        val ahora = instante.toEpochMilli()
        val sinAtencion = OcupacionDia(ahora-60000, "0".repeat(180), "0".repeat(180))
        assertTrue(politica.cancelarAlPublicar("pendiente", ahora+60000, ahora+120000, sinAtencion))
        assertFalse(politica.cancelarAlPublicar("pendiente", ahora-60000, ahora+120000, sinAtencion))
        assertFalse(politica.cancelarAlPublicar("realizado", ahora+60000, ahora+120000, sinAtencion))
        assertFalse(politica.cancelarAlPublicar("pendiente", ahora+60000, ahora+120000, sinAtencion.copy(atencion="1".repeat(180))))
    }
    private fun error(reason: ErrorReason, bloque: () -> Unit) {
        try { bloque(); fail("Se esperaba $reason") }
        catch (e: DomainValidationException) { assertEquals(DomainError.Known(reason), e.error) }
    }
}
