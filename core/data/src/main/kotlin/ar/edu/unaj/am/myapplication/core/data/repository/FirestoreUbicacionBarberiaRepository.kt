package ar.edu.unaj.am.myapplication.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.UbicacionBarberiaRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirestoreUbicacionBarberiaRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : UbicacionBarberiaRepository {
    override suspend fun obtenerMiUbicacion(): AppResult<Coordenadas?> = resultado {
        leer(uidActual())
    }

    override suspend fun obtenerUbicacion(barberoId: String): AppResult<Coordenadas?> = resultado {
        require(barberoId.isNotBlank() && '/' !in barberoId)
        leer(barberoId)
    }

    private suspend fun leer(barberoId: String): Coordenadas? {
        val uid = uidActual()
        val doc = firestore.collection("barberos").document(barberoId).get(Source.SERVER).awaitUbicacion()
        check(uidActual() == uid) { "La sesión cambió. Iniciá sesión nuevamente." }
        check(doc.exists() && doc.getBoolean("Estado") == true) { "La barbería no está disponible." }
        return doc.getGeoPoint("Ubicacion")?.let { Coordenadas(it.latitude, it.longitude) }
    }

    override suspend fun guardarMiUbicacion(ubicacion: Coordenadas): AppResult<Unit> = resultado {
        require(ubicacion.esValida) { "Seleccioná un punto válido en el mapa." }
        val uid = uidActual()
        val ref = firestore.collection("barberos").document(uid)
        // La transacción exige conexión y confirma el guardado en el servidor.
        firestore.runTransaction { tx ->
            check(uidActual() == uid) { "La sesión cambió. Iniciá sesión nuevamente." }
            val doc = tx.get(ref)
            check(doc.exists() && doc.getBoolean("Estado") == true) { "Solo un barbero habilitado puede guardar la ubicación." }
            tx.update(ref, mapOf("Ubicacion" to GeoPoint(ubicacion.latitud, ubicacion.longitud),
                "UbicacionActualizadaEn" to FieldValue.serverTimestamp()))
            Unit
        }.awaitUbicacion()
    }

    private fun uidActual() = checkNotNull(auth.currentUser?.uid) { "Iniciá sesión nuevamente." }

    private suspend fun <T> resultado(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Error(when {
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> DomainError.Network
            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                DomainError.Known(ErrorReason.LOCATION_PERMISSION)
            e is IllegalArgumentException || e is IllegalStateException -> DomainError.Known(ErrorReason.LOCATION_LOAD)
            else -> DomainError.Known(ErrorReason.LOCATION_LOAD)
        })
    }
}

private suspend fun <T> Task<T>.awaitUbicacion(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación incompleta."))
        }
    }
}
