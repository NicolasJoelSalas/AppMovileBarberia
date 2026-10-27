package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import javax.inject.Inject

class PublicarSemanaUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke(semanaOffset: Int = 0, agendaGuardada: AgendaSemanal? = null): AppResult<Int> {
        if (semanaOffset !in 0..1) return AppResult.Error(DomainError.Known(ErrorReason.WEEK_SELECTION))
        return when (val result = agendaGuardada?.let { AppResult.Success(it) } ?: repository.obtenerMiAgenda(semanaOffset)) {
            is AppResult.Error -> result
            is AppResult.Success -> ValidarAgendaUseCase()(result.data)?.let { AppResult.Error(it) }
                ?: repository.publicarSemana(result.data)
        }
    }
}
