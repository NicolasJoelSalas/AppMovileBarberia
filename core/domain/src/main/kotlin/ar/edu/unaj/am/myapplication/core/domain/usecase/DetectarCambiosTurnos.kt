package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*

data class CambioTurnoLocal(val turnoId: String, val tipo: String)

fun detectarCancelacionesCliente(antes: Map<String, List<String?>>, despues: List<Turno>): List<CambioTurnoLocal> =
    despues.filter { turno ->
        val anterior = antes[turno.id]
        anterior != null && estadoTurno(anterior.getOrNull(5).orEmpty()) == EstadoTurno.PENDIENTE &&
            turno.estadoNormalizado == EstadoTurno.CANCELADO && turno.canceladoPor == turno.barberoId
    }.map { CambioTurnoLocal(it.id, "cancelacion_cliente") }

/** Excluye nombres auxiliares y datos contables que no forman parte de la reserva. */
fun firmaTurno(turno: Turno): List<String?> = listOf(
    turno.clienteId, turno.barberoId, turno.servicioId, turno.inicioMillis?.toString(),
    turno.finMillis?.toString(), estadoFirma(turno.estado), turno.referenciaCorteUrl
)

fun detectarCambiosTurnos(antes: Map<String, List<String?>>, despues: List<Turno>): List<CambioTurnoLocal> {
    val actuales = despues.associateBy { it.id }
    return despues.mapNotNull { turno ->
        val anterior = antes[turno.id]
        when {
            turno.estadoNormalizado == EstadoTurno.REALIZADO -> null
            turno.estadoNormalizado == EstadoTurno.CANCELADO && turno.canceladoPor == turno.barberoId -> null
            anterior == null -> CambioTurnoLocal(turno.id, "alta")
            normalizarFirma(anterior) == firmaTurno(turno) -> null
            turno.estadoNormalizado == EstadoTurno.CANCELADO -> CambioTurnoLocal(turno.id, "baja")
            else -> CambioTurnoLocal(turno.id, "modificacion")
        }
    } + antes.keys.filter { it !in actuales }.map { CambioTurnoLocal(it, "baja") }
}

/** Adapta también las firmas ya persistidas, sin generar avisos por un cambio de representación. */
private fun normalizarFirma(firma: List<String?>): List<String?> =
    firma.mapIndexed { indice, valor -> if (indice == 5 && valor != null) estadoFirma(valor) else valor }

private fun estadoFirma(valor: String): String = when (val estado = estadoTurno(valor)) {
    EstadoTurno.DESCONOCIDO -> valor.lowercase()
    else -> estado.name.lowercase()
}
