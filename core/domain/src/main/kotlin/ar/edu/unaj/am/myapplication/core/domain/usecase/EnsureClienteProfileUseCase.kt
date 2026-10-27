package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.repository.ClienteRepository

class EnsureClienteProfileUseCase @Inject constructor(private val repository: ClienteRepository) {
    suspend operator fun invoke() = repository.ensureCurrentProfile()
}
