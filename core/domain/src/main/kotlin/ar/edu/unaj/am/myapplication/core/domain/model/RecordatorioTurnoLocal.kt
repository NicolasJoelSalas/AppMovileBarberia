package ar.edu.unaj.am.myapplication.core.domain.model

data class RecordatorioTurnoLocal(val id: String, val owner: String, val inicio: Long,
    val pending: List<Long> = anticipacionesRecordatorio, val disabled: Boolean = false, val version: Int = 2)
