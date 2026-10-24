package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class FirestoreClienteRepositoryTest {
    @Test fun `previously validated barber can reopen without creating a client profile`() = runTest {
        val session = mock(ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal::class.java)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("barbero")
        `when`(session.estaValidado("barbero")).thenReturn(true)
        val cached = FirestoreClienteRepository(firestore, auth, sesionBarberoLocal = session)
        assertTrue(cached.ensureCurrentProfile() is AppResult.Success)
        verifyNoInteractions(firestore)
    }
    @Test fun `previously validated client can reopen without Firestore`() = runTest {
        val session = mock(ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal::class.java)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("uid-cliente")
        `when`(session.estaValidado("uid-cliente")).thenReturn(true)
        val cached = FirestoreClienteRepository(firestore, auth, session)
        assertTrue(cached.ensureCurrentProfile() is AppResult.Success)
        verifyNoInteractions(firestore)
    }
    private val firestore = mock(FirebaseFirestore::class.java)
    private val auth = mock(FirebaseAuth::class.java)
    private val user = mock(FirebaseUser::class.java)
    private val barbero = mock(DocumentReference::class.java)
    private val cliente = mock(DocumentReference::class.java)
    private val transaction = mock(Transaction::class.java)
    private val repository = FirestoreClienteRepository(firestore, auth)

    @Suppress("UNCHECKED_CAST")
    private fun configure(isBarbero: Boolean = false, exists: Boolean = false, success: Boolean = true) {
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("uid-cliente")
        `when`(user.email).thenReturn("cliente@example.com")
        `when`(user.displayName).thenReturn("Cliente Google")
        val barberos = mock(CollectionReference::class.java)
        val clientes = mock(CollectionReference::class.java)
        `when`(firestore.collection("barberos")).thenReturn(barberos)
        `when`(firestore.collection("clientes")).thenReturn(clientes)
        `when`(barberos.document("uid-cliente")).thenReturn(barbero)
        `when`(clientes.document("uid-cliente")).thenReturn(cliente)
        val barberDoc = mock(DocumentSnapshot::class.java)
        val clientDoc = mock(DocumentSnapshot::class.java)
        `when`(barberDoc.exists()).thenReturn(isBarbero)
        `when`(clientDoc.exists()).thenReturn(exists)
        `when`(transaction.get(barbero)).thenReturn(barberDoc)
        `when`(transaction.get(cliente)).thenReturn(clientDoc)
        val task = mock(Task::class.java) as Task<Unit>
        `when`(task.isSuccessful).thenReturn(success)
        `when`(firestore.runTransaction(any<Transaction.Function<Unit>>())).thenAnswer {
            if (success) it.getArgument<Transaction.Function<Unit>>(0).apply(transaction)
            task
        }
        `when`(task.addOnCompleteListener(any(Executor::class.java), any<OnCompleteListener<Unit>>()))
            .thenAnswer {
                it.getArgument<OnCompleteListener<Unit>>(1).onComplete(task)
                task
            }
    }

    @Test fun `new client uses authenticated UID and stores no password`() = runTest {
        configure()
        assertTrue(repository.ensureCurrentProfile() is AppResult.Success)
        val data = ArgumentCaptor.forClass(Any::class.java)
        verify(transaction).set(eq(cliente), data.capture())
        val fields = data.value as Map<*, *>
        assertEquals(setOf("Nombre", "Correo", "CreadoEn"), fields.keys)
        assertEquals("cliente@example.com", fields["Correo"])
        assertEquals("Cliente Google", fields["Nombre"])
        verify(firestore.collection("clientes")).document("uid-cliente")
    }

    @Test fun `existing client is never overwritten`() = runTest {
        configure(exists = true)
        assertTrue(repository.ensureCurrentProfile() is AppResult.Success)
        verify(transaction, never()).set(any(DocumentReference::class.java), any<Any>())
    }

    @Test fun `preloaded barber is never registered as a client`() = runTest {
        configure(isBarbero = true)
        assertTrue(repository.ensureCurrentProfile() is AppResult.Success)
        verify(transaction, never()).get(cliente)
        verify(transaction, never()).set(any(DocumentReference::class.java), any<Any>())
    }

    @Test fun `profile failure is returned for retry`() = runTest {
        configure(success = false)
        assertTrue(repository.ensureCurrentProfile() is AppResult.Error)
    }

    @Test fun `no session cannot create a client`() = runTest {
        assertTrue(repository.ensureCurrentProfile() is AppResult.Error)
        verifyNoInteractions(firestore)
    }
}
