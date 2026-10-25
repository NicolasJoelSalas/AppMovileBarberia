package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.PerfilRepository

class ObtenerMiPerfilUseCase(private val repository: PerfilRepository) {
    suspend operator fun invoke() = repository.obtenerMiPerfil()
}

class ActualizarMiNombreUseCase(private val repository: PerfilRepository) {
    suspend operator fun invoke(nombre: String): AppResult<Unit> {
        val normalizado = nombre.trim()
        if (normalizado.isEmpty() || normalizado.length > 120) {
            return AppResult.Error(DomainError.Known(ErrorReason.NAME_LENGTH))
        }
        return repository.actualizarMiNombre(normalizado)
    }
}
