package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.*
import java.time.temporal.TemporalAdjusters

data class ReporteGanancias(
    val desde: LocalDate,
    val hasta: LocalDate,
    val realizados: List<Turno>,
    val totalCentavos: Long,
    val sinImporte: Int
)

class CalcularReporteGananciasUseCase @Inject constructor(
    private val clock: Clock
) {
    constructor() : this(Clock.system(ZoneId.of("America/Argentina/Buenos_Aires")))
    operator fun invoke(turnos: List<Turno>, mensual: Boolean, offset: Int): ReporteGanancias {
        val hoy = LocalDate.now(clock)
        val desde = if (mensual) hoy.withDayOfMonth(1).plusMonths(offset.toLong())
            else hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
        val fin = if (mensual) desde.plusMonths(1) else desde.plusWeeks(1)
        val inicioMillis = desde.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val finMillis = fin.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val realizados = turnos.filter { it.estadoNormalizado == EstadoTurno.REALIZADO &&
            it.inicioMillis?.let { fecha -> fecha >= inicioMillis && fecha < finMillis } == true }
            .sortedByDescending { it.inicioMillis }
        return ReporteGanancias(desde, fin.minusDays(1), realizados,
            realizados.sumOf { it.precioCobradoCentavos?.takeIf { precio -> precio >= 0 } ?: 0 },
            realizados.count { it.precioCobradoCentavos == null || it.precioCobradoCentavos < 0 })
    }
}
