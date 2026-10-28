package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.FiltrarTurnosPorSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import ar.edu.unaj.am.myapplication.core.testing.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.CancelarTurnoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.MarcarTurnoRealizadoUseCase
import kotlinx.coroutines.CompletableDeferred

@OptIn(ExperimentalCoroutinesApi::class)
class TurnoListViewModelTest {
    private class Acciones {
        val clock = Clock.fixed(Instant.parse("2026-10-08T13:00:00Z"), ZoneId.of("UTC"))
        val turno = Turno("t1", "cliente", "barbero", "corte", clock.millis(), clock.millis() + 3600000, "confirmado")
        val source = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(turno)))
        val cancelados = mutableListOf<String>()
        val realizados = mutableListOf<String>()
        var resultado: AppResult<Unit> = AppResult.Success(Unit)
        var pendiente: CompletableDeferred<AppResult<Unit>>? = null
        val turnos = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = source
            override suspend fun marcarRealizado(turnoId: String): AppResult<Unit> {
                realizados += turnoId
                return pendiente?.await() ?: resultado
            }
        }
        val reservas = object : ReservaRepository {
            override suspend fun obtenerMiAgenda(semanaOffset: Int): AppResult<AgendaSemanal> = error("No usado")
            override suspend fun guardarMiAgenda(agenda: AgendaSemanal): AppResult<Unit> = error("No usado")
            override suspend fun publicarSemana(agenda: AgendaSemanal): AppResult<Int> = error("No usado")
            override suspend fun reservar(solicitud: SolicitudReserva): AppResult<String> = error("No usado")
            override suspend fun cancelar(turnoId: String): AppResult<Unit> {
                cancelados += turnoId
                return pendiente?.await() ?: resultado
            }
            override suspend fun cargarCatalogo(): AppResult<CatalogoReservas> = error("No usado")
            override suspend fun consultarSemana(barberoId: String, servicioId: String, semanaOffset: Int, turnoId: String?): AppResult<SemanaDisponible> = error("No usado")
        }
        fun vm(cliente: Boolean = false) = TurnoListViewModel(ObserveTurnosUseCase(turnos), cliente,
            FiltrarTurnosPorSemanaUseCase(clock), MarcarTurnoRealizadoUseCase(turnos, clock), CancelarTurnoUseCase(reservas))
        fun estado(vm: TurnoListViewModel) = (vm.uiState.value as TurnosListUiState.Success).turnos.single().estado
    }

    @Test fun `cancelar actualiza listado y recordatorios sin esperar otra emision remota`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm(cliente = true)
        vm.cancelar(acciones.turno)
        assertEquals(listOf("t1"), acciones.cancelados)
        assertEquals("cancelado", acciones.estado(vm))
        assertEquals("cancelado", vm.turnosParaRecordatorios.value!!.single().estado)
        assertNull(vm.completandoId.value)
        assertNull(vm.errorCompletar.value)
    }

    @Test fun `realizar actualiza listado recordatorios y acciones disponibles`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm()
        vm.marcarRealizado(acciones.turno)
        assertEquals(listOf("t1"), acciones.realizados)
        assertEquals("realizado", acciones.estado(vm))
        assertEquals("realizado", vm.turnosParaRecordatorios.value!!.single().estado)
        assertFalse(vm.accionesTurnos(acciones.clock.millis()).getValue("t1").completable)
        assertNull(vm.completandoId.value)
    }

    @Test fun `un fallo al cancelar conserva el turno y permite reintentar`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm()
        acciones.resultado = AppResult.Error(DomainError.Network)
        vm.cancelar(acciones.turno)
        assertEquals("confirmado", acciones.estado(vm))
        assertEquals(DomainError.Network, vm.errorCompletar.value)
        assertNull(vm.completandoId.value)
        acciones.resultado = AppResult.Success(Unit)
        vm.cancelar(acciones.turno)
        assertEquals("cancelado", acciones.estado(vm))
        assertNull(vm.errorCompletar.value)
        assertEquals(2, acciones.cancelados.size)
    }

    @Test fun `un fallo al realizar conserva el turno y permite reintentar`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm()
        acciones.resultado = AppResult.Error(DomainError.Network)
        vm.marcarRealizado(acciones.turno)
        assertEquals("confirmado", acciones.estado(vm))
        assertEquals(DomainError.Network, vm.errorCompletar.value)
        assertNull(vm.completandoId.value)
        acciones.resultado = AppResult.Success(Unit)
        vm.marcarRealizado(acciones.turno)
        assertEquals("realizado", acciones.estado(vm))
        assertNull(vm.errorCompletar.value)
        assertEquals(2, acciones.realizados.size)
    }

    @Test fun `una cancelacion pendiente bloquea doble toque y completar otro turno`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm()
        acciones.pendiente = CompletableDeferred()
        vm.cancelar(acciones.turno)
        vm.cancelar(acciones.turno)
        vm.marcarRealizado(acciones.turno.copy(id = "otro"))
        assertEquals("t1", vm.completandoId.value)
        assertEquals(1, acciones.cancelados.size)
        assertTrue(acciones.realizados.isEmpty())
        acciones.pendiente!!.complete(AppResult.Success(Unit))
        assertNull(vm.completandoId.value)
        assertEquals("cancelado", acciones.estado(vm))
    }

    @Test fun `completar pendiente bloquea doble toque y cancelacion`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm()
        acciones.pendiente = CompletableDeferred()
        vm.marcarRealizado(acciones.turno)
        vm.marcarRealizado(acciones.turno)
        vm.cancelar(acciones.turno.copy(id = "otro"))
        assertEquals("t1", vm.completandoId.value)
        assertEquals(1, acciones.realizados.size)
        assertTrue(acciones.cancelados.isEmpty())
        acciones.pendiente!!.complete(AppResult.Error(DomainError.Network))
        assertNull(vm.completandoId.value)
        assertEquals("confirmado", acciones.estado(vm))
    }

    @Test fun `el cliente no puede realizar turnos aunque reciba el caso de uso`() = runTest {
        val acciones = Acciones()
        val vm = acciones.vm(cliente = true)
        vm.marcarRealizado(acciones.turno)
        assertFalse(vm.puedeCompletar)
        assertTrue(acciones.realizados.isEmpty())
        assertEquals("confirmado", acciones.estado(vm))
    }

    @Test fun `barber keeps cached appointments when synchronization fails`() = runTest {
        val turno = Turno("local", "cliente", "barbero", "corte", Instant.parse("2026-10-08T13:00:00Z").toEpochMilli(), null, "confirmado")
        val source = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(turno)))
        var queriedAsClient = true
        val repo = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = source.also { queriedAsClient = comoCliente }
            override suspend fun marcarRealizado(turnoId: String) = AppResult.Success(Unit)
        }
        val vm = TurnoListViewModel(ObserveTurnosUseCase(repo), comoCliente = false,
            filtrarSemana = FiltrarTurnosPorSemanaUseCase(Clock.fixed(
                Instant.parse("2026-10-08T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))))
        source.value = AppResult.Error(ar.edu.unaj.am.myapplication.core.domain.model.DomainError.Network)
        assertFalse(queriedAsClient)
        assertEquals(listOf(turno), (vm.uiState.value as TurnosListUiState.Success).turnos)
        assertNotNull(vm.avisoSincronizacion.value)
        source.value = AppResult.Success(listOf(turno.copy(estado = "realizado")))
        assertNull(vm.avisoSincronizacion.value)
        assertEquals("realizado", (vm.uiState.value as TurnosListUiState.Success).turnos.single().estado)
    }
    @Test fun `client keeps cached appointments when synchronization fails and clears warning on recovery`() = runTest {
        val turno = Turno("local", "cliente", "barbero", "corte", Instant.parse("2026-10-08T13:00:00Z").toEpochMilli(), null, "confirmado")
        val source = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(turno)))
        val repo = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = source
            override suspend fun marcarRealizado(turnoId: String) = AppResult.Success(Unit)
        }
        val vm = TurnoListViewModel(ObserveTurnosUseCase(repo), comoCliente = true,
            filtrarSemana = FiltrarTurnosPorSemanaUseCase(Clock.fixed(
                Instant.parse("2026-10-08T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))))
        source.value = AppResult.Error(ar.edu.unaj.am.myapplication.core.domain.model.DomainError.Network)
        assertEquals(listOf(turno), (vm.uiState.value as TurnosListUiState.Success).turnos)
        assertNotNull(vm.avisoSincronizacion.value)
        source.value = AppResult.Success(listOf(turno.copy(estado = "cancelado")))
        assertNull(vm.avisoSincronizacion.value)
        assertEquals("cancelado", (vm.uiState.value as TurnosListUiState.Success).turnos.single().estado)
    }
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test fun `switching week preserves client scope and filters live updates`() = runTest {
        fun turno(id: String, date: String) = Turno(id, "cliente", "barbero", "corte",
            Instant.parse(date).toEpochMilli(), null, "confirmado")
        val actual = turno("actual", "2026-10-08T13:00:00Z")
        val proximo = turno("proximo", "2026-10-12T13:00:00Z")
        val source = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(actual, proximo)))
        var consultaCliente = false
        val repo = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = source.also { consultaCliente = comoCliente }
            override suspend fun marcarRealizado(turnoId: String) = AppResult.Success(Unit)
        }
        val vm = TurnoListViewModel(ObserveTurnosUseCase(repo), comoCliente = true,
            filtrarSemana = FiltrarTurnosPorSemanaUseCase(Clock.fixed(
                Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))))
        fun ids() = (vm.uiState.value as TurnosListUiState.Success).turnos.map { it.id }
        assertTrue(consultaCliente)
        assertEquals(listOf("actual"), ids())
        vm.seleccionarSemana(1)
        assertEquals(listOf("proximo"), ids())
        source.value = AppResult.Success(listOf(actual))
        assertTrue(ids().isEmpty())
        vm.onRetry()
        assertEquals(1, vm.semanaOffset.value)
        assertTrue(ids().isEmpty())
        vm.seleccionarSemana(0)
        assertEquals(listOf("actual"), ids())
    }
    @Test fun `las acciones se recalculan al pasar la hora y al recibir cambios remotos`() = runTest {
        val inicio = Instant.parse("2026-10-08T13:00:00Z").toEpochMilli()
        val turno = Turno("t1","cliente","barbero","corte",inicio,null,"Confirmado")
        val source = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(turno)))
        val repo = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = source
            override suspend fun marcarRealizado(turnoId: String) = AppResult.Success(Unit)
        }
        val vm = TurnoListViewModel(ObserveTurnosUseCase(repo),comoCliente=false,
            filtrarSemana=FiltrarTurnosPorSemanaUseCase(Clock.fixed(Instant.ofEpochMilli(inicio),ZoneId.of("America/Argentina/Buenos_Aires"))))
        assertTrue(vm.accionesTurnos(inicio-1).getValue("t1").editable)
        assertFalse(vm.accionesTurnos(inicio-1).getValue("t1").completable)
        assertFalse(vm.accionesTurnos(inicio).getValue("t1").editable)
        assertTrue(vm.accionesTurnos(inicio).getValue("t1").completable)
        source.value=AppResult.Error(ar.edu.unaj.am.myapplication.core.domain.model.DomainError.Network)
        assertTrue(vm.accionesTurnos(inicio).getValue("t1").completable)
        source.value=AppResult.Success(listOf(turno.copy(estado="Cancelada")))
        assertFalse(vm.accionesTurnos(inicio).getValue("t1").completable)
    }
}
