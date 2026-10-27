package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.data.remote.ReservasApi
import com.google.android.gms.tasks.*
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.util.concurrent.Executor

class FirestoreReservaRepositoryTest {
    @Test fun `only an enabled remote barber is remembered for offline access`() = runTest {
        autenticar()
        `when`(auth.currentUser!!.uid).thenReturn("barbero")
        val collection = mock(CollectionReference::class.java)
        val reference = mock(DocumentReference::class.java)
        val profile = mock(DocumentSnapshot::class.java)
        `when`(firestore.collection("barberos")).thenReturn(collection)
        `when`(collection.document("barbero")).thenReturn(reference)
        `when`(profile.getBoolean("Estado")).thenReturn(true)
        val session = mock(ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal::class.java)
        val clientSession = mock(ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal::class.java)
        val ref = firestore.collection("barberos").document("barbero")
        `when`(profile.exists()).thenReturn(true)
        doReturn(completed(profile)).`when`(ref).get(Source.SERVER)
        val cached = FirestoreReservaRepository(firestore, auth, clientSession, session)
        assertTrue((cached.cargarCatalogo() as AppResult.Success).data.esBarbero)
        verify(session).guardar("barbero")
        verify(clientSession).limpiar("barbero")
        `when`(profile.getBoolean("Estado")).thenReturn(false)
        cached.cargarCatalogo()
        verify(session).limpiar("barbero")
        verify(session, times(1)).guardar("barbero")
        verify(clientSession, never()).guardar(anyString(), anyString())
    }
    @Test fun `offline barber role requires validation and does not hide permission denial`() = runTest {
        mockConstruction(android.util.SparseArray::class.java).use {
            val unavailable = FirebaseFirestoreException.Code.UNAVAILABLE
            val denied = FirebaseFirestoreException.Code.PERMISSION_DENIED
            val session = mock(ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal::class.java)
            val user = mock(FirebaseUser::class.java)
            `when`(auth.currentUser).thenReturn(user)
            `when`(user.uid).thenReturn("barbero")
            val barbers = mock(CollectionReference::class.java)
            val profile = mock(DocumentReference::class.java)
            `when`(firestore.collection("barberos")).thenReturn(barbers)
            `when`(barbers.document("barbero")).thenReturn(profile)
            val error = mock(FirebaseFirestoreException::class.java)
            `when`(error.code).thenReturn(unavailable)
            `when`(profile.get(Source.SERVER)).thenAnswer { throw error }
            val cached = FirestoreReservaRepository(firestore, auth, sesionBarberoLocal = session)
            assertTrue(cached.cargarCatalogo() is AppResult.Error)
            `when`(session.estaValidado("barbero")).thenReturn(true)
            val catalog = (cached.cargarCatalogo() as AppResult.Success).data
            assertTrue(catalog.esBarbero)
            assertTrue(catalog.sinConexion)
            `when`(error.code).thenReturn(denied)
            assertTrue(cached.cargarCatalogo() is AppResult.Error)
        }
    }
    @Test fun `offline catalog fallback requires a validated client and never hides permission errors`() = runTest {
        mockConstruction(android.util.SparseArray::class.java).use {
            val unavailable = FirebaseFirestoreException.Code.UNAVAILABLE
            val denied = FirebaseFirestoreException.Code.PERMISSION_DENIED
            val session = mock(ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal::class.java)
            val user = mock(FirebaseUser::class.java)
            `when`(auth.currentUser).thenReturn(user)
            `when`(user.uid).thenReturn("ana")
            val barbers = mock(CollectionReference::class.java)
            val profile = mock(DocumentReference::class.java)
            `when`(firestore.collection("barberos")).thenReturn(barbers)
            `when`(barbers.document("ana")).thenReturn(profile)
            val error = mock(FirebaseFirestoreException::class.java)
            `when`(error.code).thenReturn(unavailable)
            `when`(profile.get(Source.SERVER)).thenAnswer { throw error }
            val cached = FirestoreReservaRepository(firestore, auth, session)
            assertTrue(cached.cargarCatalogo() is AppResult.Error)
            `when`(session.estaValidado("ana")).thenReturn(true)
            `when`(session.nombre("ana")).thenReturn("Ana")
            val catalog = (cached.cargarCatalogo() as AppResult.Success).data
            assertTrue(catalog.sinConexion)
            assertFalse(catalog.esBarbero)
            assertEquals("Ana", catalog.nombreCliente)
            `when`(error.code).thenReturn(denied)
            assertTrue(cached.cargarCatalogo() is AppResult.Error)
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> completed(value: T): Task<T> {
        val task = mock(Task::class.java) as Task<T>
        `when`(task.isSuccessful).thenReturn(true)
        `when`(task.result).thenReturn(value)
        `when`(task.addOnCompleteListener(any(Executor::class.java), any<OnCompleteListener<T>>())).thenAnswer {
            it.getArgument<OnCompleteListener<T>>(1).onComplete(task); task
        }
        return task
    }
    private val firestore = mock(FirebaseFirestore::class.java)
    private val auth = mock(FirebaseAuth::class.java)
    private val llamadas = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var respuesta: Map<String, Any?> = mapOf("turnoId" to "solicitud")
    private var falla = false
    private val api = ReservasApi { operacion, datos ->
        llamadas += operacion to datos
        if (falla) error("No se confirmó la operación")
        respuesta
    }
    private val solicitud = SolicitudReserva("solicitud", "barbero", "corte", 1000, "Ana",
        finMillisEsperado = 4501000)
    private fun autenticar() {
        val user = mock(FirebaseUser::class.java)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("ana")
    }
    private fun repo(subir: (suspend (String) -> String)? = null) = FirestoreReservaRepository(
        firestore, auth, subirReferencia = subir, api = api)

    @Test fun `catalog includes all supported durations and excludes inactive or foreign services`() = runTest {
        autenticar()
        val barberos = mock(CollectionReference::class.java)
        val clientes = mock(CollectionReference::class.java)
        val servicios = mock(CollectionReference::class.java)
        val perfil = mock(DocumentReference::class.java)
        val cliente = mock(DocumentReference::class.java)
        val consulta = mock(Query::class.java)
        `when`(firestore.collection("barberos")).thenReturn(barberos)
        `when`(firestore.collection("clientes")).thenReturn(clientes)
        `when`(firestore.collection("servicios")).thenReturn(servicios)
        `when`(barberos.document("ana")).thenReturn(perfil)
        `when`(clientes.document("ana")).thenReturn(cliente)
        doReturn(completed(mock(DocumentSnapshot::class.java))).`when`(perfil).get(Source.SERVER)
        doReturn(completed(mock(DocumentSnapshot::class.java))).`when`(cliente).get(Source.SERVER)
        `when`(barberos.whereEqualTo("Estado", true)).thenReturn(consulta)
        val b = mock(DocumentSnapshot::class.java)
        `when`(b.id).thenReturn("barbero")
        val listaBarberos = mock(QuerySnapshot::class.java)
        `when`(listaBarberos.documents).thenReturn(listOf(b))
        doReturn(completed(listaBarberos)).`when`(consulta).get(Source.SERVER)
        fun servicio(id: String, minutos: Long, propietario: String = "barbero", activo: Boolean = true): DocumentSnapshot {
            val s = mock(DocumentSnapshot::class.java)
            `when`(s.id).thenReturn(id)
            `when`(s.getLong("Duracion")).thenReturn(minutos)
            `when`(s.getString("BarberoId")).thenReturn(propietario)
            `when`(s.get("Estado")).thenReturn(activo)
            return s
        }
        val lista = mock(QuerySnapshot::class.java)
        val documentos = listOf(servicio("60", 60), servicio("75", 75), servicio("90", 90),
            servicio("41", 41), servicio("ajeno", 60, "deshabilitado"), servicio("inactivo", 60, activo = false), servicio("largo", 241))
        `when`(lista.documents).thenReturn(documentos)
        doReturn(completed(lista)).`when`(servicios).get(Source.SERVER)
        val catalogo = (repo().cargarCatalogo() as AppResult.Success).data
        assertEquals(listOf(60, 75, 90, 41), catalogo.servicios.map { it.duracion })
    }

    @Test fun `reservation sends expected finish but never trusts a client UID or writes Firestore directly`() = runTest {
        autenticar()
        assertEquals("solicitud", (repo().reservar(solicitud) as AppResult.Success).data)
        val (operacion, datos) = llamadas.single()
        assertEquals("reservarTurno", operacion)
        assertEquals(4501000L, datos["finMillisEsperado"])
        assertEquals("solicitud", datos["solicitudId"])
        assertFalse(datos.containsKey("ClienteId"))
        verifyNoInteractions(firestore)
    }
    @Test fun `availability includes editing identity and preserves actual service duration`() = runTest {
        autenticar()
        respuesta = mapOf("inicioSemanaMillis" to 0L, "zonaHoraria" to "America/Argentina/Buenos_Aires",
            "horarios" to listOf(mapOf("inicioMillis" to 1000L, "finMillis" to 5401000L)))
        val semana = (repo().consultarSemana("barbero", "corte", 1, "mi-turno") as AppResult.Success).data
        assertEquals(90 * 60000L, semana.horarios.single().finMillis - semana.horarios.single().inicioMillis)
        assertEquals("mi-turno", llamadas.single().second["turnoId"])
        assertEquals(1, llamadas.single().second["semanaOffset"])
    }
    @Test fun `retry reuses request and uploaded reference after a failed confirmation`() = runTest {
        autenticar()
        var subidas = 0
        val repo = repo { subidas++; "https://res.cloudinary.com/demo/image/upload/foto.jpg" }
        val foto = solicitud.copy(referenciaLocal = "content://foto", cambiarReferencia = true)
        falla = true
        assertTrue(repo.reservar(foto) is AppResult.Error)
        falla = false
        assertTrue(repo.reservar(foto) is AppResult.Success)
        assertEquals(1, subidas)
        assertEquals(llamadas[0], llamadas[1])
    }
    @Test fun `modification and cancellation call the corresponding operation`() = runTest {
        autenticar()
        assertTrue(repo().modificar("turno", solicitud) is AppResult.Success)
        assertEquals("modificarTurno", llamadas.last().first)
        assertEquals("turno", llamadas.last().second["turnoId"])
        respuesta = mapOf("cancelado" to true)
        assertTrue(repo().cancelar("turno") is AppResult.Success)
        assertEquals("cancelarTurno", llamadas.last().first)
        verifyNoInteractions(firestore)
    }
    @Test fun `publication sends the selected week and all six days`() = runTest {
        autenticar()
        respuesta = mapOf("franjasPublicadas" to 6)
        val agenda = AgendaSemanal("America/Argentina/Buenos_Aires",
            List(6) { DiaAtencion(true, listOf(FranjaAtencion("09:00", "20:00"))) }, "2026-11-02")
        assertEquals(6, (repo().publicarSemana(agenda) as AppResult.Success).data)
        assertEquals("publicarAgenda", llamadas.single().first)
        assertEquals("2026-11-02", llamadas.single().second["inicioSemana"])
        assertEquals(6, (llamadas.single().second["agenda"] as Map<*, *>).size)
    }
    @Test fun `missing session cannot upload reference or call server`() = runTest {
        var subidas = 0
        val repo = repo { subidas++; "https://foto" }
        assertTrue(repo.reservar(solicitud.copy(referenciaLocal = "content://foto")) is AppResult.Error)
        assertEquals(0, subidas)
        assertTrue(llamadas.isEmpty())
    }
    @Test fun `malformed server responses never report success`() = runTest {
        autenticar()
        respuesta = emptyMap()
        assertTrue(repo().reservar(solicitud) is AppResult.Error)
        assertTrue(repo().cancelar("turno") is AppResult.Error)
        assertTrue(repo().consultarSemana("barbero", "corte", 0, null) is AppResult.Error)
    }
}
