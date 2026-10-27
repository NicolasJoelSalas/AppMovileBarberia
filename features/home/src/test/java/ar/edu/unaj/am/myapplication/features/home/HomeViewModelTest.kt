package ar.edu.unaj.am.myapplication.features.home

import androidx.lifecycle.SavedStateHandle
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import ar.edu.unaj.am.myapplication.core.testing.FakePublicacionRepository
import ar.edu.unaj.am.myapplication.core.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private fun vm(repo: FakePublicacionRepository, saved: SavedStateHandle = SavedStateHandle(),
        capturas: ar.edu.unaj.am.myapplication.core.domain.repository.CapturasTemporalesRepository = object : ar.edu.unaj.am.myapplication.core.domain.repository.CapturasTemporalesRepository {
            override suspend fun crear() = "content://captura"
            override suspend fun descartar(uri: String) = Unit
        }) = HomeViewModel(
        ObservarPublicacionesUseCase(repo), PublicarTrabajoUseCase(repo), EliminarPublicacionUseCase(repo),
        ModificarDescripcionPublicacionUseCase(repo), CapturasTemporalesUseCase(capturas), saved)

    @Test fun `cerrar el editor mientras se prepara la captura evita abrir una camara tardia`() = runTest {
        val pendiente = CompletableDeferred<String>()
        var descartada: String? = null
        var aperturas = 0
        val capturas = object : ar.edu.unaj.am.myapplication.core.domain.repository.CapturasTemporalesRepository {
            override suspend fun crear() = pendiente.await()
            override suspend fun descartar(uri: String) { descartada = uri }
        }
        val vm = vm(FakePublicacionRepository(), capturas = capturas)
        vm.nueva(); vm.prepararCaptura { aperturas++ }; vm.prepararCaptura { aperturas++ }
        vm.cerrarEditor(); pendiente.complete("content://captura")
        assertEquals(0, aperturas)
        assertEquals("content://captura", descartada)
    }

    @Test fun `offline warning keeps saved photos visible and recovery clears it`() = runTest {
        val repo = FakePublicacionRepository()
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
        val repo = FakePublicacionRepository().apply { pendiente = CompletableDeferred() }
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
        val repo = FakePublicacionRepository().apply { resultado = AppResult.Error(DomainError.Unknown("Sin conexión")) }
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
        val repo = FakePublicacionRepository().apply { pendiente = CompletableDeferred() }
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
        val repo = FakePublicacionRepository().apply { resultado = AppResult.Error(DomainError.Unknown("Sin conexión")) }
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
        val repo = FakePublicacionRepository()
        val vm = vm(repo)
        val p = Publicacion("id", "otro", "Otro", "https://foto", "", null, false)
        vm.eliminar(p)
        assertTrue(repo.borradas.isEmpty())
        vm.eliminar(p.copy(esPropia = true))
        assertEquals(listOf("id"), repo.borradas)
    }
}
