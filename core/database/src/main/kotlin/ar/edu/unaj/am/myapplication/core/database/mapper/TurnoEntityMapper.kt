package ar.edu.unaj.am.myapplication.core.database.mapper

import ar.edu.unaj.am.myapplication.core.database.model.TurnosEntity
import ar.edu.unaj.am.myapplication.core.domain.model.Turno

fun Turno.toEntity() = TurnosEntity(
    id = id,
    clienteId = clienteId,
    barberoId = barberoId,
    servicioId = servicioId,
    inicioMillis = inicioMillis,
    finMillis = finMillis,
    estado = estado,
    clienteNombre = clienteNombre,
    servicioNombre = servicioNombre,
    precioCobradoCentavos = precioCobradoCentavos,
    referenciaCorteUrl = referenciaCorteUrl
)

fun TurnosEntity.toDomain() = Turno(
    id = id,
    clienteId = clienteId,
    barberoId = barberoId,
    servicioId = servicioId,
    inicioMillis = inicioMillis,
    finMillis = finMillis,
    estado = estado,
    clienteNombre = clienteNombre,
    servicioNombre = servicioNombre,
    precioCobradoCentavos = precioCobradoCentavos,
    referenciaCorteUrl = referenciaCorteUrl
)
