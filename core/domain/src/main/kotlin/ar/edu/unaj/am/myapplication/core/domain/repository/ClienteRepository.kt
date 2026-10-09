package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult

interface ClienteRepository {
    /** Crea el perfil de la sesión actual si falta, salvo que sea una cuenta de barbero. */
    suspend fun ensureCurrentProfile(): AppResult<Unit>
}
