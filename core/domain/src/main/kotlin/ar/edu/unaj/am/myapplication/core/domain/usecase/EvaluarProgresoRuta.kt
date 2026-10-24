package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Coordenadas
import ar.edu.unaj.am.myapplication.core.domain.model.RutaBarberia
import kotlin.math.*

private const val RADIO_TIERRA = 6_371_000.0

fun distanciaMetros(a: Coordenadas, b: Coordenadas): Double {
    val lat1 = Math.toRadians(a.latitud)
    val lat2 = Math.toRadians(b.latitud)
    val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) *
        sin(Math.toRadians(b.longitud - a.longitud) / 2).pow(2)
    return 2 * RADIO_TIERRA * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

data class ProgresoRuta(val desvioMetros: Double, val distanciaRestanteMetros: Double)

/** Proyección sobre segmentos, no sobre vértices: una recta larga también es parte de la ruta. */
fun evaluarProgresoRuta(posicion: Coordenadas, ruta: RutaBarberia): ProgresoRuta {
    if (ruta.puntos.size < 2) return ProgresoRuta(Double.POSITIVE_INFINITY, ruta.distanciaMetros)
    var menorDistancia = Double.POSITIVE_INFINITY
    var avance = 0.0
    var acumulado = 0.0
    val escalaX = cos(Math.toRadians(posicion.latitud)) * RADIO_TIERRA
    fun x(p: Coordenadas): Double {
        val delta = ((p.longitud - posicion.longitud + 540) % 360) - 180
        return Math.toRadians(delta) * escalaX
    }
    fun y(p: Coordenadas) = Math.toRadians(p.latitud - posicion.latitud) * RADIO_TIERRA
    ruta.puntos.zipWithNext().forEach { (a, b) ->
        val ax = x(a); val ay = y(a)
        val dx = x(b) - ax; val dy = y(b) - ay
        val largoCuadrado = dx * dx + dy * dy
        val fraccion = if (largoCuadrado == 0.0) 0.0 else (-(ax * dx + ay * dy) / largoCuadrado).coerceIn(0.0, 1.0)
        val distancia = hypot(ax + fraccion * dx, ay + fraccion * dy)
        val largo = distanciaMetros(a, b)
        if (distancia < menorDistancia) {
            menorDistancia = distancia
            avance = acumulado + largo * fraccion
        }
        acumulado += largo
    }
    val restante = if (acumulado == 0.0) 0.0 else ruta.distanciaMetros * (1 - avance / acumulado).coerceIn(0.0, 1.0)
    return ProgresoRuta(menorDistancia, restante)
}
