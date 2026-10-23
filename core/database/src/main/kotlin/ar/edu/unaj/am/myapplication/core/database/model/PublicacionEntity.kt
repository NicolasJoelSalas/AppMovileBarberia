package ar.edu.unaj.am.myapplication.core.database.model

import androidx.room3.Entity

/** La foto vive en un archivo privado; Room guarda su ruta y los datos del trabajo. */
@Entity(tableName = "publicaciones", primaryKeys = ["clienteId", "id"])
data class PublicacionEntity(
    val clienteId: String,
    val id: String,
    val barberoId: String,
    val barberoNombre: String,
    val imagenUrl: String,
    val descripcion: String,
    val creadoEnMillis: Long?,
    val imagenLocalPath: String?
)
