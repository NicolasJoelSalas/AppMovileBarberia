package ar.edu.unaj.am.myapplication.core.domain.model

data class Turno(
    val id: String,
    val clienteId: String,
    val barberoId: String,
    val servicioId: String,
    val inicioMillis: Long?,
    val finMillis: Long?,
    val estado: String
)
