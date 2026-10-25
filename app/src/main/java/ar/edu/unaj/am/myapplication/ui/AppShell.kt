package ar.edu.unaj.am.myapplication.ui

import ar.edu.unaj.am.myapplication.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.reminders.rememberTurnoReminderControl
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.unaj.am.myapplication.features.turnos.list.ReservasViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            container.reservarTurno)
    }
    val reservasState by reservasViewModel.uiState.collectAsStateWithLifecycle()
    val esBarbero = reservasState.catalogo?.esBarbero == true
    var turnosParaAvisos by remember { mutableStateOf<List<Turno>?>(null) }
    var turnosPendientes by remember { mutableStateOf(0) }
    LaunchedEffect(reservasState.catalogo?.esBarbero) {
        turnosParaAvisos = null
        turnosPendientes = 0
        val rolBarbero = reservasState.catalogo?.esBarbero
        if (rolBarbero != null) {
            container.observeTurnos(comoCliente = !rolBarbero).collect { result ->
                when (result) {
                    is AppResult.Success -> {
                        if (rolBarbero) {
                            turnosPendientes = result.data.count {
                                it.estado.lowercase() in listOf("confirmado", "pendiente")
                            }
                        } else {
                            turnosParaAvisos = result.data
                        }
                    }
                    // Una falla de sincronización no invalida los turnos ya leídos de Room.
                    is AppResult.Error -> Unit
                }
            }
        }
    }
    val recordatorios = rememberTurnoReminderControl(turnosParaAvisos)
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
                    turnosPendientes = turnosPendientes,
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
            onRecordatorio = recordatorios.onClick,
            recordatoriosActivos = recordatorios.activeIds,
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
    turnosPendientes: Int,
    onTabClick: (TopLevelDestination) -> Unit
) {
    val pendingDescription = androidx.compose.ui.res.pluralStringResource(R.plurals.app_pending_appointments, turnosPendientes, turnosPendientes)
    NavigationBar {
        TopLevelDestination.entries.filter {
            esBarbero || (it != TopLevelDestination.AGENDA && it != TopLevelDestination.PUBLICACIONES)
        }.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = {
                    BadgedBox(badge = {
                        if (esBarbero && tab == TopLevelDestination.TURNOS && turnosPendientes > 0) {
                            Badge {
                                Text(
                                    turnosPendientes.toString(),
                                    modifier = Modifier.semantics {
                                        contentDescription = pendingDescription
                                    }
                                )
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = null
                        )
                    }
                },
                label = {
                    Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}
