package ar.edu.unaj.am.myapplication.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.RutaRepository
import ar.edu.unaj.am.myapplication.core.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class OsrmRutaRepository @Inject constructor(private val api: RutasApi) : RutaRepository {
    private val mutex = Mutex()
    private var ultimaConsulta = 0L
    // Una sola solicitud a la vez y como máximo una por segundo para el servidor de demostración.
    override suspend fun calcular(origen: Coordenadas, destino: Coordenadas): AppResult<RutaBarberia> = mutex.withLock {
        val restante = 1000 - (System.nanoTime() / 1_000_000 - ultimaConsulta)
        if (restante > 0) delay(restante)
        ultimaConsulta = System.nanoTime() / 1_000_000
        when (val result = safeApiCall {
            api.calcular("${origen.longitud},${origen.latitud};${destino.longitud},${destino.latitud}")
        }) {
            is NetworkResult.Success -> mapearRuta(result.data)
            is NetworkResult.NetworkError -> AppResult.Error(DomainError.Network)
            is NetworkResult.HttpError -> AppResult.Error(DomainError.Unknown(
                if (result.code == 429) "El servicio de rutas está ocupado. Esperá un momento y reintentá."
                else "No se pudo calcular el recorrido. Reintentá."))
            is NetworkResult.ParseError -> AppResult.Error(DomainError.Known(ErrorReason.ROUTE_INVALID))
        }
    }
}

internal fun mapearRuta(dto: RespuestaRutaDto): AppResult<RutaBarberia> {
    val ruta = dto.routes.firstOrNull()
    if (dto.code != "Ok" || ruta == null) return AppResult.Error(DomainError.Known(ErrorReason.ROUTE_MISSING))
    val puntos = ruta.geometry.coordinates.map { par ->
        if (par.size < 2) return AppResult.Error(DomainError.Known(ErrorReason.ROUTE_INVALID))
        Coordenadas(par[1], par[0])
    }
    if (puntos.size < 2 || puntos.any { !it.esValida } || !ruta.distance.isFinite() ||
        !ruta.duration.isFinite() || ruta.distance < 0 || ruta.duration < 0)
        return AppResult.Error(DomainError.Known(ErrorReason.ROUTE_INVALID))
    return AppResult.Success(RutaBarberia(puntos, ruta.distance, ruta.duration))
}
