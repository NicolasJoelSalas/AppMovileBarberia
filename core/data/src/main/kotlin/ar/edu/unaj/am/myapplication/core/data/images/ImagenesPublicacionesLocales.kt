package ar.edu.unaj.am.myapplication.core.data.images

import ar.edu.unaj.am.myapplication.core.network.DescargadorImagenPublicacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

interface ArchivosPublicaciones {
    fun existe(ruta: String): Boolean
    suspend fun descargar(url: String): ByteArray
    suspend fun guardar(clienteId: String, url: String, bytes: ByteArray): String
    suspend fun depurar(clienteId: String, conservar: Set<String>)
    suspend fun limpiar(clienteId: String)
}

/** Archivos privados persistentes, fuera de la caché que Android puede vaciar. */
class ImagenesPublicacionesLocales(
    private val directorio: File,
    private val descargador: DescargadorImagenPublicacion = DescargadorImagenPublicacion()
) : ArchivosPublicaciones {
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun carpeta(clienteId: String) = File(directorio, hash(clienteId))

    override fun existe(ruta: String): Boolean {
        val file = File(ruta)
        return file.canonicalFile.toPath().startsWith(directorio.canonicalFile.toPath()) &&
            file.isFile && file.length() > 0
    }

    override suspend fun descargar(url: String) = descargador.descargar(url)

    override suspend fun guardar(clienteId: String, url: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= DescargadorImagenPublicacion.MAX_BYTES)
        val folder = carpeta(clienteId)
        check(folder.isDirectory || folder.mkdirs()) { "No se pudo crear el directorio de fotos." }
        val target = File(folder, "${hash(url)}.jpg")
        val temporary = File.createTempFile("descarga-", ".tmp", folder)
        try {
            temporary.outputStream().use { it.write(bytes) }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            target.absolutePath
        } finally { temporary.delete() }
    }

    override suspend fun depurar(clienteId: String, conservar: Set<String>) = withContext(Dispatchers.IO) {
        carpeta(clienteId).listFiles()?.filter { it.absolutePath !in conservar }?.forEach {
            check(it.delete() || !it.exists()) { "No se pudo eliminar una foto antigua." }
        }
        Unit
    }

    override suspend fun limpiar(clienteId: String) = withContext(Dispatchers.IO) {
        depurar(clienteId, emptySet())
        val folder = carpeta(clienteId)
        check(folder.delete() || !folder.exists()) { "No se pudo limpiar el directorio de fotos." }
    }
}
