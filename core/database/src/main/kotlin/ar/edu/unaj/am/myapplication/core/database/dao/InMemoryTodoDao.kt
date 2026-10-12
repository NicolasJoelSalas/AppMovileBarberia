package ar.edu.unaj.am.myapplication.core.database.dao

import ar.edu.unaj.am.myapplication.core.database.model.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * "Base de datos" en memoria. Igual que Room, expone un Flow que vuelve a emitir
 * cada vez que la tabla cambia: la UI se actualiza sola, sin volver a pedir los datos.
 */
//NO BORRAR, FUTURA IMPLEMENTACION DEL ALMACENAMIENTO LOCAL
class InMemoryTodoDao : TodoDao {

    private val table = MutableStateFlow<Map<Int, TodoEntity>>(emptyMap())

    override fun observeAll(): Flow<List<TodoEntity>> = table.map { it.values.toList() }

    override suspend fun upsert(todo: TodoEntity) {
        table.update { it + (todo.id to todo) }
    }

    override suspend fun replaceAll(todos: List<TodoEntity>) {
        table.value = todos.associateBy { it.id }
    }

    override suspend fun deleteById(id: Int) {
        table.update { it - id }
    }
}

//NO BORRAR, FUTURA IMPLEMENTACION DEL ALMACENAMIENTO LOCAL