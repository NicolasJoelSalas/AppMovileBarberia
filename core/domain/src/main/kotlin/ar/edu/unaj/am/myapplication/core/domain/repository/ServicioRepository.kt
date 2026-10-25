package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*

interface ServicioRepository {
    suspend fun obtenerMisServicios(): AppResult<List<ServicioBarbero>>
    suspend fun guardar(servicio: ServicioBarbero): AppResult<Unit>
    suspend fun cambiarEstado(id: String, activo: Boolean): AppResult<Unit>
}
