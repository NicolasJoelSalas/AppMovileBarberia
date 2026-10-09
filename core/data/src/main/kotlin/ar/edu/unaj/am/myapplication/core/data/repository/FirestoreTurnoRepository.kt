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

class FirestoreTurnoRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : TurnoRepository {
    override fun observeTurnos() = callbackFlow<AppResult<List<Turno>>> {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(AppResult.Error(DomainError.Unknown("Iniciá sesión para consultar tus turnos.")))
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("turnos")
            .whereEqualTo("BarberoId", uid)
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
    }
}

private fun com.google.firebase.firestore.QuerySnapshot?.orEmptyDocuments() =
    this?.documents.orEmpty()
