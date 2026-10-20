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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreTurnoRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val serverOnly: Boolean = false
) : TurnoRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTurnos(comoCliente: Boolean) = callbackFlow<AppResult<List<Turno>>> {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(AppResult.Error(DomainError.Unknown("Iniciá sesión para consultar tus turnos.")))
            close()
            return@callbackFlow
        }
        val query = firestore.collection("turnos")
            .whereEqualTo(if (comoCliente) "ClienteId" else "BarberoId", uid)
        val listener = com.google.firebase.firestore.EventListener<com.google.firebase.firestore.QuerySnapshot> listener@{ snapshot, error ->
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
                return@listener
            }
            if (serverOnly && (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites())) {
                return@listener
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
                        precioCobradoCentavos = importeCentavos(doc.get("PrecioCobrado")),
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
        val registration = if (serverOnly) query.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, listener)
            else query.addSnapshotListener(listener)
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
                        servicioNombre = servicios[turno.servicioId]?.getString("NombreCorte").orEmpty()
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
    override suspend fun marcarRealizado(turnoId: String): AppResult<Unit> = try {
        val uid = auth.currentUser?.uid ?: error("Iniciá sesión nuevamente.")
        val referencia = firestore.collection("turnos").document(turnoId)
        val task = firestore.runTransaction { tx ->
            val doc = tx.get(referencia)
            val perfil = tx.get(firestore.collection("barberos").document(uid))
            check(perfil.getBoolean("Estado") == true && doc.getString("BarberoId") == uid) {
                "Solo podés completar tus propios turnos como barbero habilitado."
            }
            if (doc.get("Estado") == "realizado") return@runTransaction Unit
            check(doc.get("Estado") == true || doc.get("Estado") in
                listOf("confirmado", "Confirmado", "pendiente", "Pendiente")) {
                "El turno ya no está pendiente."
            }
            val inicio = doc.getTimestamp("Inicio")?.toDate()?.time
            check(inicio != null && inicio <= System.currentTimeMillis()) {
                "Todavía no llegó la hora del turno."
            }
            val servicioId = doc.getString("ServicioId").orEmpty()
            check(servicioId.isNotBlank()) { "El turno no tiene un servicio asignado." }
            val precio = tx.get(firestore.collection("servicios").document(servicioId)).get("Precio")
            check(importeCentavos(precio) != null) { "El servicio necesita un precio numérico válido." }
            tx.update(referencia, mapOf("Estado" to "realizado", "PrecioCobrado" to precio,
                "RealizadoEn" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            Unit
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            task.addOnCompleteListener(Executor { it.run() }) {
                if (continuation.isActive) {
                    if (it.isSuccessful) continuation.resume(Unit)
                    else continuation.resumeWithException(it.exception ?: IllegalStateException("No se pudo completar el turno."))
                }
            }
        }
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        AppResult.Error(DomainError.Unknown(if (e is FirebaseFirestoreException)
            "No se pudo completar el turno. Revisá la conexión, la hora y los permisos."
            else e.message ?: "No se pudo completar el turno."))
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

internal fun importeCentavos(value: Any?): Long? = try {
    if (value !is Number) null else java.math.BigDecimal(value.toString())
        .takeIf { it >= java.math.BigDecimal.ZERO && it <= java.math.BigDecimal("1000000000") }
        ?.setScale(2, java.math.RoundingMode.UNNECESSARY)?.movePointRight(2)?.longValueExact()
} catch (e: NumberFormatException) { null } catch (e: ArithmeticException) { null }
