package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.DiaAtencion
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
        require(dia.franjas.isNotEmpty()) { "Configurá las franjas del día." }
        dia.franjas.forEach { franja ->
            val desde = LocalTime.parse(franja.inicio)
            val hasta = LocalTime.parse(franja.fin)
            val a = fecha.atTime(desde).atZone(zona)
            val b = fecha.atTime(hasta).atZone(zona)
            require(a.toLocalTime() == desde && b.toLocalTime() == hasta) { "La hora no existe en esta zona horaria." }
            val primero = Duration.between(inicio, a.toInstant()).toMinutes().toInt()
            val ultimo = Duration.between(inicio, b.toInstant()).toMinutes().toInt()
            require(primero >= 0 && ultimo <= minutos && ultimo - primero >= 5) { "La franja debe durar al menos 5 minutos." }
            for (i in primero until ultimo) {
                require(mascara[i] == '0') { "Las franjas se superponen." }
                mascara[i] = '1'
            }
        }
    }
    return OcupacionDia(inicio.toEpochMilli(), mascara.concatToString(), "0".repeat(minutos))
}
