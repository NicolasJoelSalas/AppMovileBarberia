package ar.edu.unaj.am.myapplication.core.domain.model

data class Coordenadas(val latitud: Double, val longitud: Double) {
    val esValida: Boolean get() = latitud.isFinite() && longitud.isFinite() &&
        latitud in -90.0..90.0 && longitud in -180.0..180.0
}

data class RutaBarberia(
    val puntos: List<Coordenadas>,
    val distanciaMetros: Double,
    val duracionSegundos: Double
)
