package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import java.time.Duration

val anticipacionesRecordatorio: List<Long> = listOf(Duration.ofDays(1).toMillis(), Duration.ofHours(1).toMillis())

fun instantesRecordatorios(turno: Turno, ahoraMillis: Long): List<Long> {
    if (turno.estado.lowercase() !in setOf("confirmado", "pendiente")) return emptyList()
    val inicio = turno.inicioMillis ?: return emptyList()
    return anticipacionesRecordatorio.filter { inicio >= it }.map { inicio - it }.filter { it > ahoraMillis }
}

/** Instante absoluto: también funciona para turnos de otra semana o a medianoche. */
fun instanteRecordatorio(turno: Turno, ahoraMillis: Long): Long? {
    if (turno.estado.lowercase() !in setOf("confirmado", "pendiente")) return null
    val inicio = turno.inicioMillis ?: return null
    val unaHora = Duration.ofHours(1).toMillis()
    if (inicio < unaHora) return null
    return (inicio - unaHora).takeIf { it > ahoraMillis }
}
