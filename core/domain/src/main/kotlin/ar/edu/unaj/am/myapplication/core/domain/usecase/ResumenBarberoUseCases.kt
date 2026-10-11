package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class CalcularResumenBarberoUseCase(
    private val clock: Clock = Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"))
) {
    operator fun invoke(turnos: List<Turno>, semanaOffset: Int = 0): ResumenBarbero {
        val lunes = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(semanaOffset.toLong())
        val inicio = lunes.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val fin = lunes.plusWeeks(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val semana = turnos.filter { it.inicioMillis?.let { hora -> hora >= inicio && hora < fin } == true }
            .sortedBy { it.inicioMillis }
        val realizados = semana.filter { it.estaRealizado() }
        return ResumenBarbero(lunes, semana.filter { it.estaPendiente() }, realizados,
            realizados.mapNotNull { it.precioCobradoCentavos?.takeIf { precio -> precio >= 0 } }.sum(),
            realizados.count { it.precioCobradoCentavos == null || it.precioCobradoCentavos < 0 })
    }
}

class MarcarTurnoRealizadoUseCase(
    private val repository: TurnoRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(turno: Turno): AppResult<Unit> {
        if (turno.id.isBlank()) return AppResult.Error(DomainError.Unknown("El turno no es válido."))
        if (!turno.estaPendiente()) return AppResult.Error(DomainError.Unknown("Solo se pueden completar turnos pendientes."))
        if (turno.finMillis == null || turno.finMillis > clock.millis())
            return AppResult.Error(DomainError.Unknown("Podés marcar el turno como realizado cuando finalice su horario."))
        if (turno.precioServicioCentavos == null || turno.precioServicioCentavos < 0)
            return AppResult.Error(DomainError.Unknown("El servicio necesita un Precio numérico válido antes de completar el turno."))
        return repository.marcarRealizado(turno.id, turno.precioServicioCentavos)
    }
}
