package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository

class CargarCatalogoReservasUseCase(private val repository: ReservaRepository) {
    suspend operator fun invoke() = repository.cargarCatalogo()
}
class ConsultarSemanaUseCase(private val repository: ReservaRepository) {
    suspend operator fun invoke(barberoId: String, servicioId: String, semanaOffset: Int = 0): AppResult<SemanaDisponible> =
        if (barberoId.isBlank() || servicioId.isBlank())
            AppResult.Error(DomainError.Unknown("Seleccioná un barbero y un servicio."))
        else if (semanaOffset !in 0..1) AppResult.Error(DomainError.Unknown("Elegí la semana actual o la próxima."))
        else repository.consultarSemana(barberoId, servicioId, semanaOffset)
}
class ReservarTurnoUseCase(private val repository: ReservaRepository) {
    suspend operator fun invoke(solicitud: SolicitudReserva): AppResult<String> =
        if (solicitud.barberoId.isBlank() || solicitud.servicioId.isBlank() ||
            solicitud.solicitudId.isBlank() || solicitud.nombreCliente.isBlank() ||
            solicitud.nombreCliente.trim().length > 120)
            AppResult.Error(DomainError.Unknown("Completá tu nombre, barbero y servicio."))
        else repository.reservar(solicitud.copy(nombreCliente = solicitud.nombreCliente.trim()))
}
