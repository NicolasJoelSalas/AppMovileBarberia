package ar.edu.unaj.am.myapplication.core.domain.model

enum class EstadoTurno { PENDIENTE, CANCELADO, REALIZADO, DESCONOCIDO }

/** Normaliza también los valores históricos; la presentación no interpreta documentos remotos. */
fun estadoTurno(valor: String): EstadoTurno = when (valor.lowercase()) {
    "true", "confirmado", "pendiente" -> EstadoTurno.PENDIENTE
    "false", "cancelado", "cancelada" -> EstadoTurno.CANCELADO
    "realizado", "completado" -> EstadoTurno.REALIZADO
    else -> EstadoTurno.DESCONOCIDO
}

val Turno.estadoNormalizado get() = estadoTurno(estado)
