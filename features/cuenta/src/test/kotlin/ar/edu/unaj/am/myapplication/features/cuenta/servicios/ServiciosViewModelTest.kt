package ar.edu.unaj.am.myapplication.features.cuenta.servicios

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ServicioRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ServiciosViewModelTest {
    private class Fake : ServicioRepository {
        var guardado: ServicioBarbero? = null
        var resultado: AppResult<Unit> = AppResult.Success(Unit)
        var pendiente: CompletableDeferred<AppResult<Unit>>? = null
        var llamadas = 0
        override suspend fun obtenerMisServicios() = AppResult.Success(listOfNotNull(guardado))
        override suspend fun guardar(servicio: ServicioBarbero): AppResult<Unit> {
            llamadas++
            val result = pendiente?.await() ?: resultado
            if (result is AppResult.Success) guardado = servicio
            return result
        }
        override suspend fun cambiarEstado(id: String, activo: Boolean) = AppResult.Success(Unit)
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun vm(repo: Fake) = ServiciosViewModel(ObtenerMisServiciosUseCase(repo),
        GuardarServicioUseCase(repo), CambiarEstadoServicioUseCase(repo))

    @Test fun `price accepts decimal comma without rounding malformed amounts`() {
        assertEquals(1250050L, parsearPrecioCentavos("12500,50"))
        assertEquals(29L, parsearPrecioCentavos("0.29"))
        for (invalid in listOf("1.234", "1,234.50", "NaN", "-1", "1e4", "")) assertNull(parsearPrecioCentavos(invalid))
    }
    @Test fun `failed save keeps editor and ID for retry`() = runTest {
        val repo = Fake()
        val vm = vm(repo)
        vm.editar(); vm.nombre("Corte"); vm.precio("123,45"); vm.duracion("60")
        val id = vm.uiState.value.editor!!.id
        repo.resultado = AppResult.Error(DomainError.Network)
        vm.guardar()
        assertEquals(id, vm.uiState.value.editor?.id)
        assertFalse(vm.uiState.value.guardando)
        assertNotNull(vm.uiState.value.error)
        repo.resultado = AppResult.Success(Unit)
        vm.guardar()
        assertEquals(id, repo.guardado?.id)
        assertEquals(12345L, repo.guardado?.precioCentavos)
        assertEquals(60, repo.guardado?.duracionMinutos)
        assertNull(vm.uiState.value.editor)
        assertEquals(listOf(repo.guardado), vm.uiState.value.servicios)
    }
    @Test fun `double tap saves once and blocks editor dismissal while pending`() = runTest {
        val repo = Fake()
        val vm = vm(repo)
        vm.editar(); vm.nombre("Corte"); vm.precio("100")
        repo.pendiente = CompletableDeferred()
        vm.guardar(); vm.guardar(); vm.cerrarEditor()
        assertEquals(1, repo.llamadas)
        assertNotNull(vm.uiState.value.editor)
        assertTrue(vm.uiState.value.guardando)
        repo.pendiente!!.complete(AppResult.Success(Unit))
        assertNull(vm.uiState.value.editor)
    }
}
