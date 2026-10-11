package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import kotlinx.coroutines.flow.Flow

interface TurnoRepository {
    fun observeTurnos(comoCliente: Boolean = false): Flow<AppResult<List<Turno>>>
    suspend fun marcarRealizado(turnoId: String, precioEsperadoCentavos: Long): AppResult<Unit>
}
