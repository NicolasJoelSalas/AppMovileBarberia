package ar.edu.unaj.am.myapplication.core.domain.model

data class PosicionDispositivo(
    val coordenadas: Coordenadas,
    val precisionMetros: Float,
    val instanteMonotonicoMillis: Long
) {
    fun esReciente(ahora: Long): Boolean = coordenadas.esValida && precisionMetros.isFinite() &&
        precisionMetros >= 0 && ahora - instanteMonotonicoMillis in 0L..120_000L
}

