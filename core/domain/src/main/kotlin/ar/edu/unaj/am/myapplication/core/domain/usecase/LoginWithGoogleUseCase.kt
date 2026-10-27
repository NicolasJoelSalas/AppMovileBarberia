package ar.edu.unaj.am.myapplication.core.domain.usecase

import javax.inject.Inject

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository

class LoginWithGoogleUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(idToken: String): AppResult<Unit> =
        if (idToken.isBlank()) {
            AppResult.Error(DomainError.Known(ErrorReason.GOOGLE_ACCOUNT))
        } else {
            repository.loginWithGoogle(idToken)
        }
}
