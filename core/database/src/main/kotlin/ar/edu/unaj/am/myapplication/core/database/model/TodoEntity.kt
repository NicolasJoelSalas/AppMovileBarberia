package ar.edu.unaj.am.myapplication.core.database.model

/**
 * Cómo se guarda una tarea en la base local.
 * Con Room sería: @Entity(tableName = "todos") data class TodoEntity(@PrimaryKey val id: Int, …)
 */
data class TodoEntity(
    val id: Int,
    val title: String,
    val description: String,
    val isCompleted: Boolean
)
//NO BORRAR, FUTURA IMPLEMENTACION DEL ALMACENAMIENTO LOCAL