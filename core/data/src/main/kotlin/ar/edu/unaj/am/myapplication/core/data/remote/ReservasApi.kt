package ar.edu.unaj.am.myapplication.core.data.remote

/** Frontera inyectable de las operaciones de reserva. */
fun interface ReservasApi {
    suspend fun ejecutar(operacion: String, datos: Map<String, Any?>): Map<String, Any?>
}
