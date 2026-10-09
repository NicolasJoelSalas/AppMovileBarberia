package ar.edu.unaj.am.myapplication.core.data.repository

import android.util.SparseArray

import ar.edu.unaj.am.myapplication.core.domain.model.AppResult
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*

class FirestoreTurnoRepositoryTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val auth = mock(FirebaseAuth::class.java)
    private val user = mock(FirebaseUser::class.java)
    private val collection = mock(CollectionReference::class.java)
    private val query = mock(Query::class.java)
    private val registration = mock(ListenerRegistration::class.java)
    private val repository = FirestoreTurnoRepository(firestore, auth)

    private fun respond(snapshot: QuerySnapshot?, error: FirebaseFirestoreException? = null) {
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("barbero-autenticado")
        `when`(firestore.collection("turnos")).thenReturn(collection)
        `when`(collection.whereEqualTo("BarberoId", "barbero-autenticado")).thenReturn(query)
        `when`(query.addSnapshotListener(ArgumentMatchers.any<EventListener<QuerySnapshot>>()))
            .thenAnswer { invocation ->
                invocation.getArgument<EventListener<QuerySnapshot>>(0).onEvent(snapshot, error)
                registration
            }
    }

    private fun document(id: String, seconds: Long, estado: Any): DocumentSnapshot {
        val doc = mock(DocumentSnapshot::class.java)
        `when`(doc.id).thenReturn(id)
        `when`(doc.getString("ClienteId")).thenReturn("cliente")
        `when`(doc.getString("BarberoId")).thenReturn("barbero-autenticado")
        `when`(doc.getString("ServicioId")).thenReturn("corte")
        `when`(doc.getTimestamp("Inicio")).thenReturn(Timestamp(seconds, 0))
        `when`(doc.getTimestamp("Fin")).thenReturn(Timestamp(seconds + 2400, 0))
        `when`(doc.get("Estado")).thenReturn(estado)
        return doc
    }

    @Test
    fun `filters by authenticated UID maps capitalized fields and releases listener`() = runTest {
        val snapshot = mock(QuerySnapshot::class.java)
        val documents = listOf(
            document("tarde", 4000, "confirmado"),
            document("temprano", 1000, true)
        )
        `when`(snapshot.documents).thenReturn(documents)
        respond(snapshot)
        val result = repository.observeTurnos().first()
        assertTrue(result is AppResult.Success)
        val turnos = (result as AppResult.Success).data
        assertEquals(listOf("temprano", "tarde"), turnos.map { it.id })
        assertEquals(1000000L, turnos.first().inicioMillis)
        assertEquals(2400000L, turnos.first().finMillis!! - turnos.first().inicioMillis!!)
        assertEquals("cliente", turnos.first().clienteId)
        assertEquals("corte", turnos.first().servicioId)
        assertEquals("Confirmado", turnos.first().estado)
        verify(collection).whereEqualTo("BarberoId", "barbero-autenticado")
        verify(registration).remove()
    }

    @Test
    fun `does not query without a session`() = runTest {
        assertTrue(repository.observeTurnos().first() is AppResult.Error)
        verifyNoInteractions(firestore)
    }

    @Test
    fun `permission denial is reported as error instead of an empty list`() = runTest {
        // Firestore inicializa su enum de errores con una SparseArray de Android.
        mockConstruction(SparseArray::class.java).use {
            val code = FirebaseFirestoreException.Code.PERMISSION_DENIED
            val error = mock(FirebaseFirestoreException::class.java)
            `when`(error.code).thenReturn(code)
            respond(null, error)
            assertTrue(repository.observeTurnos().first() is AppResult.Error)
            verify(registration).remove()
        }
    }

    @Test
    fun `empty collection is a successful empty list`() = runTest {
        val snapshot = mock(QuerySnapshot::class.java)
        `when`(snapshot.documents).thenReturn(emptyList())
        respond(snapshot)
        val result = repository.observeTurnos().first()
        assertEquals(emptyList<Any>(), (result as AppResult.Success).data)
    }
}
