package ar.edu.unaj.am.myapplication.core.database.mapper

import ar.edu.unaj.am.myapplication.core.database.model.PublicacionEntity
import ar.edu.unaj.am.myapplication.core.domain.model.Publicacion

fun Publicacion.toEntity(clienteId: String, ruta: String? = null) = PublicacionEntity(
    clienteId, id, barberoId, barberoNombre, imagenUrl, descripcion, creadoEnMillis, ruta
)

fun PublicacionEntity.toDomain() = Publicacion(
    id, barberoId, barberoNombre, imagenUrl, descripcion, creadoEnMillis,
    esPropia = false, imagenLocalPath = imagenLocalPath
)
