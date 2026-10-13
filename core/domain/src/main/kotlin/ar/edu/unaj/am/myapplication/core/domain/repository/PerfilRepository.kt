package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.PerfilUsuario

interface PerfilRepository {
    suspend fun obtenerMiPerfil(): AppResult<PerfilUsuario>
    suspend fun actualizarMiNombre(nombre: String): AppResult<Unit>
}
