package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository

class RegisterUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmation: String
    ): AppResult<Unit> {
        val cleanEmail = email.trim()
        val message = when {
            !EMAIL_PATTERN.matches(cleanEmail) -> ErrorReason.INVALID_EMAIL
            password.isBlank() || password.length < 6 -> ErrorReason.PASSWORD_LENGTH
            password != confirmation -> ErrorReason.PASSWORD_CONFIRMATION
            else -> null
        }
        return if (message != null) AppResult.Error(DomainError.Known(message))
        else repository.register(cleanEmail, password)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
