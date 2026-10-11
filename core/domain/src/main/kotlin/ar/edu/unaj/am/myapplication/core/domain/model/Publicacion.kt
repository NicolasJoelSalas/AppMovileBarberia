package ar.edu.unaj.am.myapplication.core.domain.model

data class Publicacion(
    val id: String,
    val barberoId: String,
    val barberoNombre: String,
    val imagenUrl: String,
    val descripcion: String,
    val creadoEnMillis: Long?,
    val esPropia: Boolean = false
)

data class NuevaPublicacion(
    val solicitudId: String,
    val imagenLocal: String,
    val descripcion: String,
    val recorteX: Float = 0f,
    val recorteY: Float = 0f
)
