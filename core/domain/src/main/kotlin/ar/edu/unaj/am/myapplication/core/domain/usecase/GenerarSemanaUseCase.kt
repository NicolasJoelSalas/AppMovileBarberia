package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import java.time.*
import java.time.temporal.TemporalAdjusters

/** Regla de negocio: cortes de 40 minutos, de lunes a sábado, dentro de cada franja. */
class GenerarSemanaUseCase @Inject constructor() {
    operator fun invoke(agenda: AgendaSemanal, ahora: Instant, turnos: List<Turno> = emptyList()): SemanaDisponible {
        val zone = ZoneId.of(agenda.zonaHoraria)
        requireDomain(agenda.dias.size == 6, ErrorReason.SCHEDULE_DAYS)
        val currentMonday = ahora.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monday = agenda.inicioSemana?.let(LocalDate::parse) ?: currentMonday
        requireDomain(monday == currentMonday || monday == currentMonday.plusWeeks(1), ErrorReason.SCHEDULE_WEEK_CHANGED)
        val slots = agenda.dias.flatMapIndexed { index, day ->
            if (!day.habilitado) emptyList() else {
                val date = monday.plusDays(index.toLong())
                val spans = day.franjas.map {
                    requireDomain(it.inicio.matches(Regex("[0-2][0-9]:[0-5][0-9]")) &&
                        it.fin.matches(Regex("[0-2][0-9]:[0-5][0-9]")), ErrorReason.SCHEDULE_TIME_FORMAT)
                    val start = date.atTime(LocalTime.parse(it.inicio)).atZone(zone).toInstant()
                    val end = date.atTime(LocalTime.parse(it.fin)).atZone(zone).toInstant()
                    requireDomain(end > start, ErrorReason.SCHEDULE_END)
                    start to end
                }.sortedBy { it.first }
                requireDomain(spans.zipWithNext().none { (a, b) -> a.second > b.first }, ErrorReason.SCHEDULE_OVERLAP)
                spans.flatMap { (begin, end) ->
                    buildList {
                        var start = begin
                        while (!start.plusSeconds(2400).isAfter(end)) {
                            if (start > ahora) add(HorarioDisponible(start.toEpochMilli(), start.plusSeconds(2400).toEpochMilli()))
                            start = start.plusSeconds(2400)
                        }
                    }
                }
            }
        }
        val occupied = turnos.filter { it.estado.lowercase() !in listOf("false", "cancelado", "cancelada") }.map {
            val start = it.inicioMillis ?: throw DomainValidationException(DomainError.Known(ErrorReason.APPOINTMENT_START_MISSING))
            val end = it.finMillis ?: throw DomainValidationException(DomainError.Known(ErrorReason.APPOINTMENT_END_MISSING))
            requireDomain(end > start, ErrorReason.APPOINTMENT_DURATION)
            start to end
        }
        val available = slots.filter { slot ->
            occupied.none { (start, end) -> slot.inicioMillis < end && slot.finMillis > start }
        }
        return SemanaDisponible(monday.atStartOfDay(zone).toInstant().toEpochMilli(), available, agenda.zonaHoraria)
    }
}

class PublicarSemanaUseCase @Inject constructor(
    private val repository: ReservaRepository,
    private val generar: GenerarSemanaUseCase = GenerarSemanaUseCase(),
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(semanaOffset: Int = 0, agendaGuardada: AgendaSemanal? = null): AppResult<Int> =
        when (val result = if (agendaGuardada != null) AppResult.Success(agendaGuardada)
            else repository.obtenerMiAgenda(semanaOffset)) {
        is AppResult.Error -> result
        is AppResult.Success -> try {
            when (val turnos = repository.obtenerMisTurnos()) {
                is AppResult.Error -> turnos
                is AppResult.Success -> repository.publicarSemana(result.data,
                    generar(result.data, clock.instant(), turnos.data))
            }
        } catch (e: DomainValidationException) {
            AppResult.Error(e.error)
        } catch (e: IllegalArgumentException) {
            AppResult.Error(DomainError.Known(ErrorReason.SCHEDULE_ZONE))
        } catch (e: DateTimeException) {
            AppResult.Error(DomainError.Known(ErrorReason.SCHEDULE_ZONE))
        }
    }
}
