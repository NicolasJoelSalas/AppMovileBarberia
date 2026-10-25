package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

import ar.edu.unaj.am.myapplication.core.database.dao.TurnoDao
import ar.edu.unaj.am.myapplication.core.database.mapper.toDomain
import ar.edu.unaj.am.myapplication.core.database.mapper.toEntity
import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class FirestoreTurnoRepository(
    private val turnoDao: TurnoDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : TurnoRepository {
    private val cacheMutex = Mutex()
    private val cacheVersions = mutableMapOf<String, Long>()

    /** Invalida escrituras pendientes antes de borrar los datos de la sesión. */
    suspend fun limpiarSesion(uid: String) = cacheMutex.withLock {
        cacheVersions[uid] = (cacheVersions[uid] ?: 0L) + 1
        turnoDao.eliminarPorCliente(uid)
        turnoDao.eliminarPorBarbero(uid)
    }

    override fun observeTurnos(comoCliente: Boolean) = channelFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            send(AppResult.Error(DomainError.Known(ErrorReason.APPOINTMENTS_SIGNIN)))
            return@channelFlow
        }
        val version = cacheMutex.withLock { cacheVersions[uid] ?: 0L }
        val errorSincronizacion = MutableStateFlow<DomainError?>(null)
        val local = launch {
            try {
                val guardados = if (comoCliente) turnoDao.observarPorCliente(uid) else turnoDao.observarPorBarbero(uid)
                guardados.combine(errorSincronizacion) { entities, error -> entities to error }
                    .collect { (entities, error) ->
                        if (auth.currentUser?.uid == uid) {
                            send(AppResult.Success(entities.map { it.toDomain() }))
                            if (error != null) send(AppResult.Error(error))
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(AppResult.Error(DomainError.Known(ErrorReason.APPOINTMENTS_CACHE)))
            }
        }
        try {
            observarRemotos(comoCliente).collect { result ->
                when (result) {
                    is AppResult.Success -> cacheMutex.withLock {
                        if ((cacheVersions[uid] ?: 0L) == version && auth.currentUser?.uid == uid) {
                            val guardados = if (comoCliente) turnoDao.observarPorCliente(uid).first()
                                else turnoDao.observarPorBarbero(uid).first()
                            val anteriores = guardados.associateBy { it.id }
                            // Guardar el turno antes de consultar documentos auxiliares por red.
                            val entities = result.data.map { turno -> turno.copy(
                                clienteNombre = anteriores[turno.id]?.takeIf { it.clienteId == turno.clienteId }?.clienteNombre.orEmpty(),
                                servicioNombre = anteriores[turno.id]?.takeIf { it.servicioId == turno.servicioId }?.servicioNombre.orEmpty()
                            ).toEntity() }
                            if (comoCliente) turnoDao.reemplazarPorCliente(uid, entities)
                            else turnoDao.reemplazarPorBarbero(uid, entities)
                        }
                    }
                    is AppResult.Error -> errorSincronizacion.value = result.error
                }
                if (result is AppResult.Success) {
                    val guardados = cacheMutex.withLock {
                        if ((cacheVersions[uid] ?: 0L) != version || auth.currentUser?.uid != uid) return@collect
                        if (comoCliente) turnoDao.observarPorCliente(uid).first() else turnoDao.observarPorBarbero(uid).first()
                    }
                    val (turnos, falloNombres) = completarNombres(guardados.map { it.toDomain() })
                    cacheMutex.withLock {
                        if ((cacheVersions[uid] ?: 0L) == version && auth.currentUser?.uid == uid) {
                            // No sobrescribir estados o importes nuevos con una lectura auxiliar más antigua.
                            turnoDao.actualizarNombres(turnos.map { it.toEntity() })
                            errorSincronizacion.value = if (falloNombres) DomainError.Known(ErrorReason.NAMES_SYNC) else null
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorSincronizacion.value = DomainError.Known(ErrorReason.APPOINTMENTS_SYNC)
            awaitCancellation()
        } finally {
            local.cancel()
        }
    }

    private fun observarRemotos(comoCliente: Boolean) = callbackFlow<AppResult<List<Turno>>> {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(AppResult.Error(DomainError.Known(ErrorReason.APPOINTMENTS_SIGNIN)))
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("turnos")
            .whereEqualTo(if (comoCliente) "ClienteId" else "BarberoId", uid)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
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
            // La caché parcial de Firebase no debe reemplazar la copia completa de Room.
            if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                if (snapshot?.metadata?.isFromCache == true) {
                    trySend(AppResult.Error(DomainError.Network))
                }
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
                        precioCobradoCentavos = importeCentavos(doc.get("PrecioCobrado")),
                        referenciaCorteUrl = doc.getString("ReferenciaCorteUrl"),
                        estado = when (val estado = doc.get("Estado")) {
                            is String -> estado.ifBlank { "Sin estado" }
                            is Boolean -> if (estado) "Confirmado" else "Cancelado"
                            else -> "Sin estado"
                        }
                    )
                }.sortedBy { it.inicioMillis ?: Long.MAX_VALUE }
                trySend(AppResult.Success(turnos))
            } catch (exception: RuntimeException) {
                trySend(AppResult.Error(DomainError.Known(ErrorReason.APPOINTMENT_INVALID_FIELDS)))
            }
        }
        awaitClose { registration.remove() }
    }

    private suspend fun completarNombres(turnos: List<Turno>): Pair<List<Turno>, Boolean> {
        var fallo = false
        suspend fun nombre(coleccion: String, id: String, campo: String): String? = try {
            firestore.collection(coleccion).document(id).readDocument().getString(campo).orEmpty()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { fallo = true; null }
        val clientes = turnos.map { it.clienteId }.filter { it.isNotBlank() }.distinct()
            .associateWith { nombre("clientes", it, "Nombre") }
        val servicios = turnos.map { it.servicioId }.filter { it.isNotBlank() }.distinct()
            .associateWith { nombre("servicios", it, "NombreCorte") }
        return turnos.map { it.copy(
            clienteNombre = clientes[it.clienteId] ?: it.clienteNombre,
            servicioNombre = servicios[it.servicioId] ?: it.servicioNombre
        ) } to fallo
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
            check(importeCentavos(precio) != null) { "El servicio necesita un precio num�rico v�lido." }
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
