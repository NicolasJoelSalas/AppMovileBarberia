package ar.edu.unaj.am.myapplication.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TurnoDao {

    @Query("""
        SELECT * FROM turnos
        WHERE clienteId = :clienteId
        ORDER BY inicioMillis ASC
    """)
    fun observarPorCliente(clienteId: String): Flow<List<TurnosEntity>>

    @Query("""
        SELECT * FROM turnos
        WHERE id = :turnoId AND clienteId = :clienteId
    """)
    suspend fun obtenerPorId(
        turnoId: String,
        clienteId: String
    ): TurnosEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(turno: TurnosEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodos(turnos: List<TurnosEntity>)

    @Query("DELETE FROM turnos WHERE clienteId = :clienteId")
    suspend fun eliminarPorCliente(clienteId: String)

    @Transaction
    suspend fun reemplazarPorCliente(
        clienteId: String,
        turnos: List<TurnosEntity>
    ) {
        require(turnos.all { it.clienteId == clienteId })

        eliminarPorCliente(clienteId)
        guardarTodos(turnos)
    }
}