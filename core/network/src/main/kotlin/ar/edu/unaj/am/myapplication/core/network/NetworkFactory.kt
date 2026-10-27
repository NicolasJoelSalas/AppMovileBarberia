package ar.edu.unaj.am.myapplication.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Arma el cliente HTTP. Lo usa NetworkModule; nadie más conoce Retrofit ni OkHttp. */
object NetworkFactory {
    fun createRutasApi(baseUrl: String = "https://router.project-osrm.org/"): RutasApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "BarberApp-UNAJ/1.0 (Android)").build()) }.build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(RutasApi::class.java)

    fun createCloudinaryApi(): CloudinaryApi = Retrofit.Builder()
        .baseUrl("https://api.cloudinary.com/")
        .client(OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(CloudinaryApi::class.java)



    // ignoreUnknownKeys: la API manda más campos de los que usamos (editors, bookshelves…)
    private val json = Json { ignoreUnknownKeys = true }

}
