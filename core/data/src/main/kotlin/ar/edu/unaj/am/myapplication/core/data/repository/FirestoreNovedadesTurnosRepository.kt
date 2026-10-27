package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import ar.edu.unaj.am.myapplication.core.domain.repository.NovedadesTurnosRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreNovedadesTurnosRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : NovedadesTurnosRepository {
    private fun query(uid: String) = firestore.collection("turnos").whereEqualTo("BarberoId", uid)
    override suspend fun consultar(uid: String): List<Turno> {
        check(auth.currentUser?.uid == uid) { "La sesión cambió" }
        val snapshot = suspendCancellableCoroutine<QuerySnapshot> { continuation ->
            query(uid).get(Source.SERVER)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        check(auth.currentUser?.uid == uid) { "La sesión cambió" }
        check(!snapshot.metadata.hasPendingWrites()) { "Hay cambios pendientes de confirmar" }
        return mapear(snapshot)
    }

    override fun observar(uid: String) = callbackFlow<List<Turno>> {
        check(auth.currentUser?.uid == uid) { "La sesión cambió" }
        val listener = query(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            when {
                error != null -> close(error)
                auth.currentUser?.uid != uid -> close()
                snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites() -> {
                    try { trySend(mapear(snapshot)) } catch (e: Exception) { close(e) }
                }
            }
        }
        awaitClose { listener.remove() }
    }

    private fun mapear(snapshot: QuerySnapshot) = snapshot.documents.map { doc ->
        Turno(doc.id, doc.getString("ClienteId").orEmpty(), doc.getString("BarberoId").orEmpty(),
            doc.getString("ServicioId").orEmpty(), doc.getTimestamp("Inicio")?.toDate()?.time,
            doc.getTimestamp("Fin")?.toDate()?.time,
            when (val estado = doc.get("Estado")) {
                is Boolean -> if (estado) "confirmado" else "cancelado"
                is String -> estado
                else -> error("Estado de turno inválido")
            }, referenciaCorteUrl = doc.getString("ReferenciaCorteUrl"))
    }
}
