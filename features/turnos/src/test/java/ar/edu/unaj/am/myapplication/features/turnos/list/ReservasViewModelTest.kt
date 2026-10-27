package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import ar.edu.unaj.am.myapplication.core.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReservasViewModelTest {
    private val repo = Fake()
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private fun vm() = ReservasViewModel(CargarCatalogoReservasUseCase(repo),
        ConsultarSemanaUseCase(repo), ReservarTurnoUseCase(repo), ModificarTurnoUseCase(repo))

    @Test fun `does not request availability until both selections exist`() = runTest {
        val vm=vm()
        vm.barbero("barbero")
        assertEquals(0,repo.queries)
        vm.servicio("corte")
        assertEquals(1,repo.queries)
    }
    @Test fun `retry keeps request ID and client name without duplicating confirmation`() = runTest {
        val vm=vm()
        vm.barbero("barbero"); vm.servicio("corte")
        vm.nombre(" Ana ")
        vm.seleccionar(HorarioDisponible(1000,2401000))
        repo.result=AppResult.Error(DomainError.Network)
        vm.confirmar()
        assertFalse(vm.uiState.value.reservando)
        assertNotNull(vm.uiState.value.selected)
        repo.result=AppResult.Success("turno")
        vm.confirmar()
        assertEquals(repo.requests[0].solicitudId,repo.requests[1].solicitudId)
        assertEquals("Ana",repo.requests.last().nombreCliente)
        assertNull(vm.uiState.value.selected)
        assertNotNull(vm.uiState.value.mensaje)
    }
    @Test fun `changing service discards the old selected slot`() = runTest {
        val vm=vm()
        vm.barbero("barbero"); vm.servicio("corte")
        vm.seleccionar(HorarioDisponible(1000,2401000))
        vm.servicio("otro")
        assertNull(vm.uiState.value.selected)
    }
    @Test fun `changing week requests next week and discards previous selection`() = runTest {
        val vm = vm()
        vm.barbero("barbero"); vm.servicio("corte")
        vm.seleccionar(HorarioDisponible(1000, 2401000))
        vm.semana(1)
        assertEquals(1, repo.offset)
        assertEquals(1, vm.uiState.value.semanaOffset)
        assertNull(vm.uiState.value.selected)
        vm.semana(0)
        assertEquals(0, repo.offset)
    }
    @Test fun `double tap while confirmation is pending submits only once`() = runTest {
        val vm=vm()
        vm.barbero("barbero"); vm.servicio("corte")
        vm.seleccionar(HorarioDisponible(1000,2401000))
        repo.pending=CompletableDeferred()
        vm.confirmar(); vm.confirmar()
        assertEquals(1,repo.requests.size)
        assertFalse(vm.uiState.value.reservaConfirmada)
        repo.pending!!.complete(AppResult.Success("turno"))
        assertTrue(vm.uiState.value.reservaConfirmada)
    }
    @Test fun `editing preloads booking and updates it without creating another reservation`() = runTest {
        val vm = vm()
        vm.editar("existente", "barbero", "corte", System.currentTimeMillis()+86400000)
        assertEquals("barbero", vm.uiState.value.barberoId)
        assertEquals("corte", vm.uiState.value.servicioId)
        vm.seleccionar(HorarioDisponible(1000,2401000))
        vm.confirmar()
        assertEquals("existente", repo.modifiedId)
        assertTrue(repo.requests.isEmpty())
        assertTrue(vm.uiState.value.reservaConfirmada)
    }
    @Test fun `reference is optional and selected image survives a failed confirmation`() = runTest {
        val vm = vm()
        vm.barbero("barbero"); vm.servicio("corte")
        vm.seleccionar(HorarioDisponible(1000, 2401000))
        repo.result = AppResult.Error(DomainError.Network)
        vm.confirmar()
        assertNull(repo.requests.last().referenciaLocal)
        vm.referencia("content://foto")
        vm.confirmar()
        assertEquals("content://foto", repo.requests.last().referenciaLocal)
        assertTrue(repo.requests.last().cambiarReferencia)
        assertEquals("content://foto", vm.uiState.value.referencia)
        vm.referencia(null)
        vm.confirmar()
        assertNull(repo.requests.last().referenciaLocal)
        assertTrue(repo.requests.last().cambiarReferencia)
    }

    @Test fun `editing preserves reference until explicitly removed`() = runTest {
        val vm = vm()
        vm.editar("existente", "barbero", "corte", System.currentTimeMillis() + 86400000, "https://foto")
        assertEquals("https://foto", vm.uiState.value.referencia)
        assertFalse(vm.uiState.value.cambiarReferencia)
        vm.referencia(null)
        assertNull(vm.uiState.value.referencia)
        assertTrue(vm.uiState.value.cambiarReferencia)
    }

    @Test fun `changing barber clears a service owned by the previous barber`() = runTest {
        val vm = vm()
        vm.barbero("barbero"); vm.servicio("corte")
        vm.barbero("otro")
        assertEquals("", vm.uiState.value.servicioId)
        vm.servicio("otro-corte")
        vm.barbero("otro")
        assertEquals("otro-corte", vm.uiState.value.servicioId)
    }

    private class Fake: ReservaRepository {
        var modifiedId: String? = null
        override suspend fun modificar(turnoId: String, solicitud: SolicitudReserva): AppResult<String> {
            modifiedId = turnoId
            return result
        }
        var offset = 0
        override suspend fun guardarMiAgenda(agenda: AgendaSemanal): AppResult<Unit> = error("No es barbero")
        override suspend fun obtenerMiAgenda(semanaOffset: Int): AppResult<AgendaSemanal> = error("No es barbero")
        override suspend fun obtenerMisTurnos(): AppResult<List<Turno>> = error("No es barbero")
        override suspend fun publicarSemana(agenda: AgendaSemanal, semana: SemanaDisponible): AppResult<Int> = error("No es barbero")
        var queries=0
        val requests=mutableListOf<SolicitudReserva>()
        var result: AppResult<String> = AppResult.Success("turno")
        var pending: CompletableDeferred<AppResult<String>>?=null
        override suspend fun cargarCatalogo()=AppResult.Success(CatalogoReservas(false,
            listOf(Barbero("barbero","Barbero")),listOf(Servicio("corte","Corte",40,"barbero"), Servicio("otro-corte","Otro",40,"otro")),"Ana"))
        override suspend fun consultarSemana(barberoId:String,servicioId:String,semanaOffset:Int): AppResult<SemanaDisponible> {
            offset = semanaOffset
            queries++
            return AppResult.Success(SemanaDisponible(0,listOf(HorarioDisponible(1000,2401000))))
        }
        override suspend fun reservar(solicitud:SolicitudReserva): AppResult<String> {
            requests.add(solicitud)
            return pending?.await()?:result
        }
    }
}
