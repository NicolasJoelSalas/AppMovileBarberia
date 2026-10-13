package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.PerfilRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestorePerfilRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : PerfilRepository {
    override suspend fun obtenerMiPerfil(): AppResult<PerfilUsuario> = resultado {
        val user = requireNotNull(auth.currentUser) { "Iniciá sesión nuevamente." }
        val barbero = firestore.collection("barberos").document(user.uid).get(Source.SERVER).awaitPerfil()
        val perfil = if (barbero.exists()) barbero else
            firestore.collection("clientes").document(user.uid).get(Source.SERVER).awaitPerfil()
        check(perfil.exists()) { "No se encontró tu perfil. Reintentá o iniciá sesión nuevamente." }
        check(auth.currentUser?.uid == user.uid) { "La sesión cambió. Iniciá sesión nuevamente." }
        PerfilUsuario(perfil.getString("Nombre").orEmpty(), user.email.orEmpty(), barbero.exists())
    }

    override suspend fun actualizarMiNombre(nombre: String): AppResult<Unit> = resultado {
        require(nombre.isNotBlank() && nombre.length <= 120) { "El nombre admite entre 1 y 120 caracteres." }
        val uid = requireNotNull(auth.currentUser?.uid) { "Iniciá sesión nuevamente." }
        val barbero = firestore.collection("barberos").document(uid)
        val cliente = firestore.collection("clientes").document(uid)
        firestore.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid) { "La sesión cambió. Iniciá sesión nuevamente." }
            val esBarbero = transaction.get(barbero).exists()
            val destino = if (esBarbero) barbero else cliente
            if (!esBarbero) check(transaction.get(cliente).exists()) { "No se encontró tu perfil." }
            transaction.update(destino, "Nombre", nombre.trim())
            Unit
        }.awaitPerfil()
    }

    private suspend fun <T> resultado(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val domainError = when {
            error is FirebaseNetworkException ||
                (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE) -> DomainError.Network
            error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                DomainError.Unknown("No tenés permiso para acceder o modificar este perfil.")
            error is IllegalStateException || error is IllegalArgumentException -> DomainError.Unknown(error.message)
            else -> DomainError.Unknown("No se pudo acceder a tu perfil. Reintentá.")
        }
        AppResult.Error(domainError)
    }
}

private suspend fun <T> Task<T>.awaitPerfil(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación incompleta."))
        }
    }
}
