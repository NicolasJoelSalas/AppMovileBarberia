package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.*

class ObtenerMiUbicacionUseCase(private val repository: UbicacionBarberiaRepository) {
    suspend operator fun invoke() = repository.obtenerMiUbicacion()
}

class ObtenerUbicacionBarberiaUseCase(private val repository: UbicacionBarberiaRepository) {
    suspend operator fun invoke(barberoId: String): AppResult<Coordenadas?> =
        if (barberoId.isBlank() || '/' in barberoId) AppResult.Error(DomainError.NotFound)
        else repository.obtenerUbicacion(barberoId)
}

class GuardarMiUbicacionUseCase(private val repository: UbicacionBarberiaRepository) {
    suspend operator fun invoke(ubicacion: Coordenadas): AppResult<Unit> =
        if (!ubicacion.esValida) AppResult.Error(DomainError.Unknown("Seleccioná un punto válido en el mapa."))
        else repository.guardarMiUbicacion(ubicacion)
}

class CalcularRutaBarberiaUseCase(private val repository: RutaRepository) {
    suspend operator fun invoke(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> =
        if (!origen.esValida || !destino.esValida)
            AppResult.Error(DomainError.Unknown("No se puede calcular la ruta con estas coordenadas."))
        else repository.calcular(origen, destino)
}
