package ar.edu.unaj.am.myapplication.core.data.images

import ar.edu.unaj.am.myapplication.core.network.CloudinaryApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Comparte la infraestructura de fotos, preservando la referencia completa sin recortarla. */
class SubidorReferenciaCorte(
    private val preparador: PreparadorImagenTrabajo,
    private val api: CloudinaryApi,
    private val cloudName: String,
    private val uploadPreset: String
) {
    suspend fun subir(local: String): String = withContext(Dispatchers.IO) {
        check(cloudName.matches(Regex("[A-Za-z0-9_-]+")) && uploadPreset.isNotBlank()) {
            "Falta configurar Cloudinary para adjuntar una referencia. Podés reservar sin foto."
        }
        val bytes = preparador.preparar(local, 0f, 0f, recortar = false)
        val imagen = api.subir(cloudName, uploadPreset.toRequestBody("text/plain".toMediaType()),
            MultipartBody.Part.createFormData("file", "referencia.jpg", bytes.toRequestBody("image/jpeg".toMediaType())))
        check(imagen.secureUrl.startsWith("https://res.cloudinary.com/$cloudName/image/upload/")) {
            "No se pudo obtener la imagen de referencia."
        }
        imagen.secureUrl
    }
}
