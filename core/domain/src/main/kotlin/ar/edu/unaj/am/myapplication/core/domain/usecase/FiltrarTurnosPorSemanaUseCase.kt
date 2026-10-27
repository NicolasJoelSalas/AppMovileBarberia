package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class FiltrarTurnosPorSemanaUseCase @Inject constructor(
    private val clock: Clock
) {
    constructor() : this(Clock.system(ZoneId.of("America/Argentina/Buenos_Aires")))
    operator fun invoke(turnos: List<Turno>, semanaOffset: Int): List<Turno> {
        require(semanaOffset in 0..1)
        val lunes = LocalDate.now(clock)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(semanaOffset.toLong())
        val inicio = lunes.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val fin = lunes.plusWeeks(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return turnos.filter { it.inicioMillis?.let { hora -> hora >= inicio && hora < fin } == true }
    }
}
