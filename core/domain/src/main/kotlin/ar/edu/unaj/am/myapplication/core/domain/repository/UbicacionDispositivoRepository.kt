package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.PosicionDispositivo
import kotlinx.coroutines.flow.Flow

interface UbicacionDispositivoRepository {
    fun habilitada(): Boolean
    suspend fun obtener(preciso: Boolean): PosicionDispositivo?
    fun observar(): Flow<PosicionDispositivo>
}
