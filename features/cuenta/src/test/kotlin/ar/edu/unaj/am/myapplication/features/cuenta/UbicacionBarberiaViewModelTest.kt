package ar.edu.unaj.am.myapplication.features.cuenta

import ar.edu.unaj.am.myapplication.features.cuenta.ubicacion.*
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionBarberiaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `conserva el pin ante fallo y evita guardar dos veces`() = runTest(dispatcher) {
        val repo = FakeUbicacion()
        val vm = UbicacionBarberiaViewModel(ObtenerMiUbicacionUseCase(repo), GuardarMiUbicacionUseCase(repo))
        advanceUntilIdle()
        val punto = Coordenadas(-34.8, -58.3)
        vm.seleccionar(punto)
        vm.guardar()
        vm.guardar()
        vm.seleccionar(Coordenadas(0.0, 0.0))
        advanceUntilIdle()
        assertEquals(1, repo.escrituras)
        assertEquals(punto, vm.uiState.value.seleccion)
        assertTrue(vm.uiState.value.puedeGuardar)
        repo.falla = false
        vm.guardar()
        advanceUntilIdle()
        assertEquals(punto, vm.uiState.value.guardada)
        assertFalse(vm.uiState.value.puedeGuardar)
        assertNotNull(vm.uiState.value.mensaje)
    }

    private class FakeUbicacion : UbicacionBarberiaRepository {
        var falla = true
        var escrituras = 0
        override suspend fun obtenerMiUbicacion() = AppResult.Success(null)
        override suspend fun obtenerUbicacion(barberoId: String) = AppResult.Success(null)
        override suspend fun guardarMiUbicacion(ubicacion: Coordenadas): AppResult<Unit> {
            escrituras++
            return if (falla) AppResult.Error(DomainError.Network) else AppResult.Success(Unit)
        }
    }
}
