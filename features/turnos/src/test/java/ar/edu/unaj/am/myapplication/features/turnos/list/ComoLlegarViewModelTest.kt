package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.features.turnos.ubicacion.*
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComoLlegarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `usa el destino del barbero del turno y cancela la ruta anterior`() = runTest(dispatcher) {
        val repo = FakeUbicacion()
        val rutas = object : RutaRepository {
            override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> {
                delay(1000)
                return AppResult.Success(RutaBarberia(listOf(origen, destino), 100.0, 60.0))
            }
        }
        val vm = ComoLlegarViewModel("barbero-2", ObtenerUbicacionBarberiaUseCase(repo), CalcularRutaBarberiaUseCase(rutas))
        advanceUntilIdle()
        assertEquals("barbero-2", repo.consultado)
        vm.origen(Coordenadas(-34.0, -58.0))
        advanceTimeBy(500)
        val nuevoOrigen = Coordenadas(-34.1, -58.1)
        vm.origen(nuevoOrigen)
        advanceUntilIdle()
        assertEquals(listOf(nuevoOrigen, repo.destino), vm.uiState.value.ruta?.puntos)
        assertFalse(vm.uiState.value.calculando)
    }

    @Test fun `sin ubicacion del barbero no calcula una ruta inventada`() = runTest(dispatcher) {
        val repo = FakeUbicacion().apply { destino = null }
        var consultas = 0
        val rutas = object : RutaRepository {
            override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> {
                consultas++
                return AppResult.Error(DomainError.Network)
            }
        }
        val vm = ComoLlegarViewModel("barbero", ObtenerUbicacionBarberiaUseCase(repo), CalcularRutaBarberiaUseCase(rutas))
        advanceUntilIdle()
        vm.origen(Coordenadas(0.0, 0.0))
        advanceUntilIdle()
        assertEquals(0, consultas)
        assertNull(vm.uiState.value.destino)
        assertFalse(vm.uiState.value.cargando)
    }

    private class FakeUbicacion : UbicacionBarberiaRepository {
        var consultado: String? = null
        var destino: Coordenadas? = Coordenadas(-34.8, -58.3)
        override suspend fun obtenerMiUbicacion() = AppResult.Success(destino)
        override suspend fun obtenerUbicacion(barberoId: String): AppResult<Coordenadas?> {
            consultado = barberoId
            return AppResult.Success(destino)
        }
        override suspend fun guardarMiUbicacion(ubicacion: Coordenadas) = AppResult.Success(Unit)
    }
}
