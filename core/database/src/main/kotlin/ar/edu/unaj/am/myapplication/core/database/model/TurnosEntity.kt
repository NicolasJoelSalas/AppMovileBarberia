package ar.edu.unaj.am.myapplication.core.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Copia persistente de un turno propio del cliente o barbero, con nombres sin conexión. */

@Entity(tableName = "turnos")
data class TurnosEntity(
    @PrimaryKey val id: String,
    val clienteId: String,
    val barberoId: String,
    val servicioId: String,
    val inicioMillis: Long?,
    val finMillis: Long?,
    val estado: String,
    val clienteNombre: String = "",
    val servicioNombre: String = "",
    val precioCobradoCentavos: Long? = null
)
