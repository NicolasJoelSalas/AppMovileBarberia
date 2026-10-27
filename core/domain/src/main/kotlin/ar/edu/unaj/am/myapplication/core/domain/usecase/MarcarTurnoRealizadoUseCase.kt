package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import java.time.Clock

class MarcarTurnoRealizadoUseCase @Inject constructor(
    private val repository: TurnoRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(turno: Turno): AppResult<Unit> {
        if (turno.id.isBlank() || turno.estado.lowercase() !in listOf("confirmado", "pendiente"))
            return AppResult.Error(DomainError.Known(ErrorReason.APPOINTMENT_PENDING))
        if (turno.inicioMillis == null || turno.inicioMillis > clock.millis())
            return AppResult.Error(DomainError.Known(ErrorReason.APPOINTMENT_NOT_STARTED))
        return repository.marcarRealizado(turno.id)
    }
}
