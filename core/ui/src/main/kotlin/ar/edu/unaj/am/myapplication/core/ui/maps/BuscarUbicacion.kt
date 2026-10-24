package ar.edu.unaj.am.myapplication.core.ui.maps

import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

data class PosicionDispositivo(
    val coordenadas: Coordenadas,
    val precisionMetros: Float,
    val instanteMonotonicoMillis: Long
) {
    fun esReciente(ahora: Long): Boolean = coordenadas.esValida && precisionMetros.isFinite() &&
        precisionMetros >= 0 && ahora - instanteMonotonicoMillis in 0L..120_000L
}

/** Ningún proveedor lento o no disponible impide aprovechar los demás. */
internal suspend fun buscarUbicacion(
    proveedores: List<String>,
    ultimaPosicion: (String) -> PosicionDispositivo?,
    obtenerActual: suspend (String) -> PosicionDispositivo?,
    reloj: () -> Long,
    esperaMillis: Long = 25_000
): PosicionDispositivo? = supervisorScope {
    fun recienteGuardada() = proveedores.mapNotNull { proveedor ->
        try { ultimaPosicion(proveedor)?.takeIf { it.esReciente(reloj()) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }.minByOrNull { it.precisionMetros }

    // Una posición precisa de los últimos 15 segundos ya sirve para esta consulta puntual.
    recienteGuardada()?.takeIf {
        reloj() - it.instanteMonotonicoMillis <= 15_000 && it.precisionMetros <= 100
    }?.let { return@supervisorScope it }

    val resultados = Channel<PosicionDispositivo?>(Channel.UNLIMITED)
    val solicitudes = proveedores.map { proveedor ->
        launch {
            val posicion = try { obtenerActual(proveedor) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            resultados.send(posicion)
        }
    }
    try {
        withTimeoutOrNull(esperaMillis) {
            repeat(proveedores.size) {
                val posicion = resultados.receive()
                if (posicion != null && posicion.esReciente(reloj())) return@withTimeoutOrNull posicion
            }
            null
        } ?: recienteGuardada()
    } finally {
        solicitudes.forEach { it.cancel() }
    }
}
