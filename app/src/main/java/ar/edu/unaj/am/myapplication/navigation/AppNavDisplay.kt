package ar.edu.unaj.am.myapplication.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.features.home.HomeRoute
import ar.edu.unaj.am.myapplication.features.home.HomeViewModel
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ar.edu.unaj.am.myapplication.di.AppContainer
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
    container: AppContainer,
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
            val cuentaViewModel = viewModel {
                MiCuentaViewModel(container.obtenerMiPerfil, container.actualizarMiNombre)
            }
            MiCuentaRoute(cuentaViewModel, onPerfilGuardado = reservasViewModel::cargar,
                onLogout = onLogout,
                onUbicacionBarberia = { navigationState.navigate(UbicacionBarberiaDestination) })
        }
        entry<UbicacionBarberiaDestination> { key ->
            if (esBarbero) {
                val vm = viewModel {
                    ar.edu.unaj.am.myapplication.features.cuenta.ubicacion.UbicacionBarberiaViewModel(
                        container.obtenerMiUbicacion, container.guardarMiUbicacion)
                }
                ar.edu.unaj.am.myapplication.features.cuenta.ubicacion.UbicacionBarberiaRoute(vm,
                    onBack = { navigationState.goBackFrom(key) })
            }
        }
        entry<ComoLlegarDestination> { key ->
            val vm = viewModel {
                ar.edu.unaj.am.myapplication.features.turnos.ubicacion.ComoLlegarViewModel(key.barberoId,
                    container.obtenerUbicacionBarberia, container.calcularRutaBarberia)
            }
            ar.edu.unaj.am.myapplication.features.turnos.ubicacion.ComoLlegarRoute(vm,
                onBack = { navigationState.goBackFrom(key) })
        }
        entry<HomeDestination> {
            if (esBarbero) {
                val reporteViewModel = viewModel {
                    ar.edu.unaj.am.myapplication.features.cuenta.ReporteGananciasViewModel(container.observeTurnos)
                }
                ar.edu.unaj.am.myapplication.features.cuenta.ReporteGananciasRoute(reporteViewModel)
            } else {
                PublicacionesRoute(container, navigationState, esBarbero, onEditorTrabajoVisible)
            }
        }
        entry<PublicacionesDestination> {
            if (esBarbero) {
                PublicacionesRoute(container, navigationState, esBarbero, onEditorTrabajoVisible)
            }
        }


        entry<TurnosListDestination> {
            ReservasRoute(
                reservasViewModel,
                turnosCliente = {
                    val misTurnosViewModel = viewModel(key = "mis-turnos-cliente") {
                        ar.edu.unaj.am.myapplication.features.turnos.list.TurnoListViewModel(
                            container.observeTurnos, comoCliente = true, cancelarTurno = container.cancelarTurno
                        )
                    }
                    TurnosListRoute(
                        misTurnosViewModel,
                        onComoLlegar = { turno -> navigationState.navigate(ComoLlegarDestination(turno.barberoId)) },
                        onModificar = { turno -> navigationState.navigate(ReservarTurnoDestination(
                            turno.barberoId, turno.id, turno.servicioId, turno.inicioMillis)) },
                        onRecordatorio = onRecordatorio,
                        recordatoriosActivos = recordatoriosActivos,
                        onReservar = { navigationState.navigate(ReservarTurnoDestination()) })
                }) {
                val agendaViewModel = viewModel {
                    ar.edu.unaj.am.myapplication.features.turnos.list.TurnoListViewModel(container.observeTurnos,
                        completarTurno = container.marcarTurnoRealizado)
                }
                TurnosListRoute(viewModel = agendaViewModel)
            }
        }

        entry<ReservarTurnoDestination> { key ->
            val reservaViewModel = viewModel {
                ReservasViewModel(
                    container.cargarCatalogoReservas, container.consultarSemana,
                    container.reservarTurno, container.modificarTurno
                )
            }
            LaunchedEffect(key) {
                if (key.turnoId != null && key.barberoId != null && key.servicioId != null && key.inicioMillis != null)
                    reservaViewModel.editar(key.turnoId, key.barberoId, key.servicioId, key.inicioMillis)
                else key.barberoId?.let(reservaViewModel::barbero)
            }
            ReservarTurnoRoute(reservaViewModel, onBack = { navigationState.goBackFrom(key) })
        }

        entry<AgendaSemanalDestination> { key ->
            if (esBarbero) {
                val agendaViewModel = viewModel {
                    AgendaViewModel(container.obtenerMiAgenda, container.guardarYPublicarAgenda)
                }
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
    container: AppContainer,
    navigationState: NavigationState,
    esBarbero: Boolean,
    onEditorTrabajoVisible: (Boolean) -> Unit
) {
    val homeViewModel = viewModel {
        HomeViewModel(
            container.observarPublicaciones, container.publicarTrabajo,
            container.eliminarPublicacion, container.modificarDescripcionPublicacion, createSavedStateHandle()
        )
    }
    HomeRoute(
        homeViewModel, esBarbero = esBarbero,
        onReservar = { navigationState.navigate(ReservarTurnoDestination(it)) },
        onEditorVisible = onEditorTrabajoVisible
    )
}
