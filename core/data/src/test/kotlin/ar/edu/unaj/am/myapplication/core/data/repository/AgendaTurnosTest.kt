package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AgendaTurnosTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val agenda = AgendaSemanal(zone.id, List(6) {
        DiaAtencion(true, listOf(FranjaAtencion("09:00", "13:00"), FranjaAtencion("16:00", "20:00")))
    })
    private fun time(hora: String) = LocalDate.parse("2026-10-26").atTime(LocalTime.parse(hora)).atZone(zone)

    @Test fun `removed range excludes its bookings and preserves remaining range`() {
        val changed = agenda.copy(dias = agenda.dias.map {
            it.copy(franjas = it.franjas.drop(1))
        })
        assertFalse(changed.contieneTurno(time("09:00"), time("09:40")))
        assertTrue(changed.contieneTurno(time("16:00"), time("16:40")))
        assertTrue(changed.contieneTurno(time("19:20"), time("20:00")))
    }

    @Test fun `bookings crossing a boundary or a break are excluded`() {
        assertFalse(agenda.contieneTurno(time("12:40"), time("13:20")))
        assertFalse(agenda.contieneTurno(time("15:40"), time("16:20")))
        assertFalse(agenda.contieneTurno(time("19:40"), time("20:20")))
    }

    @Test fun `closed days exclude bookings without affecting other days`() {
        val changed = agenda.copy(dias = agenda.dias.mapIndexed { index, day ->
            if (index == 0) day.copy(habilitado = false) else day
        })
        assertFalse(changed.contieneTurno(time("09:00"), time("09:40")))
        assertTrue(changed.contieneTurno(time("09:00").plusDays(1), time("09:40").plusDays(1)))
    }
}
