package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import java.time.*

class ObtenerMiAgendaUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke(semanaOffset: Int = 0): AppResult<AgendaSemanal> =
        if (semanaOffset !in 0..1) AppResult.Error(DomainError.Known(ErrorReason.WEEK_SELECTION))
        else repository.obtenerMiAgenda(semanaOffset)
}

/** Se valida la configuración completa, incluso los días que ya pasaron esta semana. */
class ValidarAgendaUseCase @Inject constructor() {
    operator fun invoke(agenda: AgendaSemanal): DomainError? = try {
        ZoneId.of(agenda.zonaHoraria)
        requireDomain(agenda.dias.size == 6, ErrorReason.SCHEDULE_DAYS)
        agenda.dias.forEachIndexed { index, day ->
            if (day.habilitado) {
                requireDomain(day.franjas.isNotEmpty(), ErrorReason.SCHEDULE_EMPTY_DAY, index + 1)
                val spans = day.franjas.map { span ->
                    requireDomain(span.inicio.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) &&
                        span.fin.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")),
                        ErrorReason.SCHEDULE_TIME_FORMAT, index + 1)
                    val start = LocalTime.parse(span.inicio)
                    val end = LocalTime.parse(span.fin)
                    requireDomain(Duration.between(start, end).toMinutes() >= 5,
                        ErrorReason.SCHEDULE_DURATION, index + 1)
                    start to end
                }.sortedBy { it.first }
                requireDomain(spans.zipWithNext().none { (a, b) -> a.second > b.first },
                    ErrorReason.SCHEDULE_OVERLAP, index + 1)
            }
        }
        null
    } catch (e: DomainValidationException) {
        e.error
    } catch (e: IllegalArgumentException) {
        DomainError.Known(ErrorReason.SCHEDULE_ZONE)
    } catch (e: DateTimeException) {
        DomainError.Known(ErrorReason.SCHEDULE_ZONE)
    }
}

data class ResultadoGuardarAgenda(val horariosPublicados: Int?, val errorPublicacion: DomainError?)

class GuardarYPublicarAgendaUseCase @Inject constructor(
    private val repository: ReservaRepository,
    private val publicar: PublicarSemanaUseCase,
    private val validar: ValidarAgendaUseCase = ValidarAgendaUseCase()
) {
    suspend operator fun invoke(agenda: AgendaSemanal): AppResult<ResultadoGuardarAgenda> {
        validar(agenda)?.let { return AppResult.Error(it) }
        when (val saved = repository.guardarMiAgenda(agenda)) {
            is AppResult.Error -> return saved
            is AppResult.Success -> Unit
        }
        return when (val published = publicar(agendaGuardada = agenda)) {
            is AppResult.Error -> AppResult.Success(ResultadoGuardarAgenda(null, published.error))
            is AppResult.Success -> AppResult.Success(ResultadoGuardarAgenda(published.data, null))
        }
    }
}
