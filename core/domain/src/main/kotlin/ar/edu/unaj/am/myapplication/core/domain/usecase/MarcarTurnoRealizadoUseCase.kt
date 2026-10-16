package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import java.time.Clock

class MarcarTurnoRealizadoUseCase(
    private val repository: TurnoRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(turno: Turno): AppResult<Unit> {
        if (turno.id.isBlank() || turno.estado.lowercase() !in listOf("confirmado", "pendiente"))
            return AppResult.Error(DomainError.Unknown("Solo se pueden completar turnos pendientes."))
        if (turno.inicioMillis == null || turno.inicioMillis > clock.millis())
            return AppResult.Error(DomainError.Unknown("Podés completar el turno desde su hora de inicio."))
        return repository.marcarRealizado(turno.id)
    }
}
