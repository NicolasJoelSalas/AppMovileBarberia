package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import javax.inject.Inject

class EvaluarAccionesTurnoUseCase @Inject constructor(private val politica: PoliticaReservas) {
    constructor() : this(PoliticaReservas())

    operator fun invoke(turno: Turno, ahoraMillis: Long): AccionesTurno {
        val pendiente = turno.estadoNormalizado == EstadoTurno.PENDIENTE
        return AccionesTurno(
            editable = turno.id.isNotBlank() && politica.esEditable(turno.estado, turno.inicioMillis, ahoraMillis),
            completable = turno.id.isNotBlank() && politica.esRealizable(turno.estado, turno.inicioMillis, ahoraMillis),
            admiteFinalizacion = pendiente,
            admiteRecordatorios = pendiente,
            recordatorioHabilitado = instantesRecordatorios(turno, ahoraMillis).isNotEmpty(),
            permiteConsultarRuta = turno.estadoNormalizado != EstadoTurno.REALIZADO && turno.estadoNormalizado != EstadoTurno.CANCELADO
        )
    }
}
