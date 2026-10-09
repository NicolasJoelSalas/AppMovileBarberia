package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmation: String
    ): AppResult<Unit> {
        val cleanEmail = email.trim()
        val message = when {
            !EMAIL_PATTERN.matches(cleanEmail) -> "Ingresá un correo electrónico válido."
            password.isBlank() || password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
            password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
        return if (message != null) AppResult.Error(DomainError.Unknown(message))
        else repository.register(cleanEmail, password)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
