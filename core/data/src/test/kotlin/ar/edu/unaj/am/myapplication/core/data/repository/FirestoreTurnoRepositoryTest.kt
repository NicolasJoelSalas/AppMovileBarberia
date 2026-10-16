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
import com.google.firebase.firestore.DocumentReference
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
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
    private var clienteNombre: String? = "Nicolás Salas"
    private var servicioNombre: String? = "Corte de cabello"
    private lateinit var clienteReference: DocumentReference
    private lateinit var servicioReference: DocumentReference

    @Suppress("UNCHECKED_CAST")
    private fun relatedDocument(collectionName: String, id: String, field: String, name: String?): DocumentReference {
        val relatedCollection = mock(CollectionReference::class.java)
        val reference = mock(DocumentReference::class.java)
        val doc = mock(DocumentSnapshot::class.java)
        val task = mock(Task::class.java) as Task<DocumentSnapshot>
        `when`(firestore.collection(collectionName)).thenReturn(relatedCollection)
        `when`(relatedCollection.document(id)).thenReturn(reference)
        `when`(doc.getString(field)).thenReturn(name)
        `when`(reference.get()).thenReturn(task)
        `when`(task.isSuccessful).thenReturn(true)
        `when`(task.result).thenReturn(doc)
        `when`(task.addOnCompleteListener(ArgumentMatchers.any(Executor::class.java),
            ArgumentMatchers.any<OnCompleteListener<DocumentSnapshot>>())).thenAnswer {
            it.getArgument<OnCompleteListener<DocumentSnapshot>>(1).onComplete(task)
            task
        }
        return reference
    }

    private fun respond(snapshot: QuerySnapshot?, error: FirebaseFirestoreException? = null) {
        clienteReference = relatedDocument("clientes", "cliente", "Nombre", clienteNombre)
        servicioReference = relatedDocument("servicios", "corte", "NombreCorte", servicioNombre)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("barbero-autenticado")
        `when`(firestore.collection("turnos")).thenReturn(collection)
        `when`(collection.whereEqualTo("BarberoId", "barbero-autenticado")).thenReturn(query)
        `when`(collection.whereEqualTo("ClienteId", "barbero-autenticado")).thenReturn(query)
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
        assertEquals("Nicolás Salas", turnos.first().clienteNombre)
        assertEquals("Corte de cabello", turnos.first().servicioNombre)
        verify(clienteReference, times(1)).get()
        verify(servicioReference, times(1)).get()
        verify(collection).whereEqualTo("BarberoId", "barbero-autenticado")
        verify(registration).remove()
    }

    @Test
    fun `client queries only authenticated client appointments and resolves names`() = runTest {
        val doc = document("propio", 1000, "confirmado")
        `when`(doc.getString("ClienteId")).thenReturn("barbero-autenticado")
        val snapshot = mock(QuerySnapshot::class.java)
        `when`(snapshot.documents).thenReturn(listOf(doc))
        respond(snapshot)
        clienteReference = relatedDocument("clientes", "barbero-autenticado", "Nombre", "Cliente autenticado")
        val result = repository.observeTurnos(comoCliente = true).first() as AppResult.Success
        assertEquals("Cliente autenticado", result.data.single().clienteNombre)
        assertEquals("Corte de cabello", result.data.single().servicioNombre)
        verify(collection).whereEqualTo("ClienteId", "barbero-autenticado")
        verify(collection, never()).whereEqualTo("BarberoId", "barbero-autenticado")
        verify(registration).remove()
    }

    @Test
    fun `client cannot query without a session`() = runTest {
        assertTrue(repository.observeTurnos(comoCliente = true).first() is AppResult.Error)
        verifyNoInteractions(firestore)
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

    @Test
    fun `missing related names keep appointment visible without inventing data`() = runTest {
        clienteNombre = null
        servicioNombre = null
        val doc = document("sin-nombres", 1000, true)
        val snapshot = mock(QuerySnapshot::class.java)
        `when`(snapshot.documents).thenReturn(listOf(doc))
        respond(snapshot)
        val result = repository.observeTurnos().first() as AppResult.Success
        assertEquals(1, result.data.size)
        assertEquals("", result.data.first().clienteNombre)
        assertEquals("", result.data.first().servicioNombre)
    }

    @Test
    fun `blank related IDs never request an invalid document path`() = runTest {
        val doc = document("sin-referencias", 1000, true)
        `when`(doc.getString("ClienteId")).thenReturn("")
        `when`(doc.getString("ServicioId")).thenReturn("")
        val snapshot = mock(QuerySnapshot::class.java)
        `when`(snapshot.documents).thenReturn(listOf(doc))
        respond(snapshot)
        assertTrue(repository.observeTurnos().first() is AppResult.Success)
        verify(clienteReference, never()).get()
        verify(servicioReference, never()).get()
    }
}
