package ar.edu.unaj.am.myapplication.features.cuenta

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteGananciasViewModelTest {
    @Test fun `consulta como barbero actualiza importes y limpia resultados al fallar`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val turno = Turno("t", "cliente", "barbero", "corte",
                Instant.parse("2026-10-16T13:00:00Z").toEpochMilli(), null, "realizado", precioCobradoCentavos = 1500000)
            val source = MutableStateFlow<AppResult<List<Turno>>>(AppResult.Success(listOf(turno)))
            var cliente = true
            val repo = object : TurnoRepository {
                override fun observeTurnos(comoCliente: Boolean) = source.also { cliente = comoCliente }
                override suspend fun marcarRealizado(turnoId: String) = AppResult.Success(Unit)
            }
            val vm = ReporteGananciasViewModel(ObserveTurnosUseCase(repo), CalcularReporteGananciasUseCase(
                Clock.fixed(Instant.parse("2026-10-16T13:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"))))
            vm.cargar()
            assertFalse(cliente)
            assertEquals(1500000L, vm.uiState.value.reporte!!.totalCentavos)
            vm.periodo(true)
            vm.mover(-1)
            assertEquals(0L, vm.uiState.value.reporte!!.totalCentavos)
            vm.mover(2)
            assertEquals(0, vm.uiState.value.offset)
            source.value = AppResult.Error(DomainError.Network)
            assertNull(vm.uiState.value.reporte)
            source.value = AppResult.Success(listOf(turno.copy(precioCobradoCentavos = 2000000)))
            assertEquals(2000000L, vm.uiState.value.reporte!!.totalCentavos)
            vm.detener()
            assertNull(vm.uiState.value.reporte)
        } finally { Dispatchers.resetMain() }
    }
}
