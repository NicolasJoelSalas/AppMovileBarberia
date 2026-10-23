package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.data.images.ArchivosPublicaciones
import ar.edu.unaj.am.myapplication.core.database.dao.PublicacionDao
import ar.edu.unaj.am.myapplication.core.database.mapper.toDomain
import ar.edu.unaj.am.myapplication.core.database.mapper.toEntity
import ar.edu.unaj.am.myapplication.core.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordina la copia por cliente. La red nunca se ejecuta dentro del bloqueo de limpieza. */
class PublicacionesClienteLocal(
    private val dao: PublicacionDao,
    private val archivos: ArchivosPublicaciones,
    private val usuarioActual: () -> String?
) {
    private val mutex = Mutex()
    private val versiones = mutableMapOf<String, Long>()

    suspend fun limpiar(clienteId: String) = mutex.withLock {
        versiones[clienteId] = (versiones[clienteId] ?: 0L) + 1
        dao.eliminarPorCliente(clienteId)
        archivos.limpiar(clienteId)
    }

    fun observar(clienteId: String, remotos: Flow<AppResult<List<Publicacion>>>): Flow<AppResult<List<Publicacion>>> = channelFlow {
        val version = mutex.withLock { versiones[clienteId] ?: 0L }
        fun vigente() = usuarioActual() == clienteId && (versiones[clienteId] ?: 0L) == version
        val aviso = MutableStateFlow<DomainError?>(null)
        val local = launch {
            try {
                dao.observarPorCliente(clienteId).combine(aviso) { rows, error -> rows to error }
                    .collect { (rows, error) ->
                        if (usuarioActual() == clienteId) {
                            val publicaciones = rows.map { row ->
                                row.toDomain().let { p ->
                                    if (p.imagenLocalPath?.let(archivos::existe) == true) p
                                    else p.copy(imagenLocalPath = null)
                                }
                            }
                            send(AppResult.Success(publicaciones))
                            if (error != null) send(AppResult.Error(error))
                        }
                    }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                send(AppResult.Error(DomainError.Unknown("No se pudieron leer los trabajos guardados.")))
            }
        }
        try {
            remotos.collectLatest { result ->
                when (result) {
                    is AppResult.Error -> aviso.value = result.error
                    is AppResult.Success -> {
                        val publicaciones = result.data.take(50)
                        val actualizado = mutex.withLock {
                            if (!vigente()) false else {
                                val anteriores = dao.obtenerPorCliente(clienteId).associateBy { it.id }
                                dao.reemplazarPorCliente(clienteId, publicaciones.map { p ->
                                    val anterior = anteriores[p.id]
                                    val ruta = anterior?.imagenLocalPath?.takeIf {
                                        anterior.imagenUrl == p.imagenUrl && archivos.existe(it)
                                    }
                                    p.toEntity(clienteId, ruta)
                                })
                                true
                            }
                        }
                        if (!actualizado) return@collectLatest
                        var falloImagen = false
                        for (p in publicaciones) {
                            val necesita = mutex.withLock {
                                vigente() && dao.obtenerPorCliente(clienteId).find { it.id == p.id }
                                    ?.imagenLocalPath?.let(archivos::existe) != true
                            }
                            if (!necesita) continue
                            try {
                                val bytes = archivos.descargar(p.imagenUrl)
                                mutex.withLock {
                                    if (vigente()) {
                                        val ruta = archivos.guardar(clienteId, p.imagenUrl, bytes)
                                        dao.actualizarImagen(clienteId, p.id, p.imagenUrl, ruta)
                                    }
                                }
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { falloImagen = true }
                        }
                        mutex.withLock {
                            if (vigente()) {
                                archivos.depurar(clienteId, dao.obtenerPorCliente(clienteId).mapNotNull { it.imagenLocalPath }.toSet())
                                aviso.value = if (falloImagen) DomainError.Unknown(
                                    "Algunas fotos no se pudieron guardar para ver sin conexión. Reintentá con conexión.") else null
                            }
                        }
                    }
                }
            }
            awaitCancellation()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            aviso.value = DomainError.Unknown("No se pudieron actualizar los trabajos guardados. Reintentá.")
            awaitCancellation()
        } finally { local.cancel() }
    }
}
