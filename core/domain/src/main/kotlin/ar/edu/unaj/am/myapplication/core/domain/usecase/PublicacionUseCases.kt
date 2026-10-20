package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PublicacionRepository

class ObservarPublicacionesUseCase(private val repository: PublicacionRepository) {
    operator fun invoke() = repository.observar()
}

class PublicarTrabajoUseCase(private val repository: PublicacionRepository) {
    suspend operator fun invoke(publicacion: NuevaPublicacion): AppResult<Unit> {
        if (publicacion.solicitudId.isBlank() || publicacion.imagenLocal.isBlank())
            return AppResult.Error(DomainError.Unknown("Elegí una foto para publicar."))
        if (publicacion.descripcion.trim().length > 300)
            return AppResult.Error(DomainError.Unknown("La descripción admite hasta 300 caracteres."))
        if (!publicacion.recorteX.isFinite() || !publicacion.recorteY.isFinite() ||
            publicacion.recorteX !in -1f..1f || publicacion.recorteY !in -1f..1f)
            return AppResult.Error(DomainError.Unknown("El encuadre de la foto no es válido."))
        return repository.publicar(publicacion.copy(descripcion = publicacion.descripcion.trim()))
    }
}

class ModificarDescripcionPublicacionUseCase(private val repository: PublicacionRepository) {
    suspend operator fun invoke(id: String, descripcion: String): AppResult<Unit> {
        if (id.isBlank() || descripcion.trim().length > 300)
            return AppResult.Error(DomainError.Unknown("La descripción admite hasta 300 caracteres."))
        return repository.modificarDescripcion(id, descripcion.trim())
    }
}

class EliminarPublicacionUseCase(private val repository: PublicacionRepository) {
    suspend operator fun invoke(id: String): AppResult<Unit> = if (id.isBlank())
        AppResult.Error(DomainError.Unknown("Publicación inválida.")) else repository.eliminar(id)
}
