package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.repository.ClienteRepository

class EnsureClienteProfileUseCase(private val repository: ClienteRepository) {
    suspend operator fun invoke() = repository.ensureCurrentProfile()
}
