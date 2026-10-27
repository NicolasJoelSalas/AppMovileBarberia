package ar.edu.unaj.am.myapplication.core.domain.usecase

import ar.edu.unaj.am.myapplication.core.domain.model.*
import java.time.*
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Reglas de agenda independientes del almacenamiento y de la pantalla. */
class PoliticaReservas @Inject constructor(private val reloj: Clock) {
    constructor() : this(Clock.system(ZoneId.of("America/Argentina/Buenos_Aires")))
    fun duracion(minutos: Int): Int {
        requireDomain(minutos in 5..240, ErrorReason.SERVICE_FIELDS)
        return minutos
    }
    fun validarEditable(estado: String, inicio: Long) {
        requireDomain(estadoTurno(estado) == EstadoTurno.PENDIENTE && inicio > reloj.millis(), ErrorReason.BOOKING_NOT_EDITABLE)
    }
    fun validarRealizable(estado: String, inicio: Long?) {
        requireDomain(estadoTurno(estado) == EstadoTurno.PENDIENTE, ErrorReason.APPOINTMENT_PENDING)
        requireDomain(inicio != null && inicio <= reloj.millis(), ErrorReason.APPOINTMENT_NOT_STARTED)
    }
    fun lunes(zona: ZoneId, offset: Int): LocalDate {
        requireDomain(offset in 0..1, ErrorReason.WEEK_SELECTION)
        return LocalDate.now(reloj.withZone(zona)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
    }
    fun validarSemana(fecha: LocalDate, zona: ZoneId) {
        requireDomain(fecha == lunes(zona,0) || fecha == lunes(zona,1), ErrorReason.SCHEDULE_WEEK_CHANGED)
    }
    fun semanaDe(inicio: Long, zona: ZoneId): LocalDate = Instant.ofEpochMilli(inicio).atZone(zona).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun offsetOriginal(inicio: Long, zona: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")): Int = if (semanaDe(inicio,zona) > lunes(zona,0)) 1 else 0
    fun cancelarAlPublicar(estado: String, inicio: Long, fin: Long, dia: OcupacionDia): Boolean =
        estadoTurno(estado) == EstadoTurno.PENDIENTE && inicio > reloj.millis() &&
            dia.indices(inicio,fin).any { dia.atencion[it] != '1' }
}
