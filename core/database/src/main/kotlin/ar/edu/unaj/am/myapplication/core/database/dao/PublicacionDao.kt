package ar.edu.unaj.am.myapplication.core.database.dao

import androidx.room3.*
import ar.edu.unaj.am.myapplication.core.database.model.PublicacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicacionDao {
    @Query("SELECT * FROM publicaciones WHERE clienteId = :clienteId ORDER BY creadoEnMillis DESC, id ASC LIMIT 50")
    fun observarPorCliente(clienteId: String): Flow<List<PublicacionEntity>>

    @Query("SELECT * FROM publicaciones WHERE clienteId = :clienteId")
    suspend fun obtenerPorCliente(clienteId: String): List<PublicacionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarTodos(publicaciones: List<PublicacionEntity>)

    @Query("UPDATE publicaciones SET imagenLocalPath = :ruta WHERE clienteId = :clienteId AND id = :id AND imagenUrl = :url")
    suspend fun actualizarImagen(clienteId: String, id: String, url: String, ruta: String)

    @Query("DELETE FROM publicaciones WHERE clienteId = :clienteId")
    suspend fun eliminarPorCliente(clienteId: String)

    @Transaction
    suspend fun reemplazarPorCliente(clienteId: String, publicaciones: List<PublicacionEntity>) {
        require(publicaciones.size <= 50 && publicaciones.all { it.clienteId == clienteId })
        eliminarPorCliente(clienteId)
        guardarTodos(publicaciones)
    }
}
