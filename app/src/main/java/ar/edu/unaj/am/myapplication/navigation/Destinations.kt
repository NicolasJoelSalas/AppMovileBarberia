package ar.edu.unaj.am.myapplication.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Cada pantalla es una "key" tipada: un objeto de Kotlin con sus argumentos como propiedades.
// El back stack es literalmente una lista de estas keys, por ejemplo [BookListDestination, BookDetailDestination(1342)].
// @Serializable permite guardar esa lista al rotar la pantalla o si el sistema mata el proceso.

@Serializable
data object TodoListDestination : NavKey

@Serializable
data class TodoDetailDestination(val todoId: Int) : NavKey

@Serializable
data object BookListDestination : NavKey

@Serializable
data class BookDetailDestination(val bookId: Int) : NavKey

@Serializable
data object PlaygroundDestination : NavKey

@Serializable
data object TurnosListDestination : NavKey
