package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.data.images.ArchivosPublicaciones
import ar.edu.unaj.am.myapplication.core.database.dao.PublicacionDao
import ar.edu.unaj.am.myapplication.core.database.model.PublicacionEntity
import ar.edu.unaj.am.myapplication.core.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PublicacionesClienteLocalTest {
    private val dao = MemoryPublicacionDao()
    private val files = MemoryPhotos()
    private val photo = Publicacion("p1", "barbero", "Luis", "https://res.cloudinary.com/demo/image/upload/p1.jpg", "Corte", 1000L)
    private val local = PublicacionesClienteLocal(dao, files) { "ana" }

    @Test fun `downloaded photos survive reopening offline and are not downloaded again`() = runTest {
        val remote = MutableStateFlow<AppResult<List<Publicacion>>>(AppResult.Success(listOf(photo)))
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { local.observar("ana", remote).collect() }
        runCurrent()
        val stored = dao.obtenerPorCliente("ana").single()
        assertNotNull(stored.imagenLocalPath)
        assertTrue(files.existe(stored.imagenLocalPath!!))
        remote.value = AppResult.Success(listOf(photo.copy(descripcion = "Nuevo texto")))
        runCurrent()
        assertEquals(1, files.downloads)
        job.cancelAndJoin()
        val offline = flowOf<AppResult<List<Publicacion>>>(AppResult.Error(DomainError.Network))
        val reopened = PublicacionesClienteLocal(dao, files) { "ana" }
        val result = reopened.observar("ana", offline).first { it is AppResult.Success } as AppResult.Success
        assertEquals(stored.imagenLocalPath, result.data.single().imagenLocalPath)
        assertEquals("Nuevo texto", result.data.single().descripcion)
    }

    @Test fun `image failure keeps publication and remote URL and reports retry`() = runTest {
        files.fail = true
        val events = mutableListOf<AppResult<List<Publicacion>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            local.observar("ana", flowOf(AppResult.Success(listOf(photo)))).collect { events += it }
        }
        runCurrent()
        val stored = dao.obtenerPorCliente("ana").single()
        assertEquals(photo.imagenUrl, stored.imagenUrl)
        assertNull(stored.imagenLocalPath)
        assertTrue(events.any { it is AppResult.Error })
    }

    @Test fun `confirmed deletions remove old image files and records`() = runTest {
        val remote = MutableStateFlow<AppResult<List<Publicacion>>>(AppResult.Success(listOf(photo)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { local.observar("ana", remote).collect() }
        runCurrent()
        assertEquals(1, files.paths.size)
        remote.value = AppResult.Error(DomainError.Network)
        runCurrent()
        assertEquals(1, dao.obtenerPorCliente("ana").size)
        assertEquals(1, files.paths.size)
        remote.value = AppResult.Success(emptyList())
        runCurrent()
        assertTrue(dao.obtenerPorCliente("ana").isEmpty())
        assertTrue(files.paths.isEmpty())
    }

    @Test fun `logout prevents a pending download from recreating client files`() = runTest {
        files.gate = CompletableDeferred()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            local.observar("ana", flowOf(AppResult.Success(listOf(photo)))).collect()
        }
        runCurrent()
        assertEquals(1, files.downloads)
        local.limpiar("ana")
        files.gate!!.complete(Unit)
        runCurrent()
        assertTrue(files.paths.isEmpty())
        assertTrue(dao.obtenerPorCliente("ana").isEmpty())
    }

    @Test fun `changed URL replaces photo and removes obsolete file`() = runTest {
        val remote = MutableStateFlow<AppResult<List<Publicacion>>>(AppResult.Success(listOf(photo)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { local.observar("ana", remote).collect() }
        runCurrent()
        val old = dao.obtenerPorCliente("ana").single().imagenLocalPath
        remote.value = AppResult.Success(listOf(photo.copy(imagenUrl = "https://res.cloudinary.com/demo/image/upload/nueva.jpg")))
        runCurrent()
        assertEquals(2, files.downloads)
        assertFalse(files.paths.contains(old))
        assertEquals(1, files.paths.size)
    }
}

private class MemoryPublicacionDao : PublicacionDao {
    private val rows = MutableStateFlow<List<PublicacionEntity>>(emptyList())
    override fun observarPorCliente(clienteId: String) = rows.map { all -> all.filter { it.clienteId == clienteId } }
    override suspend fun obtenerPorCliente(clienteId: String) = rows.value.filter { it.clienteId == clienteId }
    override suspend fun guardarTodos(publicaciones: List<PublicacionEntity>) {
        rows.value = rows.value.filter { old -> publicaciones.none { it.id == old.id && it.clienteId == old.clienteId } } + publicaciones
    }
    override suspend fun actualizarImagen(clienteId: String, id: String, url: String, ruta: String) {
        rows.value = rows.value.map { if (it.clienteId == clienteId && it.id == id && it.imagenUrl == url) it.copy(imagenLocalPath = ruta) else it }
    }
    override suspend fun eliminarPorCliente(clienteId: String) { rows.value = rows.value.filterNot { it.clienteId == clienteId } }
}

private class MemoryPhotos : ArchivosPublicaciones {
    val paths = mutableSetOf<String>()
    var downloads = 0
    var fail = false
    var gate: CompletableDeferred<Unit>? = null
    override fun existe(ruta: String) = ruta in paths
    override suspend fun descargar(url: String): ByteArray {
        downloads++
        gate?.await()
        if (fail) throw IOException("sin red")
        return byteArrayOf(1, 2, 3)
    }
    override suspend fun guardar(clienteId: String, url: String, bytes: ByteArray) = "$clienteId/$url".also { paths += it }
    override suspend fun depurar(clienteId: String, conservar: Set<String>) { paths.removeAll { it.startsWith("$clienteId/") && it !in conservar } }
    override suspend fun limpiar(clienteId: String) { depurar(clienteId, emptySet()) }
}
