package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String
    ): AppResult<Unit> {
        val cleanEmail = email.trim()

        if (cleanEmail.isBlank() || password.isBlank()) {
            return AppResult.Error(
                DomainError.Known(ErrorReason.LOGIN_REQUIRED_FIELDS)
            )
        }

        return repository.login(cleanEmail, password)
    }
}