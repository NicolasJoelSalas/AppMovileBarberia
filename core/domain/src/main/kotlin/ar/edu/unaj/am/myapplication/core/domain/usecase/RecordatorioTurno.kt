package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*

fun instantesRecordatorios(turno: Turno, ahoraMillis: Long): List<Long> {
    if (turno.estadoNormalizado != EstadoTurno.PENDIENTE) return emptyList()
    val inicio = turno.inicioMillis ?: return emptyList()
    return anticipacionesRecordatorio.filter { inicio >= it }.map { inicio - it }.filter { it > ahoraMillis }
}
