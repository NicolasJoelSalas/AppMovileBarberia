package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class GenerarSemanaUseCaseTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val generator = GenerarSemanaUseCase()
    private fun instant(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant()
    private fun agenda(vararg spans: FranjaAtencion) = AgendaSemanal(zone.id,
        List(6) { DiaAtencion(true, spans.toList()) })

    @Test fun `40 minute intervals fit inside opening and closing without Sundays`() {
        val result = generator(agenda(FranjaAtencion("09:00", "11:00")), instant("2026-10-12T08:00"))
        assertEquals(18, result.horarios.size)
        assertTrue(result.horarios.all { it.finMillis - it.inicioMillis == 2400000L })
        assertTrue(result.horarios.none { Instant.ofEpochMilli(it.inicioMillis).atZone(zone).dayOfWeek == DayOfWeek.SUNDAY })
    }
    @Test fun `next week is available on Sunday and becomes current after rollover`() {
        val next = agenda(FranjaAtencion("09:00", "11:00")).copy(inicioSemana = "2026-10-19")
        val sunday = generator(next, instant("2026-10-18T08:00"))
        assertEquals(18, sunday.horarios.size)
        assertEquals(instant("2026-10-19T00:00").toEpochMilli(), sunday.inicioSemanaMillis)
        val monday = generator(next, instant("2026-10-19T08:00"))
        assertEquals(sunday, monday)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `weeks outside current and next cannot be published`() {
        generator(agenda(FranjaAtencion("09:00", "11:00")).copy(inicioSemana = "2026-10-26"),
            instant("2026-10-12T08:00"))
    }
    @Test fun `past appointments and incomplete final interval are excluded`() {
        val result = generator(agenda(FranjaAtencion("09:00", "10:30")), instant("2026-10-17T09:10"))
        assertEquals(1, result.horarios.size)
        assertEquals(instant("2026-10-17T09:40").toEpochMilli(), result.horarios.single().inicioMillis)
    }
    @Test fun `closed days and lunch breaks are respected`() {
        val config = agenda(FranjaAtencion("09:00", "09:40"),FranjaAtencion("16:00", "16:40"))
        val result = generator(config.copy(dias = config.dias.mapIndexed { index, day ->
            if (index == 0) day else day.copy(habilitado = false)
        }), instant("2026-10-12T08:00"))
        assertEquals(2, result.horarios.size)
    }
    @Test fun `Sunday has no future appointments and Monday starts a new week`() {
        val config = agenda(FranjaAtencion("09:00", "10:20"))
        assertTrue(generator(config, instant("2026-10-18T08:00")).horarios.isEmpty())
        val result = generator(config, instant("2026-10-19T08:00"))
        assertEquals(instant("2026-10-19T00:00").toEpochMilli(), result.inicioSemanaMillis)
        assertEquals(12, result.horarios.size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `overlapping spans are rejected`() {
        generator(agenda(FranjaAtencion("09:00", "11:00"),FranjaAtencion("10:00", "12:00")), instant("2026-10-12T08:00"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `reversed spans are rejected`() {
        generator(agenda(FranjaAtencion("11:00", "09:00")), instant("2026-10-12T08:00"))
    }
    @Test fun `legacy active appointment of another duration blocks overlapping slots only`() {
        val old = Turno("viejo", "cliente", "barbero", "servicio",
            instant("2026-10-12T09:20").toEpochMilli(), instant("2026-10-12T10:20").toEpochMilli(), "true")
        val result = generator(agenda(FranjaAtencion("09:00", "11:00")),
            instant("2026-10-12T08:00"), listOf(old))
        assertEquals(16, result.horarios.size)
        assertEquals(instant("2026-10-12T10:20").toEpochMilli(), result.horarios.first().inicioMillis)
        val cancelled = generator(agenda(FranjaAtencion("09:00", "11:00")),
            instant("2026-10-12T08:00"), listOf(old.copy(estado = "cancelado")))
        assertEquals(18, cancelled.horarios.size)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `corrupt legacy appointment prevents publishing misleading availability`() {
        generator(agenda(FranjaAtencion("09:00", "11:00")), instant("2026-10-12T08:00"),
            listOf(Turno("viejo", "", "barbero", "", null, null, "confirmado")))
    }
}
