package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import java.time.*
import java.time.temporal.TemporalAdjusters

/** Regla de negocio: cortes de 40 minutos, de lunes a sábado, dentro de cada franja. */
class GenerarSemanaUseCase {
    operator fun invoke(agenda: AgendaSemanal, ahora: Instant, turnos: List<Turno> = emptyList()): SemanaDisponible {
        val zone = ZoneId.of(agenda.zonaHoraria)
        require(agenda.dias.size == 6) { "Configurá los seis días de atención." }
        val currentMonday = ahora.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monday = agenda.inicioSemana?.let(LocalDate::parse) ?: currentMonday
        require(monday == currentMonday || monday == currentMonday.plusWeeks(1)) {
            "La semana cambió. Volvé a cargar la agenda actual o la próxima."
        }
        val slots = agenda.dias.flatMapIndexed { index, day ->
            if (!day.habilitado) emptyList() else {
                val date = monday.plusDays(index.toLong())
                val spans = day.franjas.map {
                    require(it.inicio.matches(Regex("[0-2][0-9]:[0-5][0-9]")) &&
                        it.fin.matches(Regex("[0-2][0-9]:[0-5][0-9]"))) { "Usá horas con formato HH:mm." }
                    val start = date.atTime(LocalTime.parse(it.inicio)).atZone(zone).toInstant()
                    val end = date.atTime(LocalTime.parse(it.fin)).atZone(zone).toInstant()
                    require(end > start) { "El fin de una franja debe ser posterior al inicio." }
                    start to end
                }.sortedBy { it.first }
                require(spans.zipWithNext().none { (a, b) -> a.second > b.first }) {
                    "Las franjas de atención no pueden superponerse."
                }
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
            val start = requireNotNull(it.inicioMillis) { "Corregí Inicio de los turnos existentes." }
            val end = requireNotNull(it.finMillis) { "Corregí Fin de los turnos existentes." }
            require(end > start) { "Corregí la duración de los turnos existentes." }
            start to end
        }
        val available = slots.filter { slot ->
            occupied.none { (start, end) -> slot.inicioMillis < end && slot.finMillis > start }
        }
        return SemanaDisponible(monday.atStartOfDay(zone).toInstant().toEpochMilli(), available, agenda.zonaHoraria)
    }
}

class PublicarSemanaUseCase(
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
        } catch (e: IllegalArgumentException) {
            AppResult.Error(DomainError.Unknown(e.message ?: "Revisá la agenda semanal."))
        } catch (e: DateTimeException) {
            AppResult.Error(DomainError.Unknown("Revisá las horas y la zona horaria de la agenda."))
        }
    }
}
