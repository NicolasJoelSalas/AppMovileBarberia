package ar.edu.unaj.am.myapplication.features.cuenta

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PerfilRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import ar.edu.unaj.am.myapplication.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiCuentaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private fun viewModel() = MiCuentaViewModel(ObtenerMiPerfilUseCase(repository), ActualizarMiNombreUseCase(repository))

    @Test fun `un fallo conserva la edicion y un reintento actualiza el perfil y el catalogo`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("Ana", vm.uiState.value.nombre)
        assertFalse(vm.uiState.value.puedeGuardar)
        vm.onNombreChange(" Ana Pérez ")
        repository.resultado = AppResult.Error(DomainError.Network)
        var actualizaciones = 0
        vm.guardarCambios { actualizaciones++ }
        advanceUntilIdle()
        assertEquals(" Ana Pérez ", vm.uiState.value.nombre)
        assertEquals(DomainError.Network, vm.uiState.value.error)
        assertEquals(0, actualizaciones)
        assertTrue(vm.uiState.value.puedeGuardar)
        repository.resultado = AppResult.Success(Unit)
        vm.guardarCambios { actualizaciones++ }
        advanceUntilIdle()
        assertEquals("Ana Pérez", vm.uiState.value.perfil?.nombre)
        assertFalse(vm.uiState.value.puedeGuardar)
        assertEquals(1, actualizaciones)
    }

    @Test fun `impide envios duplicados y edicion mientras guarda`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNombreChange("Nuevo nombre")
        vm.guardarCambios {}
        vm.guardarCambios {}
        vm.onNombreChange("Cambio durante guardado")
        advanceUntilIdle()
        assertEquals(1, repository.escrituras)
        assertEquals("Nuevo nombre", vm.uiState.value.nombre)
    }

    @Test fun `permite reintentar una carga fallida`() = runTest(dispatcher) {
        repository.fallaCarga = true
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.uiState.value.perfil)
        assertFalse(vm.uiState.value.cargando)
        repository.fallaCarga = false
        vm.cargarPerfil()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.perfil)
        assertNull(vm.uiState.value.error)
    }

    private class FakeRepository : PerfilRepository {
        var resultado: AppResult<Unit> = AppResult.Success(Unit)
        var fallaCarga = false
        var escrituras = 0
        override suspend fun obtenerMiPerfil(): AppResult<PerfilUsuario> =
            if (fallaCarga) AppResult.Error(DomainError.Network)
            else AppResult.Success(PerfilUsuario("Ana", "ana@example.com", false))
        override suspend fun actualizarMiNombre(nombre: String): AppResult<Unit> {
            escrituras++
            return resultado
        }
    }
}
