package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.FiltrarTurnosPorSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class TurnoListViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

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
}
