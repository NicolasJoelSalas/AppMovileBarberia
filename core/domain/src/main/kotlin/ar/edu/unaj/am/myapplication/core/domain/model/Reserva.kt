package ar.edu.unaj.am.myapplication.core.domain.model

data class Barbero(val id: String, val nombre: String)
data class Servicio(val id: String, val nombre: String, val duracion: Int)
data class CatalogoReservas(
    val esBarbero: Boolean,
    val barberos: List<Barbero>,
    val servicios: List<Servicio>,
    val nombreCliente: String,
    val sinConexion: Boolean = false
)
data class HorarioDisponible(val inicioMillis: Long, val finMillis: Long)
data class SemanaDisponible(
    val inicioSemanaMillis: Long,
    val horarios: List<HorarioDisponible>,
    val zonaHoraria: String = "America/Argentina/Buenos_Aires"
)
data class SolicitudReserva(
    val solicitudId: String,
    val barberoId: String,
    val servicioId: String,
    val inicioMillis: Long,
    val nombreCliente: String
)
