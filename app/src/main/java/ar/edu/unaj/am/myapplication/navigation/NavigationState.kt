package ar.edu.unaj.am.myapplication.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Todo el estado de navegación de la app: qué tab está activo y el back stack de cada uno.
 *
 * Con Navigation 3 no hay NavController: navegar es modificar una lista.
 * Como los back stacks son estado observable, Compose recompone la UI cuando cambian,
 * igual que con cualquier mutableStateOf.
 */
class NavigationState(
    private val startTab: TopLevelDestination,
    currentTabState: MutableState<TopLevelDestination>,
    val backStacks: Map<TopLevelDestination, NavBackStack<NavKey>>
) {
    var currentTab: TopLevelDestination by currentTabState
        private set

    private val currentStack: NavBackStack<NavKey>
        get() = backStacks.getValue(currentTab)

    /** La pantalla que se ve ahora. */
    val currentKey: NavKey
        get() = currentStack.last()

    /** true si estamos en la pantalla principal de un tab (ahí se muestra la bottom bar). */
    val isOnTabRoot: Boolean
        get() = currentStack.size == 1

    /**
     * Back stacks que se muestran, uno detrás del otro. El tab inicial siempre queda "abajo":
     * desde Libros, "atrás" vuelve a Tareas antes de salir de la app (así funcionan las apps de Google).
     */
    val visibleTabs: List<TopLevelDestination>
        get() = listOf(startTab, currentTab).distinct()

    fun navigate(key: NavKey) {
        currentStack.add(key)
    }

    fun goBack() {
        when {
            currentStack.size > 1 -> currentStack.removeAt(currentStack.lastIndex)
            currentTab != startTab -> currentTab = startTab
        }
    }

    /**
     * "Atrás" pedido por una pantalla concreta: solo actúa si esa pantalla sigue arriba.
     * Si el usuario toca la flecha dos veces rápido, el segundo toque no hace nada.
     */
    fun goBackFrom(key: NavKey) {
        if (currentKey == key) goBack()
    }

    fun selectTab(tab: TopLevelDestination) {
        if (tab == currentTab) {
            // Tocar el tab actual vuelve a su pantalla principal
            val stack = backStacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            currentTab = tab
        }
    }
}

@Composable
fun rememberNavigationState(
    startTab: TopLevelDestination = TopLevelDestination.HOME
): NavigationState {
    // rememberSaveable / rememberNavBackStack: el tab actual y cada back stack sobreviven a la rotación.
    val currentTab = rememberSaveable { mutableStateOf(startTab) }
    val backStacks = TopLevelDestination.entries.associateWith { tab -> rememberNavBackStack(tab.startKey) }
    return remember(backStacks) { NavigationState(startTab, currentTab, backStacks) }
}
