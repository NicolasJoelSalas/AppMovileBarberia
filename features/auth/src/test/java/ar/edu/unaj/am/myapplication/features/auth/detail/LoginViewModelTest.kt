package ar.edu.unaj.am.myapplication.features.auth.detail

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.ClienteRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.EnsureClienteProfileUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginWithGoogleUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LogoutUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegisterUseCase
import ar.edu.unaj.am.myapplication.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val repository = FakeAuthRepository()
    private var profileResult: AppResult<Unit> = AppResult.Success(Unit)
    private var profileCalls = 0
    private val profiles = object : ClienteRepository {
        override suspend fun ensureCurrentProfile(): AppResult<Unit> {
            profileCalls++
            return profileResult
        }
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(initiallyLoggedIn: Boolean = false) = LoginViewModel(
        LoginUseCase(repository), LoginWithGoogleUseCase(repository), RegisterUseCase(repository),
        EnsureClienteProfileUseCase(profiles), LogoutUseCase(repository), initiallyLoggedIn
    )

    @Test fun `Google success opens authenticated session`() = runTest {
        val vm = viewModel()
        vm.onGoogleLogin { "google-token" }
        assertEquals("google-token", repository.token)
        assertTrue(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `cancelling picker keeps login available without error`() = runTest {
        val vm = viewModel()
        vm.onGoogleLogin { null }
        assertNull(repository.token)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isAuthenticated)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `Firebase rejection leaves user on login`() = runTest {
        repository.result = AppResult.Error(DomainError.Network)
        val vm = viewModel()
        vm.onGoogleLogin { "token" }
        assertEquals(DomainError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `pending Google picker blocks other login requests`() = runTest {
        val picker = CompletableDeferred<String?>()
        val vm = viewModel()
        vm.onGoogleLogin { picker.await() }
        assertTrue(vm.uiState.value.isLoading)
        vm.onGoogleLogin { error("Second picker must not open") }
        vm.onLogin("user@example.com", "password")
        vm.onRegister("user@example.com", "password", "password")
        assertEquals(0, repository.passwordCalls)
        assertEquals(0, repository.registerCalls)
        picker.complete(null)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `picker failure clears loading and permits retry`() = runTest {
        val vm = viewModel()
        vm.onGoogleLogin { throw IllegalStateException("Provider unavailable") }
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
        vm.onGoogleLogin { "retry-token" }
        assertTrue(vm.uiState.value.isAuthenticated)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `registration success opens app`() = runTest {
        val vm = viewModel()
        vm.onToggleRegistration()
        vm.onRegister("user@example.com", "password", "password")
        assertTrue(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, repository.registerCalls)
    }

    @Test fun `duplicate email stays on registration and allows login`() = runTest {
        repository.result = AppResult.Error(DomainError.Unknown("Ya existe una cuenta con ese correo."))
        val vm = viewModel()
        vm.onToggleRegistration()
        vm.onRegister("user@example.com", "password", "password")
        assertFalse(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
        vm.onToggleRegistration()
        assertFalse(vm.uiState.value.isRegistering)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `profile retry does not register the account twice`() = runTest {
        profileResult = AppResult.Error(DomainError.Network)
        val vm = viewModel()
        vm.onRegister("user@example.com", "password", "password")
        assertFalse(vm.uiState.value.isAuthenticated)
        assertTrue(vm.uiState.value.needsProfileRetry)
        assertEquals(1, repository.registerCalls)
        profileResult = AppResult.Success(Unit)
        vm.onRetryProfile()
        assertTrue(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.needsProfileRetry)
        assertEquals(1, repository.registerCalls)
        assertEquals(2, profileCalls)
    }

    @Test fun `restored session completes profile before opening app`() = runTest {
        profileResult = AppResult.Error(DomainError.Network)
        val vm = viewModel(initiallyLoggedIn = true)
        assertFalse(vm.uiState.value.isAuthenticated)
        assertTrue(vm.uiState.value.needsProfileRetry)
        assertEquals(1, profileCalls)
    }

    @Test fun `failed authentication never creates a profile`() = runTest {
        repository.result = AppResult.Error(DomainError.Network)
        val vm = viewModel()
        vm.onLogin("user@example.com", "password")
        assertEquals(0, profileCalls)
    }

    @Test fun `logout returns to login and allows another authenticated session`() = runTest {
        val vm = viewModel(initiallyLoggedIn = true)
        assertTrue(vm.uiState.value.isAuthenticated)
        vm.onLogout()
        vm.onLogout()
        assertEquals(1, repository.logoutCalls)
        assertFalse(vm.uiState.value.isAuthenticated)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.needsProfileRetry)
        vm.onLogin("another@example.com", "password")
        assertTrue(vm.uiState.value.isAuthenticated)
        assertEquals(2, profileCalls)
    }

    private class FakeAuthRepository : AuthRepository {
        var logoutCalls = 0
        var token: String? = null
        var passwordCalls = 0
        var registerCalls = 0
        var result: AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun login(email: String, password: String): AppResult<Unit> {
            passwordCalls++
            return result
        }
        override suspend fun loginWithGoogle(idToken: String): AppResult<Unit> {
            token = idToken
            return result
        }
        override suspend fun register(email: String, password: String): AppResult<Unit> {
            registerCalls++
            return result
        }
        override fun isLoggedIn() = false
        override fun logout() { logoutCalls++ }
    }
}
