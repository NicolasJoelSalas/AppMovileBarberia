package ar.edu.unaj.am.myapplication.core.auth.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

class FirebaseAuthRepositoryTest {
    private val auth = mock(FirebaseAuth::class.java)
    private val repo = FirebaseAuthRepository(auth)

    @Suppress("UNCHECKED_CAST")
    private fun respuesta(error: Exception? = null): Task<AuthResult> {
        val tarea = mock(Task::class.java) as Task<AuthResult>
        `when`(tarea.isSuccessful).thenReturn(error == null)
        `when`(tarea.exception).thenReturn(error)
        `when`(tarea.addOnCompleteListener(any())).thenAnswer {
            it.getArgument<OnCompleteListener<AuthResult>>(0).onComplete(tarea); tarea
        }
        return tarea
    }
    @Test fun `email login reports success network failure and rejected credentials`() = runTest {
        `when`(auth.signInWithEmailAndPassword("ana@example.com", "clave")).thenAnswer { respuesta() }
        assertEquals(AppResult.Success(Unit), repo.login("ana@example.com", "clave"))
        `when`(auth.signInWithEmailAndPassword("ana@example.com", "clave"))
            .thenAnswer { respuesta(mock(FirebaseNetworkException::class.java)) }
        assertEquals(AppResult.Error(DomainError.Network), repo.login("ana@example.com", "clave"))
        `when`(auth.signInWithEmailAndPassword("ana@example.com", "clave"))
            .thenAnswer { respuesta(mock(FirebaseAuthInvalidCredentialsException::class.java)) }
        assertEquals(AppResult.Error(DomainError.Known(ErrorReason.LOGIN_FAILED)), repo.login("ana@example.com", "clave"))
    }
    @Test fun `registration preserves Firebase validation errors`() = runTest {
        for ((tipo, motivo) in listOf(
            FirebaseAuthUserCollisionException::class.java to ErrorReason.EMAIL_EXISTS,
            FirebaseAuthWeakPasswordException::class.java to ErrorReason.WEAK_PASSWORD,
            FirebaseAuthInvalidCredentialsException::class.java to ErrorReason.INVALID_EMAIL
        )) {
            `when`(auth.createUserWithEmailAndPassword("ana@example.com", "clave")).thenAnswer { respuesta(mock(tipo)) }
            assertEquals(AppResult.Error(DomainError.Known(motivo)), repo.register("ana@example.com", "clave"))
        }
        `when`(auth.createUserWithEmailAndPassword("ana@example.com", "clave")).thenAnswer { respuesta() }
        assertEquals(AppResult.Success(Unit), repo.register("ana@example.com", "clave"))
    }
    @Test fun `Google token is exchanged for Firebase credential`() = runTest {
        mockStatic(GoogleAuthProvider::class.java).use { proveedor ->
            val credencial = mock(AuthCredential::class.java)
            proveedor.`when`<AuthCredential> { GoogleAuthProvider.getCredential("id-token", null) }.thenReturn(credencial)
            `when`(auth.signInWithCredential(credencial)).thenAnswer { respuesta() }
            assertEquals(AppResult.Success(Unit), repo.loginWithGoogle("id-token"))
            verify(auth).signInWithCredential(credencial)
        }
    }
    @Test fun `session follows current Firebase user and logout signs out`() {
        val sesion = FirebaseSesionActual(auth)
        assertFalse(repo.isLoggedIn())
        assertNull(sesion.usuarioId())
        val user = mock(FirebaseUser::class.java)
        `when`(user.uid).thenReturn("ana")
        `when`(auth.currentUser).thenReturn(user)
        assertTrue(repo.isLoggedIn())
        assertEquals("ana", sesion.usuarioId())
        repo.logout()
        verify(auth).signOut()
    }
}
