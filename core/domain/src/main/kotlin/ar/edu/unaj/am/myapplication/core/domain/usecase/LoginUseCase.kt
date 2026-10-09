package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository

class LoginUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String
    ): AppResult<Unit> {
        val cleanEmail = email.trim()

        if (cleanEmail.isBlank() || password.isBlank()) {
            return AppResult.Error(
                DomainError.Unknown("Ingresá el correo y la contraseña")
            )
        }

        return repository.login(cleanEmail, password)
    }
}