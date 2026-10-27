package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ServicioRepository

class ObtenerMisServiciosUseCase @Inject constructor(private val repository: ServicioRepository) {
    suspend operator fun invoke() = repository.obtenerMisServicios()
}

class GuardarServicioUseCase @Inject constructor(private val repository: ServicioRepository) {
    suspend operator fun invoke(servicio: ServicioBarbero): AppResult<Unit> {
        val nombre = servicio.nombre.trim()
        if (nombre.isEmpty() || nombre.length > 120 || servicio.precioCentavos !in 0..100000000000L ||
            servicio.duracionMinutos !in 5..240) {
            return AppResult.Error(DomainError.Known(ErrorReason.SERVICE_FIELDS))
        }
        return repository.guardar(servicio.copy(nombre = nombre))
    }
}

class CambiarEstadoServicioUseCase @Inject constructor(private val repository: ServicioRepository) {
    suspend operator fun invoke(id: String, activo: Boolean): AppResult<Unit> =
        if (id.isBlank()) AppResult.Error(DomainError.Known(ErrorReason.SERVICE_SELECTION))
        else repository.cambiarEstado(id, activo)
}
