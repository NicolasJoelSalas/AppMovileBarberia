package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.repository.DispositivoRepository

class DesvincularDispositivoUseCase(private val repository: DispositivoRepository) {
    suspend operator fun invoke(instalacionId: String) = repository.desvincular(instalacionId)
}
