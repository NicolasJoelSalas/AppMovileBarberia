package ar.edu.unaj.am.myapplication.core.data.images

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import ar.edu.unaj.am.myapplication.core.domain.repository.CapturasTemporalesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CapturasTemporales @Inject constructor(@ApplicationContext private val context: Context) : CapturasTemporalesRepository {
    override suspend fun crear(): String = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir,"trabajos").apply { mkdirs() }
        folder.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86400000 }?.forEach { it.delete() }
        val file = File.createTempFile("trabajo-",".jpg",folder)
        FileProvider.getUriForFile(context,"${context.packageName}.trabajos.files",file).toString()
    }
    override suspend fun descartar(uri: String): Unit = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        // Solo se eliminan las capturas del proveedor de esta aplicación.
        if (parsed.scheme == "content" && parsed.authority == "${context.packageName}.trabajos.files")
            context.contentResolver.delete(parsed,null,null)
        Unit
    }
}
