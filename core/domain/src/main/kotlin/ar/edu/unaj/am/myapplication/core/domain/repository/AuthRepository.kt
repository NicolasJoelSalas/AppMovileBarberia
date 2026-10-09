package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult

interface AuthRepository {
    suspend fun login(
        email: String,
        password: String
    ): AppResult<Unit>

    suspend fun loginWithGoogle(idToken: String): AppResult<Unit>

    suspend fun register(email: String, password: String): AppResult<Unit>

    fun isLoggedIn(): Boolean

    fun logout()
}
