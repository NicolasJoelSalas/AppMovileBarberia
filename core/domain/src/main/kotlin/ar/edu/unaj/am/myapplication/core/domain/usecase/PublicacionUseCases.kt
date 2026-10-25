package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PublicacionRepository

class ObservarPublicacionesUseCase(private val repository: PublicacionRepository) {
    operator fun invoke() = repository.observar()
}

class PublicarTrabajoUseCase(private val repository: PublicacionRepository) {
    suspend operator fun invoke(publicacion: NuevaPublicacion): AppResult<Unit> {
        if (publicacion.solicitudId.isBlank() || publicacion.imagenLocal.isBlank())
            return AppResult.Error(DomainError.Known(ErrorReason.PHOTO_REQUIRED))
        if (publicacion.descripcion.trim().length > 300)
            return AppResult.Error(DomainError.Known(ErrorReason.DESCRIPTION_LENGTH))
        if (!publicacion.recorteX.isFinite() || !publicacion.recorteY.isFinite() ||
            publicacion.recorteX !in -1f..1f || publicacion.recorteY !in -1f..1f)
            return AppResult.Error(DomainError.Known(ErrorReason.PHOTO_CROP))
        return repository.publicar(publicacion.copy(descripcion = publicacion.descripcion.trim()))
    }
}

class ModificarDescripcionPublicacionUseCase(private val repository: PublicacionRepository) {
    suspend operator fun invoke(id: String, descripcion: String): AppResult<Unit> {
        if (id.isBlank() || descripcion.trim().length > 300)
            return AppResult.Error(DomainError.Known(ErrorReason.DESCRIPTION_LENGTH))
        return repository.modificarDescripcion(id, descripcion.trim())
    }
}

class EliminarPublicacionUseCase(private val repository: PublicacionRepository) {
    suspend operator fun invoke(id: String): AppResult<Unit> = if (id.isBlank())
        AppResult.Error(DomainError.Known(ErrorReason.POST_INVALID)) else repository.eliminar(id)
}
