package ar.edu.unaj.am.myapplication.core.network

import kotlinx.serialization.SerializationException
import retrofit2.HttpException as RetrofitHttpException
import java.io.IOException

/**
 * Resultado de una llamada de red. Distingue los dos tipos de falla típicos:
 * - HttpError: el servidor respondió, pero con un código de error (404, 500…).
 * - NetworkError: ni siquiera llegamos al servidor (sin internet, timeout…).
 * - ParseError: llegó una respuesta pero no tiene la forma que esperábamos.
 */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class HttpError(val code: Int, val message: String?) : NetworkResult<Nothing>
    data class NetworkError(val cause: IOException) : NetworkResult<Nothing>
    data class ParseError(val message: String?) : NetworkResult<Nothing>
}

/**
 * Envuelve una llamada para que NUNCA lance excepciones hacia arriba.
 * Solo atrapamos errores de red/HTTP: una CancellationException sigue propagándose,
 * que es lo correcto con corrutinas.
 */
suspend fun <T> safeApiCall(call: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(call())
    } catch (e: RetrofitHttpException) {
        NetworkResult.HttpError(e.code(), e.message())
    } catch (e: IOException) {
        NetworkResult.NetworkError(e)
    } catch (e: SerializationException) {
        NetworkResult.ParseError(e.message)
    }
