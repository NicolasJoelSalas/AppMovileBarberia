package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import kotlinx.coroutines.flow.Flow

interface PublicacionRepository {
    fun observar(): Flow<AppResult<List<Publicacion>>>
    suspend fun publicar(publicacion: NuevaPublicacion): AppResult<Unit>
    suspend fun eliminar(id: String): AppResult<Unit>
    suspend fun modificarDescripcion(id: String, descripcion: String): AppResult<Unit>
}
