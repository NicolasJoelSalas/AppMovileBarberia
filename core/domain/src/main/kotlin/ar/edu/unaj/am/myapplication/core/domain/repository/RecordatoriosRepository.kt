package ar.edu.unaj.am.myapplication.core.domain.repository

import ar.edu.unaj.am.myapplication.core.domain.model.RecordatorioTurnoLocal
import kotlinx.coroutines.flow.Flow

interface RecordatoriosRepository {
    fun listar(): List<RecordatorioTurnoLocal>
    fun guardar(recordatorio: RecordatorioTurnoLocal): Boolean
    fun eliminar(id: String)
    fun permisoSolicitado(uid: String?): Boolean
    fun registrarSolicitudPermiso(uid: String?)
    fun observarCambios(): Flow<Unit>
}
