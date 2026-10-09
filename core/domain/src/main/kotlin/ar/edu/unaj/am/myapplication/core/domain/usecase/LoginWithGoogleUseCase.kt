package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository

class LoginWithGoogleUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(idToken: String): AppResult<Unit> =
        if (idToken.isBlank()) {
            AppResult.Error(DomainError.Unknown("No se pudo obtener la cuenta de Google."))
        } else {
            repository.loginWithGoogle(idToken)
        }
}
