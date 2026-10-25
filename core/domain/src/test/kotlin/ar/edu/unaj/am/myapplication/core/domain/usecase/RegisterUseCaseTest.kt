package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RegisterUseCaseTest {
    private val repository = FakeRepository()
    private val register = RegisterUseCase(repository)

    @Test fun `invalid inputs never create a user`() = runTest {
        assertEquals(AppResult.Error(DomainError.Known(ErrorReason.INVALID_EMAIL)),
            register("invalid", "password", "password"))
        assertEquals(AppResult.Error(DomainError.Known(ErrorReason.PASSWORD_LENGTH)),
            register("a@example.com", "short", "short"))
        assertEquals(AppResult.Error(DomainError.Known(ErrorReason.PASSWORD_CONFIRMATION)),
            register("a@example.com", "password", "different"))
        assertTrue(register("a@example.com", "      ", "      ") is AppResult.Error)
        assertEquals(0, repository.calls)
    }

    @Test fun `email is trimmed and password preserved`() = runTest {
        assertTrue(register(" a@example.com ", " password ", " password ") is AppResult.Success)
        assertEquals("a@example.com", repository.email)
        assertEquals(" password ", repository.password)
    }

    @Test fun `server duplicate rejection is returned to caller`() = runTest {
        val duplicate = AppResult.Error(DomainError.Unknown("Cuenta existente"))
        repository.result = duplicate
        assertEquals(duplicate, register("a@example.com", "password", "password"))
        assertEquals(1, repository.calls)
    }

    private class FakeRepository : AuthRepository {
        var calls = 0
        var email = ""
        var password = ""
        var result: AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun register(email: String, password: String): AppResult<Unit> {
            calls++
            this.email = email
            this.password = password
            return result
        }
        override suspend fun login(email: String, password: String) = AppResult.Success(Unit)
        override suspend fun loginWithGoogle(idToken: String) = AppResult.Success(Unit)
        override fun isLoggedIn() = false
        override fun logout() = Unit
    }
}
