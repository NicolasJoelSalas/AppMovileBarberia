package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.repository.DispositivoRepository

class DesvincularDispositivoUseCase @Inject constructor(private val repository: DispositivoRepository) {
    suspend operator fun invoke(instalacionId: String) = repository.desvincular(instalacionId)
}
