package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.Turno

data class CambioTurnoLocal(val turnoId: String, val tipo: String)

/** Excluye nombres auxiliares y datos contables que no forman parte de la reserva. */
fun firmaTurno(turno: Turno): List<String?> = listOf(
    turno.clienteId, turno.barberoId, turno.servicioId, turno.inicioMillis?.toString(),
    turno.finMillis?.toString(), turno.estado.lowercase(), turno.referenciaCorteUrl
)

fun detectarCambiosTurnos(antes: Map<String, List<String?>>, despues: List<Turno>): List<CambioTurnoLocal> {
    val actuales = despues.associateBy { it.id }
    return despues.mapNotNull { turno ->
        val anterior = antes[turno.id]
        when {
            anterior == null -> CambioTurnoLocal(turno.id, "alta")
            anterior == firmaTurno(turno) -> null
            turno.estado.equals("cancelado", true) -> CambioTurnoLocal(turno.id, "baja")
            else -> CambioTurnoLocal(turno.id, "modificacion")
        }
    } + antes.keys.filter { it !in actuales }.map { CambioTurnoLocal(it, "baja") }
}
