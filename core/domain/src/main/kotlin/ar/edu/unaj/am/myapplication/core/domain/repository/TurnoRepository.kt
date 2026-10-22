package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import kotlinx.coroutines.flow.Flow

interface TurnoRepository {
    val cacheInfo: Flow<ar.edu.unaj.am.myapplication.core.domain.model.TurnosCacheInfo>
        get() = kotlinx.coroutines.flow.flowOf(ar.edu.unaj.am.myapplication.core.domain.model.TurnosCacheInfo())
    fun observeTurnos(comoCliente: Boolean = false): Flow<AppResult<List<Turno>>>
    suspend fun marcarRealizado(turnoId: String): AppResult<Unit>
}
