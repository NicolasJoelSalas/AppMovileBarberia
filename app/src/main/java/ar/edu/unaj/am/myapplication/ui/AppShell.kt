package ar.edu.unaj.am.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.unaj.am.myapplication.features.turnos.list.ReservasViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ar.edu.unaj.am.myapplication.di.AppContainer
import ar.edu.unaj.am.myapplication.navigation.AppNavDisplay
import ar.edu.unaj.am.myapplication.navigation.NavigationState
import ar.edu.unaj.am.myapplication.navigation.TopLevelDestination
import ar.edu.unaj.am.myapplication.navigation.rememberNavigationState

/** Estructura general de la app: bottom bar + NavDisplay. Cada pantalla tiene su propio Scaffold adentro. */
@Composable
fun AppShell(
    container: AppContainer,
    onLogout: () -> Unit,
    navigationState: NavigationState = rememberNavigationState()
) {
    val reservasViewModel = viewModel(key = "catalogo-sesion") {
        ReservasViewModel(container.cargarCatalogoReservas, container.consultarSemana,
            container.reservarTurno, container.publicarSemana)
    }
    val reservasState by reservasViewModel.uiState.collectAsStateWithLifecycle()
    val esBarbero = reservasState.catalogo?.esBarbero == true
    var editorTrabajoVisible by remember { mutableStateOf(false) }
    Scaffold(
        // Los insets (status bar, barra de gestos) los maneja el Scaffold de cada pantalla.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Leer navigationState acá es leer estado: cuando cambia el back stack, esto se recompone solo.
            // La bottom bar solo se muestra en la pantalla principal de cada tab, no en los detalles.
            AnimatedVisibility(
                visible = navigationState.isOnTabRoot && !editorTrabajoVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                AppBottomBar(
                    currentTab = navigationState.currentTab,
                    esBarbero = esBarbero,
                    onTabClick = navigationState::selectTab
                )
            }
        }
    ) { innerPadding ->
        AppNavDisplay(
            navigationState = navigationState,
            container = container,
            reservasViewModel = reservasViewModel,
            esBarbero = esBarbero,
            onEditorTrabajoVisible = { editorTrabajoVisible = it },
            onLogout = onLogout,
            // consumeWindowInsets: la bottom bar ya ocupa el espacio de la barra de navegación,
            // así las pantallas no vuelven a sumarlo.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}

@Composable
private fun AppBottomBar(
    currentTab: TopLevelDestination,
    esBarbero: Boolean,
    onTabClick: (TopLevelDestination) -> Unit
) {
    NavigationBar {
        TopLevelDestination.entries.filter {
            esBarbero || (it != TopLevelDestination.AGENDA && it != TopLevelDestination.RESUMEN)
        }.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(tab.label)) }
            )
        }
    }
}
