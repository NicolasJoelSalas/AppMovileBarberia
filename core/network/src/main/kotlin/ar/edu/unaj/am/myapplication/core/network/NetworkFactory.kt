package ar.edu.unaj.am.myapplication.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Arma el cliente HTTP. Lo usa AppContainer; nadie más conoce Retrofit ni OkHttp. */
object NetworkFactory {
    fun createCloudinaryApi(): CloudinaryApi = Retrofit.Builder()
        .baseUrl("https://api.cloudinary.com/")
        .client(OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(CloudinaryApi::class.java)

    private const val GUTENDEX_BASE_URL = "https://gutendex.com/"

    // ignoreUnknownKeys: la API manda más campos de los que usamos (editors, bookshelves…)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param logger dónde escribir los logs de red, o null para no loguear.
     * Lo recibimos de afuera porque este módulo es Kotlin puro y no conoce android.util.Log.
     */
    fun createBookApi(logger: ((String) -> Unit)? = null): BookApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Gutendex puede ser lento en pedidos no cacheados
            .apply {
                // BASIC: método, URL, código de respuesta y duración de cada request
                if (logger != null) {
                    addInterceptor(
                        HttpLoggingInterceptor { message -> logger(message) }
                            .setLevel(HttpLoggingInterceptor.Level.BASIC)
                    )
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(GUTENDEX_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BookApi::class.java)
    }
}
