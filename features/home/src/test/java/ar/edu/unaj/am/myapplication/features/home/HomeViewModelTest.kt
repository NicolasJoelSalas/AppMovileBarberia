package ar.edu.unaj.am.myapplication.features.home

import androidx.lifecycle.SavedStateHandle
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PublicacionRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Fake : PublicacionRepository {
        val solicitudes = mutableListOf<NuevaPublicacion>()
        var resultado: AppResult<Unit> = AppResult.Success(Unit)
        var pendiente: CompletableDeferred<AppResult<Unit>>? = null
        val borradas = mutableListOf<String>()
        override fun observar() = flowOf(AppResult.Success(emptyList<Publicacion>()))
        override suspend fun publicar(publicacion: NuevaPublicacion): AppResult<Unit> {
            solicitudes += publicacion; return pendiente?.await() ?: resultado
        }
        override suspend fun eliminar(id: String): AppResult<Unit> { borradas += id; return AppResult.Success(Unit) }
    }
    private fun vm(repo: Fake, saved: SavedStateHandle = SavedStateHandle()) = HomeViewModel(
        ObservarPublicacionesUseCase(repo), PublicarTrabajoUseCase(repo), EliminarPublicacionUseCase(repo), saved)

    @Test fun `double tap publishes once and blocks draft changes and exit`() = runTest {
        val repo = Fake().apply { pendiente = CompletableDeferred() }
        val vm = vm(repo)
        vm.nueva(); vm.imagen("content://foto"); vm.confirmar(); vm.confirmar()
        vm.imagen("content://otra"); vm.cerrarEditor(); vm.descripcion("cambiada")
        assertEquals(1, repo.solicitudes.size)
        assertEquals("content://foto", vm.uiState.value.imagen)
        assertTrue(vm.uiState.value.editando)
        assertEquals("", vm.uiState.value.descripcion)
        repo.pendiente!!.complete(AppResult.Success(Unit))
        assertFalse(vm.uiState.value.editando)
        assertEquals("", vm.uiState.value.imagen)
    }
    @Test fun `failure keeps stable ID and draft for retry including restored state`() = runTest {
        val repo = Fake().apply { resultado = AppResult.Error(DomainError.Unknown("Sin conexión")) }
        val saved = SavedStateHandle()
        val vm = vm(repo, saved)
        vm.nueva(); vm.imagen("content://foto"); vm.descripcion("Degradado"); vm.encuadre(.5f, -.5f)
        vm.confirmar()
        assertTrue(vm.uiState.value.editando)
        val restored = vm(repo, saved)
        assertEquals(.5f, restored.uiState.value.recorteX)
        assertEquals("Degradado", restored.uiState.value.descripcion)
        repo.resultado = AppResult.Success(Unit)
        restored.confirmar()
        assertEquals(repo.solicitudes[0].solicitudId, repo.solicitudes[1].solicitudId)
    }
    @Test fun `cannot delete another barbers publication`() = runTest {
        val repo = Fake()
        val vm = vm(repo)
        val p = Publicacion("id", "otro", "Otro", "https://foto", "", null, false)
        vm.eliminar(p)
        assertTrue(repo.borradas.isEmpty())
        vm.eliminar(p.copy(esPropia = true))
        assertEquals(listOf("id"), repo.borradas)
    }
}
