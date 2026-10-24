package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal
import ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.ClienteRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class FirestoreClienteRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val sesionLocal: SesionClienteLocal? = null,
    private val sesionBarberoLocal: SesionBarberoLocal? = null
) : ClienteRepository {
    override suspend fun ensureCurrentProfile(): AppResult<Unit> {
        val user = auth.currentUser
            ?: return AppResult.Error(DomainError.Unknown("Iniciá sesión para completar tu perfil."))
        // Permite consultar Room al reiniciar sin red una sesión ya validada.
        if (sesionLocal?.estaValidado(user.uid) == true || sesionBarberoLocal?.estaValidado(user.uid) == true)
            return AppResult.Success(Unit)
        val email = user.email
            ?: return AppResult.Error(DomainError.Unknown("Tu cuenta no tiene un correo disponible."))
        val barbero = firestore.collection("barberos").document(user.uid)
        val cliente = firestore.collection("clientes").document(user.uid)
        return suspendCancellableCoroutine { continuation ->
            // La transacción evita sobrescribir un perfil creado por otro ingreso simultáneo.
            firestore.runTransaction { transaction ->
                if (!transaction.get(barbero).exists() && !transaction.get(cliente).exists()) {
                    transaction.set(cliente, mapOf(
                        "Nombre" to user.displayName.orEmpty().take(120),
                        "Correo" to email,
                        "CreadoEn" to FieldValue.serverTimestamp()
                    ))
                }
                Unit
            }.addOnCompleteListener(Executor { it.run() }) { task ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (task.isSuccessful) AppResult.Success(Unit)
                        else AppResult.Error(DomainError.Unknown(
                            "Tu cuenta está autenticada, pero no se pudo completar tu perfil. " +
                                "Revisá tu conexión y reintentá. Si continúa, revisá las reglas de Firestore."
                        ))
                    )
                }
            }
        }
    }
}
