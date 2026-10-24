package ar.edu.unaj.am.myapplication.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface RutasApi {
    @GET("route/v1/driving/{coordenadas}?overview=full&geometries=geojson&steps=false&alternatives=false")
    suspend fun calcular(@Path("coordenadas") coordenadas: String): RespuestaRutaDto
}

@Serializable
data class RespuestaRutaDto(val code: String, val routes: List<RutaDto> = emptyList())
@Serializable
data class RutaDto(val distance: Double, val duration: Double, val geometry: GeometriaRutaDto)
@Serializable
data class GeometriaRutaDto(val coordinates: List<List<Double>>)
