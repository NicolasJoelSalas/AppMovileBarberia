package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.*

class ObtenerMiUbicacionUseCase @Inject constructor(private val repository: UbicacionBarberiaRepository) {
    suspend operator fun invoke() = repository.obtenerMiUbicacion()
}

class ObtenerUbicacionBarberiaUseCase @Inject constructor(private val repository: UbicacionBarberiaRepository) {
    suspend operator fun invoke(barberoId: String): AppResult<Coordenadas?> =
        if (barberoId.isBlank() || '/' in barberoId) AppResult.Error(DomainError.NotFound)
        else repository.obtenerUbicacion(barberoId)
}

class GuardarMiUbicacionUseCase @Inject constructor(private val repository: UbicacionBarberiaRepository) {
    suspend operator fun invoke(ubicacion: Coordenadas): AppResult<Unit> =
        if (!ubicacion.esValida) AppResult.Error(DomainError.Known(ErrorReason.LOCATION_INVALID))
        else repository.guardarMiUbicacion(ubicacion)
}

class CalcularRutaBarberiaUseCase @Inject constructor(private val repository: RutaRepository) {
    suspend operator fun invoke(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> =
        if (!origen.esValida || !destino.esValida)
            AppResult.Error(DomainError.Known(ErrorReason.ROUTE_COORDINATES))
        else repository.calcular(origen, destino)
}
