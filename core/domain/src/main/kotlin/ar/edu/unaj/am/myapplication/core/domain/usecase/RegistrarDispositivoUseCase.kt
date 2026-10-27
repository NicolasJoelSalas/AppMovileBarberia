package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.repository.DispositivoRepository

class RegistrarDispositivoUseCase @Inject constructor(private val repository: DispositivoRepository) {
    suspend operator fun invoke(instalacionId: String, token: String) = repository.registrar(instalacionId, token)
}
