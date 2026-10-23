package ar.edu.unaj.am.myapplication.core.data.images

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ImagenesPublicacionesLocalesTest {
    @Test fun `files persist across storage instances and cleanup only affects owner`() = runTest {
        val root = Files.createTempDirectory("fotos-publicaciones").toFile()
        try {
            val store = ImagenesPublicacionesLocales(root)
            val bytes = byteArrayOf(1, 2, 3)
            val ana = store.guardar("ana/../../", "https://foto/1", bytes)
            val other = store.guardar("otro", "https://foto/1", bytes)
            assertNotEquals(ana, other)
            assertArrayEquals(bytes, java.io.File(ana).readBytes())
            val reopened = ImagenesPublicacionesLocales(root)
            assertTrue(reopened.existe(ana))
            assertFalse(reopened.existe(java.io.File(root.parentFile, "externa.jpg").path))
            reopened.limpiar("ana/../../")
            assertFalse(reopened.existe(ana))
            assertTrue(reopened.existe(other))
            reopened.limpiar("otro")
        } finally { root.delete() }
    }
}
