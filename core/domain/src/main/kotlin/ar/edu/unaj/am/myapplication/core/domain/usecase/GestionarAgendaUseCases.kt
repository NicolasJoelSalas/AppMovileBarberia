package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import java.time.*

class ObtenerMiAgendaUseCase(private val repository: ReservaRepository) {
    suspend operator fun invoke(semanaOffset: Int = 0): AppResult<AgendaSemanal> =
        if (semanaOffset !in 0..1) AppResult.Error(DomainError.Unknown("Elegí la semana actual o la próxima."))
        else repository.obtenerMiAgenda(semanaOffset)
}

/** Se valida la configuración completa, incluso los días que ya pasaron esta semana. */
class ValidarAgendaUseCase {
    operator fun invoke(agenda: AgendaSemanal): String? = try {
        ZoneId.of(agenda.zonaHoraria)
        require(agenda.dias.size == 6) { "Configurá de lunes a sábado." }
        val nombres = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
        agenda.dias.forEachIndexed { index, day ->
            if (day.habilitado) {
                require(day.franjas.isNotEmpty()) { "${nombres[index]}: agregá una franja o cerrá el día." }
                val spans = day.franjas.map { span ->
                    require(span.inicio.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) &&
                        span.fin.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) {
                        "${nombres[index]}: ingresá horas válidas con formato HH:mm."
                    }
                    val start = LocalTime.parse(span.inicio)
                    val end = LocalTime.parse(span.fin)
                    require(Duration.between(start, end).toMinutes() >= 40) {
                        "${nombres[index]}: cada franja debe permitir al menos un corte de 40 minutos."
                    }
                    start to end
                }.sortedBy { it.first }
                require(spans.zipWithNext().none { (a, b) -> a.second > b.first }) {
                    "${nombres[index]}: las franjas no pueden superponerse."
                }
            }
        }
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "Revisá la agenda."
    } catch (e: DateTimeException) {
        "Revisá las horas y la zona horaria."
    }
}

data class ResultadoGuardarAgenda(val horariosPublicados: Int?, val errorPublicacion: DomainError?)

class GuardarYPublicarAgendaUseCase(
    private val repository: ReservaRepository,
    private val publicar: PublicarSemanaUseCase,
    private val validar: ValidarAgendaUseCase = ValidarAgendaUseCase()
) {
    suspend operator fun invoke(agenda: AgendaSemanal): AppResult<ResultadoGuardarAgenda> {
        validar(agenda)?.let { return AppResult.Error(DomainError.Unknown(it)) }
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
