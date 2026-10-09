package ar.edu.unaj.am.myapplication.features.auth.detail

import ar.edu.unaj.am.myapplication.core.domain.model.DomainError

data class LoginUiState(
    val isLoading: Boolean = false,
    val isRegistering: Boolean = false,
    val isAuthenticated: Boolean = false,
    val needsProfileRetry: Boolean = false,
    val error: DomainError? = null
)
