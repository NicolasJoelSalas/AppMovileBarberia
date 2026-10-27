package ar.edu.unaj.am.myapplication.features.turnos.agenda

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {
    private val repo = Fake()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun vm() = AgendaViewModel(ObtenerMiAgendaUseCase(repo),
        GuardarYPublicarAgendaUseCase(repo, PublicarSemanaUseCase(repo)))
    @Test fun `removing last span closes day and allows saving`() = runTest {
        val vm = vm()
        vm.habilitar(0, true)
        vm.hora(0, 0, true, "09:00")
        vm.hora(0, 0, false, "13:00")
        vm.quitar(0, 0)
        assertFalse(vm.uiState.value.agenda!!.dias[0].habilitado)
        assertTrue(vm.uiState.value.agenda!!.dias[0].franjas.isEmpty())
        vm.guardar()
        assertEquals(1, repo.writes)
        assertNull(vm.uiState.value.error)
    }
    @Test fun `edits remain draft until saved and duplicate taps do not submit twice`() = runTest {
        val vm = vm()
        vm.habilitar(0, true)
        vm.hora(0, 0, true, "09:00"); vm.hora(0, 0, false, "13:00")
        assertTrue(vm.uiState.value.cambios)
        assertFalse(repo.agenda.dias[0].habilitado)
        repo.pending = CompletableDeferred()
        vm.guardar(); vm.guardar()
        assertEquals(1, repo.writes)
        assertTrue(vm.uiState.value.guardando)
        vm.hora(0, 0, false, "20:00")
        assertEquals("13:00", vm.uiState.value.agenda!!.dias[0].franjas[0].fin)
        repo.pending!!.complete(AppResult.Success(Unit))
        assertFalse(vm.uiState.value.cambios)
        assertFalse(vm.uiState.value.guardando)
    }
    @Test fun `invalid schedule retains edits without persistence`() = runTest {
        val vm = vm()
        vm.habilitar(0, true)
        vm.guardar()
        assertEquals(0, repo.writes)
        assertTrue(vm.uiState.value.cambios)
        assertNotNull(vm.uiState.value.error)
    }
    @Test fun `network failure retains draft for retry`() = runTest {
        val vm = vm()
        vm.habilitar(0, true)
        vm.hora(0, 0, true, "09:00"); vm.hora(0, 0, false, "13:00")
        repo.result = AppResult.Error(DomainError.Network)
        vm.guardar()
        assertTrue(vm.uiState.value.cambios)
        assertEquals(DomainError.Network, vm.uiState.value.error)
        repo.result = AppResult.Success(Unit)
        vm.guardar()
        assertFalse(vm.uiState.value.cambios)
    }
    @Test fun `closing a day keeps its spans to reopen later`() = runTest {
        val vm = vm()
        vm.habilitar(0, true)
        vm.hora(0, 0, true, "09:00"); vm.hora(0, 0, false, "13:00")
        vm.habilitar(0, false); vm.habilitar(0, true)
        assertEquals("09:00", vm.uiState.value.agenda!!.dias[0].franjas[0].inicio)
    }
    @Test fun `switching weeks keeps independent drafts and unsaved exit state`() = runTest {
        val vm = vm()
        vm.habilitar(0, true)
        vm.hora(0, 0, true, "09:00"); vm.hora(0, 0, false, "13:00")
        vm.semana(1)
        assertEquals(1, repo.loadedWeek)
        assertFalse(vm.uiState.value.agenda!!.dias[0].habilitado)
        assertTrue(vm.tieneCambiosPendientes())
        vm.habilitar(1, true)
        vm.hora(1, 0, true, "16:00"); vm.hora(1, 0, false, "20:00")
        vm.semana(0)
        assertEquals("09:00", vm.uiState.value.agenda!!.dias[0].franjas[0].inicio)
        assertFalse(vm.uiState.value.agenda!!.dias[1].habilitado)
        vm.semana(1)
        assertEquals("16:00", vm.uiState.value.agenda!!.dias[1].franjas[0].inicio)
    }
    private class Fake : ReservaRepository {
        var loadedWeek = -1
        var agenda = AgendaSemanal("America/Argentina/Buenos_Aires", List(6) { DiaAtencion(false, emptyList()) })
        var writes = 0
        var pending: CompletableDeferred<AppResult<Unit>>? = null
        var result: AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun obtenerMiAgenda(semanaOffset: Int): AppResult<AgendaSemanal> {
            loadedWeek = semanaOffset
            return AppResult.Success(agenda)
        }
        override suspend fun guardarMiAgenda(agenda: AgendaSemanal): AppResult<Unit> {
            writes++
            val saved = pending?.await() ?: result
            if (saved is AppResult.Success) this.agenda = agenda
            return saved
        }
        override suspend fun obtenerMisTurnos() = AppResult.Success(emptyList<Turno>())
        override suspend fun publicarSemana(agenda: AgendaSemanal, semana: SemanaDisponible) = AppResult.Success(semana.horarios.size)
        override suspend fun cargarCatalogo(): AppResult<CatalogoReservas> = error("No usado")
        override suspend fun consultarSemana(barberoId: String, servicioId: String, semanaOffset: Int): AppResult<SemanaDisponible> = error("No usado")
        override suspend fun reservar(solicitud: SolicitudReserva): AppResult<String> = error("No usado")
    }
}
