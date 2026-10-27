package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.model.OcupacionDia
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Traduce las franjas del barbero a minutos de atención, respetando su zona horaria. */
fun crearDiaAtencion(fecha: LocalDate, zona: ZoneId, dia: DiaAtencion): OcupacionDia {
    val inicio = fecha.atStartOfDay(zona).toInstant()
    val fin = fecha.plusDays(1).atStartOfDay(zona).toInstant()
    val minutos = Duration.between(inicio, fin).toMinutes().toInt()
    val mascara = CharArray(minutos) { '0' }
    if (dia.habilitado) {
        requireDomain(dia.franjas.isNotEmpty(), ErrorReason.SCHEDULE_EMPTY_DAY)
        dia.franjas.forEach { franja ->
            val desde = LocalTime.parse(franja.inicio)
            val hasta = LocalTime.parse(franja.fin)
            val a = fecha.atTime(desde).atZone(zona)
            val b = fecha.atTime(hasta).atZone(zona)
            requireDomain(a.toLocalTime() == desde && b.toLocalTime() == hasta, ErrorReason.SCHEDULE_TIME_FORMAT)
            val primero = Duration.between(inicio, a.toInstant()).toMinutes().toInt()
            val ultimo = Duration.between(inicio, b.toInstant()).toMinutes().toInt()
            requireDomain(primero >= 0 && ultimo <= minutos && ultimo - primero >= 5, ErrorReason.SCHEDULE_DURATION)
            for (i in primero until ultimo) {
                requireDomain(mascara[i] == '0', ErrorReason.SCHEDULE_OVERLAP)
                mascara[i] = '1'
            }
        }
    }
    return OcupacionDia(inicio.toEpochMilli(), mascara.concatToString(), "0".repeat(minutos))
}
