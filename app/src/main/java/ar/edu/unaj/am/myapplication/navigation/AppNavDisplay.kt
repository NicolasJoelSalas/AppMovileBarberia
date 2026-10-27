package ar.edu.unaj.am.myapplication.navigation

import androidx.compose.runtime.Composable
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnoListViewModel
import ar.edu.unaj.am.myapplication.features.cuenta.ReporteGananciasViewModel
import ar.edu.unaj.am.myapplication.features.turnos.ubicacion.ComoLlegarViewModel
import ar.edu.unaj.am.myapplication.features.cuenta.ubicacion.UbicacionBarberiaViewModel
import ar.edu.unaj.am.myapplication.features.cuenta.servicios.ServiciosViewModel
import androidx.compose.runtime.LaunchedEffect
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.features.home.HomeRoute
import ar.edu.unaj.am.myapplication.features.home.HomeViewModel
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ar.edu.unaj.am.myapplication.features.turnos.list.TurnosListRoute
import ar.edu.unaj.am.myapplication.features.turnos.list.ReservasRoute
import ar.edu.unaj.am.myapplication.features.turnos.list.ReservarTurnoRoute
import ar.edu.unaj.am.myapplication.features.turnos.list.ReservasViewModel
import ar.edu.unaj.am.myapplication.features.turnos.agenda.AgendaRoute
import ar.edu.unaj.am.myapplication.features.turnos.agenda.AgendaViewModel
import ar.edu.unaj.am.myapplication.features.cuenta.MiCuentaRoute
import ar.edu.unaj.am.myapplication.features.cuenta.MiCuentaViewModel

/**
 * La navegación vive en :app. Las features no se conocen entre sí:
 * solo exponen callbacks (onTodoClick, onBack…) y acá decidimos qué hace cada uno.
 */
@Composable
fun AppNavDisplay(
    navigationState: NavigationState,
    reservasViewModel: ReservasViewModel,
    esBarbero: Boolean,
    onEditorTrabajoVisible: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onRecordatorio: (Turno) -> Unit,
    recordatoriosActivos: Set<String>,
    modifier: Modifier = Modifier
) {
    // entryProvider: "para esta key, mostra esta pantalla". La key llega ya tipada:
    // key.todoId es un Int, sin getInt("todoId") ni strings de por medio.
    val entryProvider = entryProvider<NavKey> {
        entry<MiCuentaDestination> {
            val cuentaViewModel: MiCuentaViewModel = hiltViewModel()
            MiCuentaRoute(cuentaViewModel, onPerfilGuardado = reservasViewModel::cargar,
                onLogout = onLogout,
                onUbicacionBarberia = { navigationState.navigate(UbicacionBarberiaDestination) },
                onServicios = { navigationState.navigate(ServiciosDestination) })
        }
        entry<ServiciosDestination> { key ->
            if (esBarbero) {
                val vm: ServiciosViewModel = hiltViewModel()
                ar.edu.unaj.am.myapplication.features.cuenta.servicios.ServiciosRoute(vm,
                    onBack = { navigationState.goBackFrom(key) })
            }
        }
        entry<UbicacionBarberiaDestination> { key ->
            if (esBarbero) {
                val vm: UbicacionBarberiaViewModel = hiltViewModel()
                ar.edu.unaj.am.myapplication.features.cuenta.ubicacion.UbicacionBarberiaRoute(vm,
                    onBack = { navigationState.goBackFrom(key) })
            }
        }
        entry<ComoLlegarDestination> { key ->
            val vm = hiltViewModel<ComoLlegarViewModel,
                ComoLlegarViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.barberoId) })
            ar.edu.unaj.am.myapplication.features.turnos.ubicacion.ComoLlegarRoute(vm,
                onBack = { navigationState.goBackFrom(key) })
        }
        entry<HomeDestination> {
            if (esBarbero) {
                val reporteViewModel: ReporteGananciasViewModel = hiltViewModel()
                ar.edu.unaj.am.myapplication.features.cuenta.ReporteGananciasRoute(reporteViewModel)
            } else {
                PublicacionesRoute(navigationState, esBarbero, onEditorTrabajoVisible)
            }
        }
        entry<PublicacionesDestination> {
            if (esBarbero) {
                PublicacionesRoute(navigationState, esBarbero, onEditorTrabajoVisible)
            }
        }


        entry<TurnosListDestination> {
            ReservasRoute(
                reservasViewModel,
                turnosCliente = {
                    val misTurnosViewModel = hiltViewModel<TurnoListViewModel,
                        TurnoListViewModel.Factory>(
                        key = "mis-turnos-cliente", creationCallback = { factory -> factory.create(true) })
                    TurnosListRoute(
                        misTurnosViewModel,
                        onComoLlegar = { turno -> navigationState.navigate(ComoLlegarDestination(turno.barberoId)) },
                        onModificar = { turno -> navigationState.navigate(ReservarTurnoDestination(
                            turno.barberoId, turno.id, turno.servicioId, turno.inicioMillis, turno.referenciaCorteUrl)) },
                        onRecordatorio = onRecordatorio,
                        recordatoriosActivos = recordatoriosActivos,
                        onReservar = { navigationState.navigate(ReservarTurnoDestination()) })
                }) {
                val agendaViewModel = hiltViewModel<TurnoListViewModel,
                    TurnoListViewModel.Factory>(
                    creationCallback = { factory -> factory.create(false) })
                TurnosListRoute(viewModel = agendaViewModel)
            }
        }

        entry<ReservarTurnoDestination> { key ->
            val reservaViewModel: ReservasViewModel = hiltViewModel()
            LaunchedEffect(key) {
                if (key.turnoId != null && key.barberoId != null && key.servicioId != null && key.inicioMillis != null)
                    reservaViewModel.editar(key.turnoId, key.barberoId, key.servicioId, key.inicioMillis, key.referenciaCorteUrl)
                else key.barberoId?.let(reservaViewModel::barbero)
            }
            ReservarTurnoRoute(reservaViewModel, onBack = { navigationState.goBackFrom(key) })
        }

        entry<AgendaSemanalDestination> { key ->
            if (esBarbero) {
                val agendaViewModel: AgendaViewModel = hiltViewModel()
                AgendaRoute(agendaViewModel, onBack = { navigationState.goBackFrom(key) })
            } else {
                ar.edu.unaj.am.myapplication.core.ui.components.LoadingContent(message = "Verificando acceso a tu agenda…")
            }
        }
    }

    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        onBack = navigationState::goBack,
        modifier = modifier
    )
}

/**
 * Convierte cada back stack en entradas "decoradas" y junta las de los tabs visibles.
 *
 * Decoramos TODOS los back stacks, aunque no se vean: así, al cambiar de tab,
 * las pantallas del otro tab conservan su ViewModel y su estado (scroll, formularios…).
 */
@Composable
private fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): List<NavEntry<NavKey>> {
    val entriesByTab = backStacks.mapValues { (_, backStack) ->
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                // Guarda el estado de rememberSaveable de cada pantalla
                rememberSaveableStateHolderNavEntryDecorator(),
                // Le da a cada pantalla su propio ViewModelStore
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider
        )
    }
    return visibleTabs.flatMap { tab -> entriesByTab.getValue(tab) }
}

@Composable
private fun PublicacionesRoute(
    navigationState: NavigationState,
    esBarbero: Boolean,
    onEditorTrabajoVisible: (Boolean) -> Unit
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    HomeRoute(
        homeViewModel, esBarbero = esBarbero,
        onReservar = { navigationState.navigate(ReservarTurnoDestination(it)) },
        onEditorVisible = onEditorTrabajoVisible
    )
}
