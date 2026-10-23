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
        val publicaciones = kotlinx.coroutines.flow.MutableStateFlow<AppResult<List<Publicacion>>>(AppResult.Success(emptyList()))
        val solicitudes = mutableListOf<NuevaPublicacion>()
        var resultado: AppResult<Unit> = AppResult.Success(Unit)
        var pendiente: CompletableDeferred<AppResult<Unit>>? = null
        val borradas = mutableListOf<String>()
        val modificadas = mutableListOf<Pair<String, String>>()
        override suspend fun modificarDescripcion(id: String, descripcion: String): AppResult<Unit> {
            modificadas += id to descripcion
            return pendiente?.await() ?: resultado
        }
        override fun observar() = publicaciones
        override suspend fun publicar(publicacion: NuevaPublicacion): AppResult<Unit> {
            solicitudes += publicacion; return pendiente?.await() ?: resultado
        }
        override suspend fun eliminar(id: String): AppResult<Unit> { borradas += id; return AppResult.Success(Unit) }
    }
    private fun vm(repo: Fake, saved: SavedStateHandle = SavedStateHandle()) = HomeViewModel(
        ObservarPublicacionesUseCase(repo), PublicarTrabajoUseCase(repo), EliminarPublicacionUseCase(repo),
        ModificarDescripcionPublicacionUseCase(repo), saved)

    @Test fun `offline warning keeps saved photos visible and recovery clears it`() = runTest {
        val repo = Fake()
        val photo = Publicacion("foto", "barbero", "Luis", "https://foto", "Corte", 1L,
            imagenLocalPath = "/privado/foto.jpg")
        repo.publicaciones.value = AppResult.Success(listOf(photo))
        val vm = vm(repo)
        repo.publicaciones.value = AppResult.Error(DomainError.Network)
        assertEquals(listOf(photo), vm.uiState.value.publicaciones)
        assertFalse(vm.uiState.value.cargando)
        assertNotNull(vm.uiState.value.error)
        repo.publicaciones.value = AppResult.Success(listOf(photo.copy(descripcion = "Actualizada")))
        assertNull(vm.uiState.value.error)
        assertEquals("Actualizada", vm.uiState.value.publicaciones.single().descripcion)
    }

    @Test fun `only own descriptions can be edited and saving blocks duplicate requests`() = runTest {
        val repo = Fake().apply { pendiente = CompletableDeferred() }
        val vm = vm(repo)
        val p = Publicacion("id", "otro", "Otro", "https://foto", "Original", null, false)
        vm.editarDescripcion(p)
        assertNull(vm.uiState.value.modificando)
        vm.editarDescripcion(p.copy(esPropia = true))
        vm.cambiarDescripcion(" Nueva descripción ")
        vm.guardarDescripcion(); vm.guardarDescripcion()
        vm.cerrarDescripcion(); vm.cambiarDescripcion("Otro texto")
        assertNotNull(vm.uiState.value.modificando)
        assertEquals(listOf("id" to "Nueva descripción"), repo.modificadas)
        repo.pendiente!!.complete(AppResult.Success(Unit))
        assertNull(vm.uiState.value.modificando)
        assertFalse(vm.uiState.value.guardandoDescripcion)
    }

    @Test fun `description failure preserves draft for retry`() = runTest {
        val repo = Fake().apply { resultado = AppResult.Error(DomainError.Unknown("Sin conexión")) }
        val vm = vm(repo)
        vm.editarDescripcion(Publicacion("id", "yo", "Yo", "https://foto", "Original", null, true))
        vm.cambiarDescripcion("Cambio"); vm.guardarDescripcion()
        assertEquals("Cambio", vm.uiState.value.descripcionModificada)
        assertNotNull(vm.uiState.value.errorDescripcion)
        repo.resultado = AppResult.Success(Unit)
        vm.guardarDescripcion()
        assertNull(vm.uiState.value.modificando)
    }

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
