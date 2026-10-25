package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.ServicioRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** La baja es lógica: conserva los servicios usados por turnos anteriores. */
class FirestoreServicioRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ServicioRepository {
    private fun uid() = requireNotNull(auth.currentUser?.uid) { "Iniciá sesión nuevamente." }

    override suspend fun obtenerMisServicios(): AppResult<List<ServicioBarbero>> = resultado {
        val user = uid()
        firestore.collection("servicios").whereEqualTo("BarberoId", user).get(Source.SERVER).esperarServicio()
            .documents.map { doc ->
                ServicioBarbero(doc.id, doc.getString("NombreCorte").orEmpty(),
                    requireNotNull(importeCentavos(doc.get("Precio"))) { "Un servicio tiene un precio inválido." },
                    requireNotNull(doc.getLong("Duracion")).toInt(), doc.getBoolean("Estado") == true)
            }.sortedWith(compareByDescending<ServicioBarbero> { it.activo }.thenBy { it.nombre.lowercase() })
    }

    override suspend fun guardar(servicio: ServicioBarbero): AppResult<Unit> = resultado {
        val user = uid()
        val ref = if (servicio.id.isBlank()) firestore.collection("servicios").document()
            else firestore.collection("servicios").document(servicio.id)
        firestore.runTransaction { tx ->
            val perfil = tx.get(firestore.collection("barberos").document(user))
            val anterior = tx.get(ref)
            check(perfil.getBoolean("Estado") == true) { "Solo un barbero habilitado puede gestionar servicios." }
            check(!anterior.exists() || anterior.getString("BarberoId") == user) { "El servicio no te pertenece." }
            if (anterior.exists()) {
                tx.update(ref, mapOf("NombreCorte" to servicio.nombre, "Precio" to servicio.precioCentavos / 100.0,
                    "Duracion" to servicio.duracionMinutos))
            } else {
                tx.set(ref, mapOf("BarberoId" to user, "NombreCorte" to servicio.nombre,
                    "Precio" to servicio.precioCentavos / 100.0, "Duracion" to servicio.duracionMinutos,
                    "Estado" to true, "CreadoEn" to FieldValue.serverTimestamp()))
            }
            Unit
        }.esperarServicio()
    }

    override suspend fun cambiarEstado(id: String, activo: Boolean): AppResult<Unit> = resultado {
        val user = uid()
        firestore.runTransaction { tx ->
            val ref = firestore.collection("servicios").document(id)
            val servicio = tx.get(ref)
            val perfil = tx.get(firestore.collection("barberos").document(user))
            check(perfil.getBoolean("Estado") == true && servicio.getString("BarberoId") == user) {
                "Solo podés modificar tus propios servicios."
            }
            tx.update(ref, "Estado", activo)
            Unit
        }.esperarServicio()
    }

    private suspend fun <T> resultado(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        AppResult.Error(if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE)
            DomainError.Network else DomainError.Known(ErrorReason.SERVICE_OPERATION))
    }
}

private suspend fun <T> Task<T>.esperarServicio(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación fallida"))
        }
    }
}
