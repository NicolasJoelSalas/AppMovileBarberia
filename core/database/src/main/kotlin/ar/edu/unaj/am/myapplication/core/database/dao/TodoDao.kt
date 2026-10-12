package ar.edu.unaj.am.myapplication.core.database.dao

import ar.edu.unaj.am.myapplication.core.database.model.TodoEntity
import kotlinx.coroutines.flow.Flow

//NO BORRAR, FUTURA IMPLEMENTACION DEL ALMACENAMIENTO LOCAL
/**
 * Contrato de acceso a la base local. Con Room sería un @Dao con
 * @Query("SELECT * FROM todos") fun observeAll(): Flow<List<TodoEntity>>
 */
interface TodoDao {
    fun observeAll(): Flow<List<TodoEntity>>
    suspend fun upsert(todo: TodoEntity)
    suspend fun replaceAll(todos: List<TodoEntity>)
    suspend fun deleteById(id: Int)
}

//NO BORRAR, FUTURA IMPLEMENTACION DEL ALMACENAMIENTO LOCAL