package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.repository.DispositivoRepository

class RegistrarDispositivoUseCase(private val repository: DispositivoRepository) {
    suspend operator fun invoke(instalacionId: String, token: String) = repository.registrar(instalacionId, token)
}
