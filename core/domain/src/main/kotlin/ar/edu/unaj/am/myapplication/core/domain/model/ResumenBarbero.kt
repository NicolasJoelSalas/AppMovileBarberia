package ar.edu.unaj.am.myapplication.core.domain.model

import java.time.LocalDate

data class ResumenBarbero(
    val inicioSemana: LocalDate,
    val pendientes: List<Turno>,
    val realizados: List<Turno>,
    val gananciasCentavos: Long,
    val realizadosSinPrecio: Int
)

fun Turno.estaPendiente() = estado.lowercase() in listOf("confirmado", "pendiente")
fun Turno.estaRealizado() = estado.lowercase() in listOf("realizado", "completado")
