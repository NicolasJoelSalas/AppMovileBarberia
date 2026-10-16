package ar.edu.unaj.am.myapplication.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import ar.edu.unaj.am.myapplication.R

/**
 * Los tabs de la bottom bar. Cada uno tiene su propio back stack, que arranca en [startKey].
 * Agregar un tab nuevo = agregar una entrada acá y sus pantallas en AppNavDisplay.
 */
enum class TopLevelDestination(
    val startKey: NavKey,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val label: Int
) {
    HOME(
        startKey = HomeDestination,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        label = R.string.tab_home
    ),
    TURNOS(
        startKey = TurnosListDestination,
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Outlined.DateRange,
        label = R.string.tab_turnos
    ),
    AGENDA(
        startKey = AgendaSemanalDestination,
        selectedIcon = Icons.AutoMirrored.Filled.EventNote,
        unselectedIcon = Icons.AutoMirrored.Outlined.EventNote,
        label = R.string.tab_agenda
    ),
    MI_CUENTA(
        startKey = MiCuentaDestination,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        label = R.string.tab_mi_cuenta
    )
}
