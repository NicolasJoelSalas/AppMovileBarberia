package ar.edu.unaj.am.myapplication.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton

import ar.edu.unaj.am.myapplication.core.domain.repository.DispositivoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirestoreDispositivoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : DispositivoRepository {
    override suspend fun registrar(instalacionId: String, token: String) {
        val uid = auth.currentUser?.uid ?: return
        val barbero = firestore.collection("barberos").document(uid)
        if (barbero.get().esperarDispositivo().getBoolean("Estado") != true) return
        check(auth.currentUser?.uid == uid)
        barbero.collection("dispositivos").document(instalacionId).set(mapOf(
            "token" to token, "actualizadoEn" to FieldValue.serverTimestamp()
        )).esperarDispositivo()
    }

    override suspend fun desvincular(instalacionId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("barberos").document(uid).collection("dispositivos")
            .document(instalacionId).delete().esperarDispositivo()
    }
}

private suspend fun <T> Task<T>.esperarDispositivo(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}
