package ar.edu.unaj.am.myapplication.features.turnos.resumen

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import java.time.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenBarberoViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `uses barber query updates weekly summary and waits for persisted completion`() = runTest {
        val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))
        val turno = Turno("turno", "cliente", "barbero", "corte", clock.millis() - 7200000,
            clock.millis() - 3600000, "confirmado", precioServicioCentavos = 1250000)
        val fuente = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(turno)))
        var consultaCliente = true
        var escrito = false
        val repo = object : TurnoRepository {
            override fun observeTurnos(comoCliente: Boolean) = fuente.also { consultaCliente = comoCliente }
            override suspend fun marcarRealizado(turnoId: String, precioEsperadoCentavos: Long): AppResult<Unit> {
                assertEquals("turno", turnoId)
                assertEquals(1250000L, precioEsperadoCentavos)
                escrito = true
                return AppResult.Success(Unit)
            }
        }
        val vm = ResumenBarberoViewModel(ObserveTurnosUseCase(repo), CalcularResumenBarberoUseCase(clock),
            MarcarTurnoRealizadoUseCase(repo, clock))
        assertFalse(consultaCliente)
        assertEquals(1, vm.uiState.value.resumen!!.pendientes.size)
        vm.marcarRealizado(turno)
        assertTrue(escrito)
        assertEquals(0L, vm.uiState.value.resumen!!.gananciasCentavos)
        fuente.value = AppResult.Success(listOf(turno.copy(estado = "realizado", precioCobradoCentavos = 1250000)))
        assertEquals(1250000L, vm.uiState.value.resumen!!.gananciasCentavos)
        assertTrue(vm.uiState.value.resumen!!.pendientes.isEmpty())
        vm.semana(-1)
        assertTrue(vm.uiState.value.resumen!!.realizados.isEmpty())
        vm.semana(0)
        assertEquals(1250000L, vm.uiState.value.resumen!!.gananciasCentavos)
        fuente.value = AppResult.Error(DomainError.Network)
        assertNull(vm.uiState.value.resumen)
        assertEquals(DomainError.Network, vm.uiState.value.error)
    }
}
