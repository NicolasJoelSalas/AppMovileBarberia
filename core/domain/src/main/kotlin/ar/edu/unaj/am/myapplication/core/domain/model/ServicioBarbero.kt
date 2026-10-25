package ar.edu.unaj.am.myapplication.core.domain.model

/** El precio se expresa en centavos para evitar errores de redondeo. */
data class ServicioBarbero(
    val id: String = "",
    val nombre: String,
    val precioCentavos: Long,
    val duracionMinutos: Int,
    val activo: Boolean = true
)
