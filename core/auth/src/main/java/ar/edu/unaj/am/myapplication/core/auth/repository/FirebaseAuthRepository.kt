package ar.edu.unaj.am.myapplication.core.auth.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import ar.edu.unaj.am.myapplication.core.domain.model.DomainError
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override suspend fun login(
        email: String,
        password: String
    ): AppResult<Unit> = suspendCancellableCoroutine { continuation ->
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (continuation.isActive) {
                    val result: AppResult<Unit> = when {
                        task.isSuccessful ->
                            AppResult.Success(Unit)

                        task.exception is FirebaseNetworkException ->
                            AppResult.Error(DomainError.Network)

                        else ->
                            AppResult.Error(
                                DomainError.Unknown(
                                    "No se pudo iniciar sesión. " +
                                        "Revisá tus credenciales e intentá nuevamente."
                                )
                            )
                    }

                    continuation.resume(result)
                }
            }
    }

    override suspend fun loginWithGoogle(idToken: String): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (continuation.isActive) {
                    continuation.resume(
                        when {
                            task.isSuccessful -> AppResult.Success(Unit)
                            task.exception is FirebaseNetworkException ->
                                AppResult.Error(DomainError.Network)
                            else -> AppResult.Error(
                                DomainError.Unknown("No se pudo ingresar con Google. Intentá nuevamente.")
                            )
                        }
                    )
                }
            }
        }

    override suspend fun register(email: String, password: String): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            // Firebase checks uniqueness during creation, including concurrent requests.
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                if (continuation.isActive) {
                    val result: AppResult<Unit> = when {
                        task.isSuccessful -> AppResult.Success(Unit)
                        task.exception is FirebaseAuthUserCollisionException -> AppResult.Error(
                            DomainError.Unknown("Ya existe una cuenta con ese correo. Iniciá sesión.")
                        )
                        task.exception is FirebaseAuthWeakPasswordException -> AppResult.Error(
                            DomainError.Unknown("La contraseña no cumple los requisitos. Elegí una más segura.")
                        )
                        task.exception is FirebaseAuthInvalidCredentialsException -> AppResult.Error(
                            DomainError.Unknown("Ingresá un correo electrónico válido.")
                        )
                        task.exception is FirebaseNetworkException -> AppResult.Error(DomainError.Network)
                        else -> AppResult.Error(
                            DomainError.Unknown("No se pudo crear la cuenta. Intentá nuevamente.")
                        )
                    }
                    continuation.resume(result)
                }
            }
        }

    override fun isLoggedIn(): Boolean =
        auth.currentUser != null

    override fun logout() {
        auth.signOut()
    }
}
