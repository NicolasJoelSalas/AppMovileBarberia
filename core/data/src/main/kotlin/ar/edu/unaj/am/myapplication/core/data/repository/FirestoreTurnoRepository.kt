package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import java.util.concurrent.Executor
import java.math.BigDecimal
import java.math.RoundingMode
import com.google.firebase.firestore.FieldValue
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreTurnoRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : TurnoRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTurnos(comoCliente: Boolean) = callbackFlow<AppResult<List<Turno>>> {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(AppResult.Error(DomainError.Unknown("Iniciá sesión para consultar tus turnos.")))
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("turnos")
            .whereEqualTo(if (comoCliente) "ClienteId" else "BarberoId", uid)
            .addSnapshotListener { snapshot, error ->
            if (error != null) {
                val message = when (error.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "No tenés permiso para consultar los turnos. Revisá las reglas de Firestore."
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        "Iniciá sesión nuevamente para consultar los turnos."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "No se pudo conectar con Firestore. Revisá tu conexión."
                    else -> "No se pudieron cargar los turnos."
                }
                trySend(AppResult.Error(DomainError.Unknown(message)))
                return@addSnapshotListener
            }
            try {
                val turnos = snapshot.orEmptyDocuments().map { doc ->
                    Turno(
                        id = doc.id,
                        clienteId = doc.getString("ClienteId").orEmpty(),
                        barberoId = doc.getString("BarberoId").orEmpty(),
                        servicioId = doc.getString("ServicioId").orEmpty(),
                        inicioMillis = doc.getTimestamp("Inicio")?.toDate()?.time,
                        finMillis = doc.getTimestamp("Fin")?.toDate()?.time,
                        precioCobradoCentavos = precioCentavos(doc.get("PrecioCobrado")),
                        estado = when (val estado = doc.get("Estado")) {
                            is String -> estado.ifBlank { "Sin estado" }
                            is Boolean -> if (estado) "Confirmado" else "Cancelado"
                            else -> "Sin estado"
                        }
                    )
                }.sortedBy { it.inicioMillis ?: Long.MAX_VALUE }
                trySend(AppResult.Success(turnos))
            } catch (exception: RuntimeException) {
                trySend(AppResult.Error(DomainError.Unknown(
                    "Hay un turno con campos de tipo incorrecto. Inicio y Fin deben ser timestamp; los identificadores deben ser texto."
                )))
            }
        }
        awaitClose { registration.remove() }
    }.mapLatest { result ->
        when (result) {
            is AppResult.Error -> result
            is AppResult.Success -> try {
                // Una lectura por ID en cada actualización, aunque haya varios turnos del mismo cliente.
                val clientes = result.data.map { it.clienteId }.filter { it.isNotBlank() }.distinct()
                    .associateWith { id ->
                        firestore.collection("clientes").document(id).readDocument()
                            .getString("Nombre").orEmpty()
                    }
                val servicios = result.data.map { it.servicioId }.filter { it.isNotBlank() }.distinct()
                    .associateWith { id ->
                        firestore.collection("servicios").document(id).readDocument()
                    }
                AppResult.Success(result.data.map { turno ->
                    turno.copy(
                        clienteNombre = clientes[turno.clienteId].orEmpty(),
                        servicioNombre = servicios[turno.servicioId]?.getString("NombreCorte").orEmpty(),
                        precioServicioCentavos = precioCentavos(servicios[turno.servicioId]?.get("Precio"))
                    )
                })
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                AppResult.Error(DomainError.Unknown(
                    "No se pudieron cargar los nombres de clientes o servicios. " +
                        "Revisá la conexión, los campos Nombre y NombreCorte y las reglas de Firestore."
                ))
            }
        }
    }

    override suspend fun marcarRealizado(turnoId: String, precioEsperadoCentavos: Long): AppResult<Unit> = try {
        val uid = auth.currentUser?.uid ?: error("Iniciá sesión nuevamente.")
        val referencia = firestore.collection("turnos").document(turnoId)
        firestore.runTransaction { tx ->
            val doc = tx.get(referencia)
            val perfil = tx.get(firestore.collection("barberos").document(uid))
            check(perfil.getBoolean("Estado") == true && doc.getString("BarberoId") == uid) {
                "Solo podés completar los turnos de tu sesión de barbero."
            }
            val estado = doc.get("Estado")
            if (estado is String && estado.lowercase() in listOf("realizado", "completado")) {
                return@runTransaction Unit
            }
            check(estado == true || estado in listOf("confirmado", "Confirmado", "pendiente", "Pendiente")) {
                "El turno ya no está pendiente."
            }
            val fin = doc.getTimestamp("Fin")?.toDate()?.time
            check(fin != null && fin <= System.currentTimeMillis()) { "El horario del turno todavía no terminó." }
            val servicioId = doc.getString("ServicioId").orEmpty()
            check(servicioId.isNotBlank()) { "El turno no tiene un servicio asignado." }
            val servicio = tx.get(firestore.collection("servicios").document(servicioId))
            val precio = servicio.get("Precio")
            check(precioCentavos(precio) != null) { "Configurá Precio como número válido en el servicio." }
            check(precioCentavos(precio) == precioEsperadoCentavos) {
                "El precio del servicio cambió. Actualizá el resumen y confirmá el nuevo importe."
            }
            tx.update(referencia, mapOf("Estado" to "realizado", "PrecioCobrado" to precio,
                "RealizadoEn" to FieldValue.serverTimestamp()))
            Unit
        }.esperarTurno()
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        AppResult.Error(DomainError.Unknown(
            if (e is FirebaseFirestoreException) "No se pudo completar el turno. Revisá la conexión y las reglas de Firestore."
            else e.message ?: "No se pudo completar el turno."))
    }
}

internal fun precioCentavos(value: Any?): Long? = try {
    if (value !is Number) null else BigDecimal(value.toString())
        .takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("1000000000") }
        ?.setScale(2, RoundingMode.UNNECESSARY)?.movePointRight(2)?.longValueExact()
} catch (e: NumberFormatException) { null } catch (e: ArithmeticException) { null }

private suspend fun <T> Task<T>.esperarTurno(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación fallida"))
        }
    }
}

private suspend fun DocumentReference.readDocument(): DocumentSnapshot =
    suspendCancellableCoroutine { continuation ->
        get().addOnCompleteListener(Executor { it.run() }) { task ->
            if (continuation.isActive) {
                if (task.isSuccessful) continuation.resume(task.result)
                else continuation.resumeWithException(task.exception ?: IllegalStateException("Lectura fallida"))
            }
        }
    }

private fun com.google.firebase.firestore.QuerySnapshot?.orEmptyDocuments() =
    this?.documents.orEmpty()
