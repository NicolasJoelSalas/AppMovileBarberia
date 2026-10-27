package ar.edu.unaj.am.myapplication.core.domain.repository

interface TokenNotificacionesRepository {
    val instalacionId: String
    suspend fun obtenerToken(): String
    suspend fun eliminarToken()
}

interface PermisoNotificacionesRepository {
    val solicitado: Boolean
    fun registrarSolicitud()
}

interface EstadoAvisosRepository {
    val usuarioId: String?
    val comoCliente: Boolean
    fun iniciar(uid: String, comoCliente: Boolean)
    fun cerrar()
    fun obtenerFirmas(uid: String): Map<String, List<String?>>?
    fun guardarFirmas(uid: String, firmas: Map<String, List<String?>>)
}
