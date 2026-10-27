package ar.edu.unaj.am.myapplication.core.data.notifications

import android.content.Context
import ar.edu.unaj.am.myapplication.core.domain.repository.TokenNotificacionesRepository
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirebaseTokenNotificaciones @Inject constructor(
    @ApplicationContext context: Context,
    private val messaging: FirebaseMessaging
) : TokenNotificacionesRepository {
    private val preferences = context.getSharedPreferences("fcm_installation", Context.MODE_PRIVATE)
    override val instalacionId = preferences.getString("id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("id", it).apply()
    }
    override suspend fun obtenerToken(): String = suspendCancellableCoroutine { continuation ->
        messaging.token.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
    override suspend fun eliminarToken(): Unit = suspendCancellableCoroutine { continuation ->
        messaging.deleteToken().addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
