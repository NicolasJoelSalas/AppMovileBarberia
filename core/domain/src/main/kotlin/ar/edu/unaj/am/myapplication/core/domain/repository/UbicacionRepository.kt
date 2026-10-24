package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*

interface UbicacionBarberiaRepository {
    suspend fun obtenerMiUbicacion(): AppResult<Coordenadas?>
    suspend fun obtenerUbicacion(barberoId: String): AppResult<Coordenadas?>
    suspend fun guardarMiUbicacion(ubicacion: Coordenadas): AppResult<Unit>
}

interface RutaRepository {
    suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia>
}
