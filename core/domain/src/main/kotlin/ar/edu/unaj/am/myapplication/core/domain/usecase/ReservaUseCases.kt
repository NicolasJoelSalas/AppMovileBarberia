package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository

class CargarCatalogoReservasUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke() = repository.cargarCatalogo()
}
class ConsultarSemanaUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke(barberoId: String, servicioId: String, semanaOffset: Int = 0, turnoId: String? = null): AppResult<SemanaDisponible> =
        if (barberoId.isBlank() || servicioId.isBlank())
            AppResult.Error(DomainError.Known(ErrorReason.BOOKING_SELECTION))
        else if (semanaOffset !in 0..1) AppResult.Error(DomainError.Known(ErrorReason.WEEK_SELECTION))
        else repository.consultarSemana(barberoId, servicioId, semanaOffset, turnoId)
}
class ReservarTurnoUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke(solicitud: SolicitudReserva): AppResult<String> =
        if (solicitud.barberoId.isBlank() || solicitud.servicioId.isBlank() ||
            solicitud.solicitudId.isBlank() || !nombreClienteReservaValido(solicitud.nombreCliente))
            AppResult.Error(DomainError.Known(ErrorReason.BOOKING_FIELDS))
        else repository.reservar(solicitud.copy(nombreCliente = solicitud.nombreCliente.trim()))
}

class CancelarTurnoUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke(turnoId: String) = repository.cancelar(turnoId)
}
class ModificarTurnoUseCase @Inject constructor(private val repository: ReservaRepository) {
    suspend operator fun invoke(turnoId: String, solicitud: SolicitudReserva): AppResult<String> =
        if (turnoId.isBlank() || solicitud.barberoId.isBlank() || solicitud.servicioId.isBlank() ||
            !nombreClienteReservaValido(solicitud.nombreCliente))
            AppResult.Error(DomainError.Known(ErrorReason.BOOKING_FIELDS))
        else repository.modificar(turnoId, solicitud.copy(nombreCliente = solicitud.nombreCliente.trim()))
}

fun nombreClienteReservaValido(nombre: String): Boolean = nombre.isNotBlank() && nombre.trim().length <= 120
