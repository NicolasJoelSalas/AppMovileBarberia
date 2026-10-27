package ar.edu.unaj.am.myapplication.features.auth.detail

import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LogoutUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginWithGoogleUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegisterUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.EnsureClienteProfileUseCase
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel(
    private val login: LoginUseCase,
    private val loginWithGoogle: LoginWithGoogleUseCase,
    private val register: RegisterUseCase,
    private val ensureClienteProfile: EnsureClienteProfileUseCase,
    private val logout: LogoutUseCase,
    initiallyLoggedIn: Boolean
) : ViewModel() {
    @Inject constructor(
        login: LoginUseCase,
        loginWithGoogle: LoginWithGoogleUseCase,
        register: RegisterUseCase,
        ensureClienteProfile: EnsureClienteProfileUseCase,
        logout: LogoutUseCase,
        auth: ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
    ) : this(login, loginWithGoogle, register, ensureClienteProfile, logout, auth.isLoggedIn())


    private val _uiState = MutableStateFlow(
        LoginUiState(isLoading = initiallyLoggedIn)
    )

    val uiState = _uiState.asStateFlow()

    fun onLogout() {
        if (!_uiState.value.isAuthenticated || _uiState.value.isLoading) return
        logout()
        _uiState.value = LoginUiState()
    }

    init {
        if (initiallyLoggedIn) viewModelScope.launch { completeSession() }
    }

    private suspend fun completeSession() {
        when (val result = ensureClienteProfile()) {
            is AppResult.Success -> _uiState.update {
                it.copy(isLoading = false, isAuthenticated = true, needsProfileRetry = false, error = null)
            }
            is AppResult.Error -> _uiState.update {
                it.copy(isLoading = false, isAuthenticated = false, needsProfileRetry = true, error = result.error)
            }
        }
    }

    fun onRetryProfile() {
        if (_uiState.value.isLoading || !_uiState.value.needsProfileRetry) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { completeSession() }
    }

    fun onToggleRegistration() {
        if (_uiState.value.isLoading || _uiState.value.isAuthenticated) return
        _uiState.update { it.copy(isRegistering = !it.isRegistering, error = null) }
    }

    fun onRegister(email: String, password: String, confirmation: String) {
        if (_uiState.value.isLoading || _uiState.value.isAuthenticated) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = register(email, password, confirmation)) {
                is AppResult.Success -> completeSession()
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    fun onGoogleLogin(getIdToken: suspend () -> String?) {
        val current = _uiState.value
        if (current.isLoading || current.isAuthenticated) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val token = getIdToken()
                if (token == null) {
                    // Closing the account picker is a normal cancellation.
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }
                when (val result = loginWithGoogle(token)) {
                    is AppResult.Success -> completeSession()
                    is AppResult.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.error)
                    }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isLoading = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = DomainError.Known(ErrorReason.GOOGLE_OPEN)
                    )
                }
            }
        }
    }

    fun onGoogleConfigurationMissing() {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(error = DomainError.Known(ErrorReason.GOOGLE_UNAVAILABLE))
        }
    }

    fun onLogin(email: String, password: String) {
        val current = _uiState.value

        if (current.isLoading || current.isAuthenticated) return

        _uiState.update {
            it.copy(isLoading = true, error = null)
        }

        viewModelScope.launch {
            when (val result = login(email, password)) {
                is AppResult.Success -> {
                    completeSession()
                }

                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error
                        )
                    }
                }
            }
        }
    }
}
