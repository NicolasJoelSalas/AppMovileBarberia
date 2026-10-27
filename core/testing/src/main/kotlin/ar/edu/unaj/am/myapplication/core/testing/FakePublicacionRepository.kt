package ar.edu.unaj.am.myapplication.core.testing

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.NuevaPublicacion
import ar.edu.unaj.am.myapplication.core.domain.model.Publicacion
import ar.edu.unaj.am.myapplication.core.domain.repository.PublicacionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Emite publicaciones y registra solicitudes sin acceder a Firebase ni a imágenes reales. */
class FakePublicacionRepository : PublicacionRepository {
    val publicaciones = MutableStateFlow<AppResult<List<Publicacion>>>(AppResult.Success(emptyList()))
    val solicitudes = mutableListOf<NuevaPublicacion>()
    val borradas = mutableListOf<String>()
    val modificadas = mutableListOf<Pair<String, String>>()
    var resultado: AppResult<Unit> = AppResult.Success(Unit)
    var pendiente: CompletableDeferred<AppResult<Unit>>? = null

    override fun observar() = publicaciones

    override suspend fun publicar(publicacion: NuevaPublicacion): AppResult<Unit> {
        solicitudes += publicacion
        return pendiente?.await() ?: resultado
    }

    override suspend fun modificarDescripcion(id: String, descripcion: String): AppResult<Unit> {
        modificadas += id to descripcion
        return pendiente?.await() ?: resultado
    }

    override suspend fun eliminar(id: String): AppResult<Unit> {
        borradas += id
        return pendiente?.await() ?: resultado
    }
}
