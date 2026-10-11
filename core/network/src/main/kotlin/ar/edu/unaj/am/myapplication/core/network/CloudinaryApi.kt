package ar.edu.unaj.am.myapplication.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

@Serializable
data class ImagenSubidaDto(
    @SerialName("secure_url") val secureUrl: String,
    @SerialName("public_id") val publicId: String
)

interface CloudinaryApi {
    @Multipart
    @POST("v1_1/{cloud}/image/upload")
    suspend fun subir(
        @Path("cloud") cloud: String,
        @Part("upload_preset") preset: RequestBody,
        @Part file: MultipartBody.Part
    ): ImagenSubidaDto
}
