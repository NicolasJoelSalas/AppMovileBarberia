package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import kotlinx.coroutines.flow.Flow

/** Solo emite lecturas confirmadas por el servidor, nunca una caché parcial. */
interface NovedadesTurnosRepository {
    suspend fun consultar(uid: String, comoCliente: Boolean = false): List<Turno>
    fun observar(uid: String, comoCliente: Boolean = false): Flow<List<Turno>>
}
