package ar.edu.unaj.am.myapplication.features.turnos.list

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import kotlin.math.abs

internal enum class SeccionTurnos { PENDIENTES, CANCELADOS, COMPLETADOS, OTROS }

internal fun seccionesTurnos(turnos: List<Turno>, ahoraMillis: Long): Map<SeccionTurnos, List<Turno>> {
    val grupos = turnos.groupBy {
        when (it.estado.lowercase()) {
            "pendiente", "confirmado", "true" -> SeccionTurnos.PENDIENTES
            "cancelado", "cancelada", "false" -> SeccionTurnos.CANCELADOS
            "realizado", "completado" -> SeccionTurnos.COMPLETADOS
            else -> SeccionTurnos.OTROS
        }
    }
    return SeccionTurnos.entries.mapNotNull { seccion ->
        grupos[seccion]?.let { items ->
            seccion to items.sortedWith(compareBy<Turno> {
                it.inicioMillis?.let { inicio -> abs(inicio - ahoraMillis) } ?: Long.MAX_VALUE
            }.thenBy { it.inicioMillis }.thenBy { it.id })
        }
    }.toMap()
}
