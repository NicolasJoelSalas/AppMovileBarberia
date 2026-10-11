package ar.edu.unaj.am.myapplication.core.domain.model

data class FranjaAtencion(val inicio: String, val fin: String)
data class DiaAtencion(val habilitado: Boolean, val franjas: List<FranjaAtencion>)
data class AgendaSemanal(val zonaHoraria: String, val dias: List<DiaAtencion>, val inicioSemana: String? = null)
