package ar.edu.unaj.am.myapplication.core.auth.repository

import ar.edu.unaj.am.myapplication.core.domain.model.ErrorReason

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
                                DomainError.Known(ErrorReason.LOGIN_FAILED)
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
                                DomainError.Known(ErrorReason.GOOGLE_SIGNIN)
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
                            DomainError.Known(ErrorReason.EMAIL_EXISTS)
                        )
                        task.exception is FirebaseAuthWeakPasswordException -> AppResult.Error(
                            DomainError.Known(ErrorReason.WEAK_PASSWORD)
                        )
                        task.exception is FirebaseAuthInvalidCredentialsException -> AppResult.Error(
                            DomainError.Known(ErrorReason.INVALID_EMAIL)
                        )
                        task.exception is FirebaseNetworkException -> AppResult.Error(DomainError.Network)
                        else -> AppResult.Error(
                            DomainError.Known(ErrorReason.REGISTER_FAILED)
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
