package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class OcupacionDiaTest {
    private val zona = ZoneId.of("America/Argentina/Buenos_Aires")
    private val fecha = LocalDate.parse("2026-11-02")
    private val dia = crearDiaAtencion(fecha, zona, DiaAtencion(true,
        listOf(FranjaAtencion("09:00", "13:00"), FranjaAtencion("16:00", "20:00"))))
    private fun hora(h: Int, m: Int = 0) = dia.inicioMillis + (h * 60 + m) * 60000L

    @Test fun `each free interval has one option without overlapping starts every five minutes`() {
        val franja = crearDiaAtencion(fecha,zona,DiaAtencion(true,
            listOf(FranjaAtencion("09:50","11:00"))))
        assertEquals(listOf(HorarioDisponible(hora(9,50),hora(10,30))),franja.disponibles(40,hora(8)))
        assertEquals(listOf(hora(9),hora(9,40),hora(10,20),hora(11),hora(11,40),hora(12,20),
            hora(16),hora(16,40),hora(17,20),hora(18),hora(18,40),hora(19,20)),
            dia.disponibles(40,hora(8)).map { it.inicioMillis })
        for (minutos in listOf(40,41,60,75,90)) {
            val opciones = dia.disponibles(minutos,hora(8))
            assertTrue(opciones.zipWithNext().all { (a,b) -> a.finMillis <= b.inicioMillis })
        }
    }

    @Test fun `editing preserves an off grid original time without introducing overlapping options`() {
        val opciones = dia.disponibles(40,hora(8),inicioPreferido=hora(9,50))
        assertTrue(opciones.contains(HorarioDisponible(hora(9,50),hora(10,30))))
        assertTrue(opciones.zipWithNext().all { (a,b) -> a.finMillis <= b.inicioMillis })
        val ocupado = dia.ocupar(hora(9,50),hora(10,30))
        val bloqueado = ocupado.disponibles(40,hora(8),inicioPreferido=hora(9,50))
        assertFalse(bloqueado.any { it.inicioMillis == hora(9,50) })
        assertTrue(bloqueado.zipWithNext().all { (a,b) -> a.finMillis <= b.inicioMillis })
    }

    @Test fun `services of different durations stay entirely inside the published ranges`() {
        for (minutos in listOf(5, 40, 41, 60, 75, 90, 240)) {
            val horarios = dia.disponibles(minutos, hora(8))
            assertTrue(horarios.isNotEmpty())
            for (h in horarios) {
                assertEquals(minutos * 60000L, h.finMillis - h.inicioMillis)
                assertTrue((h.inicioMillis >= hora(9) && h.finMillis <= hora(13)) ||
                    (h.inicioMillis >= hora(16) && h.finMillis <= hora(20)))
            }
        }
        assertTrue(dia.disponibles(60, hora(10)).all { it.inicioMillis > hora(10) })
    }
    @Test fun `forty one minute service frees exactly its finish and cancellation restores availability`() {
        val ocupado = dia.ocupar(hora(9), hora(9, 41))
        assertEquals(hora(9, 41), ocupado.disponibles(75, hora(8)).first().inicioMillis)
        assertEquals(dia, ocupado.liberar(hora(9), hora(9, 41)))
    }
    @Test fun `cannot cross breaks close outside the day or book an occupied interval`() {
        for ((a,b) in listOf(hora(12,30) to hora(13,30), hora(19,30) to hora(20,30),
            hora(8) to hora(9), hora(23) to hora(25))) {
            try { dia.ocupar(a,b); fail("No debe reservar fuera de una franja") } catch (_: IllegalArgumentException) { }
        }
        val ocupado = dia.ocupar(hora(9), hora(10))
        try { ocupado.ocupar(hora(9,30),hora(10,30)); fail("No debe superponerse") } catch (_: IllegalArgumentException) { }
        assertEquals(hora(10), ocupado.disponibles(90,hora(8)).first().inicioMillis)
    }
    @Test fun `a failed modification preserves the original immutable map`() {
        val original = dia.ocupar(hora(9),hora(10)).ocupar(hora(10),hora(11))
        try { original.liberar(hora(9),hora(10)).ocupar(hora(9,30),hora(10,30)); fail("Horario ocupado") }
        catch (_: IllegalArgumentException) { }
        assertEquals("1".repeat(120), original.ocupacion.substring(540,660))
    }
    @Test fun `same start can grow to seventy five minutes after releasing its own interval`() {
        val actual = dia.ocupar(hora(9),hora(10))
        val editado = actual.liberar(hora(9),hora(10)).ocupar(hora(9),hora(10,15))
        assertEquals(hora(10,15),editado.disponibles(60,hora(8)).first().inicioMillis)
        assertEquals("1".repeat(60),actual.ocupacion.substring(540,600))
    }
    @Test fun `closed day has no availability and invalid spans are rejected`() {
        assertTrue(crearDiaAtencion(fecha,zona,DiaAtencion(false,emptyList())).disponibles(60,hora(8)).isEmpty())
        for (rangos in listOf(listOf(FranjaAtencion("09:00","09:04")),
            listOf(FranjaAtencion("09:00","11:00"),FranjaAtencion("10:00","12:00")))) {
            try { crearDiaAtencion(fecha,zona,DiaAtencion(true,rangos)); fail("Franja inválida") }
            catch (_: IllegalArgumentException) { }
        }
        for (minutos in listOf(4,241)) {
            try { dia.disponibles(minutos,hora(8)); fail("Duración inválida") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `day masks follow the actual length of daylight saving days`() {
        val ny = ZoneId.of("America/New_York")
        val primavera = crearDiaAtencion(LocalDate.parse("2026-03-08"),ny,DiaAtencion(false,emptyList()))
        val otono = crearDiaAtencion(LocalDate.parse("2026-11-01"),ny,DiaAtencion(false,emptyList()))
        assertEquals(1380,primavera.atencion.length)
        assertEquals(1500,otono.atencion.length)
    }
}
