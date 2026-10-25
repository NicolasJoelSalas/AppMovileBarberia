package ar.edu.unaj.am.myapplication.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Cada pantalla es una "key" tipada: un objeto de Kotlin con sus argumentos como propiedades.
// El back stack es literalmente una lista de estas keys, por ejemplo [BookListDestination, BookDetailDestination(1342)].
// @Serializable permite guardar esa lista al rotar la pantalla o si el sistema mata el proceso.

@Serializable
data object HomeDestination : NavKey

@Serializable
data object PublicacionesDestination : NavKey

@Serializable
data object MiCuentaDestination : NavKey

@Serializable
data object UbicacionBarberiaDestination : NavKey

@Serializable
data object ServiciosDestination : NavKey

@Serializable
data class ComoLlegarDestination(val barberoId: String) : NavKey

@Serializable
data object TurnosListDestination : NavKey

@Serializable
data object AgendaSemanalDestination : NavKey

@Serializable
data class ReservarTurnoDestination(val barberoId: String? = null, val turnoId: String? = null, val servicioId: String? = null, val inicioMillis: Long? = null, val referenciaCorteUrl: String? = null) : NavKey
