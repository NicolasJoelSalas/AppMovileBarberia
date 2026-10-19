package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import com.google.android.gms.tasks.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.util.Date
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.Executor

class FirestoreReservaRepositoryTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val auth = mock(FirebaseAuth::class.java)
    private val tx = mock(Transaction::class.java)
    private val refs = mutableMapOf<String, DocumentReference>()
    private val docs = mutableMapOf<String, DocumentSnapshot>()
    private val start = System.currentTimeMillis() + 86400000
    private val slotId = "barbero_$start"
    private val request = SolicitudReserva("solicitud", "barbero", "corte", start, "Ana")
    private val repo = FirestoreReservaRepository(firestore, auth)

    private fun collection(path: String): CollectionReference {
        val collection = mock(CollectionReference::class.java)
        `when`(collection.document(anyString())).thenAnswer { call ->
            val id = call.getArgument<String>(0)
            refs.getOrPut("$path/$id") {
                val ref = mock(DocumentReference::class.java)
                `when`(ref.id).thenReturn(id)
                `when`(ref.collection(anyString())).thenAnswer { collection("$path/$id/${it.getArgument<String>(0)}") }
                ref
            }
        }
        return collection
    }
    @Suppress("UNCHECKED_CAST")
    private fun setup(occupied: Boolean = false, existing: Boolean = false) {
        val user = mock(FirebaseUser::class.java)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenReturn("ana")
        `when`(firestore.collection(anyString())).thenAnswer { collection(it.getArgument(0)) }
        for (path in listOf("turnos/solicitud", "horarios/$slotId", "clientes/ana", "barberos/barbero",
            "servicios/corte", "barberos/barbero/agenda/semanal")) {
            docs[path] = mock(DocumentSnapshot::class.java)
        }
        `when`(tx.get(any(DocumentReference::class.java))).thenAnswer { call ->
            val path = refs.entries.single { it.value === call.getArgument<DocumentReference>(0) }.key
            docs.getValue(path)
        }
        `when`(docs.getValue("turnos/solicitud").exists()).thenReturn(existing)
        if (existing) {
            val previous = docs.getValue("turnos/solicitud")
            `when`(previous.getString("ClienteId")).thenReturn("ana")
            `when`(previous.getString("BarberoId")).thenReturn("barbero")
            `when`(previous.getString("ServicioId")).thenReturn("corte")
            `when`(previous.getString("HorarioId")).thenReturn(slotId)
        }
        val slot = docs.getValue("horarios/$slotId")
        `when`(slot.exists()).thenReturn(true)
        `when`(slot.getString("Estado")).thenReturn(if (occupied) "ocupado" else "libre")
        `when`(slot.getTimestamp("Inicio")).thenReturn(Timestamp(Date(start)))
        `when`(slot.getTimestamp("Fin")).thenReturn(Timestamp(Date(start + 2400000)))
        `when`(slot.get("Agenda")).thenReturn(emptyMap<String, Any>())
        `when`(slot.getString("ZonaHoraria")).thenReturn("America/Argentina/Buenos_Aires")
        `when`(docs.getValue("clientes/ana").exists()).thenReturn(true)
        `when`(docs.getValue("clientes/ana").getString("Nombre")).thenReturn("")
        `when`(docs.getValue("barberos/barbero").getBoolean("Estado")).thenReturn(true)
        `when`(docs.getValue("barberos/barbero").getString("ZonaHoraria")).thenReturn("America/Argentina/Buenos_Aires")
        `when`(docs.getValue("servicios/corte").get("Activo")).thenReturn(true)
        `when`(docs.getValue("servicios/corte").getLong("Duracion")).thenReturn(40L)
        `when`(docs.getValue("barberos/barbero/agenda/semanal").data).thenReturn(emptyMap())
        `when`(firestore.runTransaction(any<Transaction.Function<Any?>>())).thenAnswer { call ->
            val value = call.getArgument<Transaction.Function<Any?>>(0).apply(tx)
            val task = mock(Task::class.java) as Task<Any?>
            `when`(task.isSuccessful).thenReturn(true)
            `when`(task.result).thenReturn(value)
            `when`(task.addOnCompleteListener(any(Executor::class.java), any<OnCompleteListener<Any?>>())).thenAnswer {
                it.getArgument<OnCompleteListener<Any?>>(1).onComplete(task)
                task
            }
            task
        }
    }

    private fun editableBooking() {
        setup(occupied = true, existing = true)
        val turno = docs.getValue("turnos/solicitud")
        `when`(turno.getString("Estado")).thenReturn("confirmado")
        `when`(turno.getTimestamp("Inicio")).thenReturn(Timestamp(Date(start)))
        `when`(turno.get("CreadoEn")).thenReturn(Timestamp(Date(start - 86400000)))
        val slot = docs.getValue("horarios/$slotId")
        `when`(slot.getString("TurnoId")).thenReturn("solicitud")
        `when`(slot.getString("BarberoId")).thenReturn("barbero")
    }
    @Test fun `cancellation frees slot and preserves booking as cancelled`() = runTest {
        editableBooking()
        assertTrue(repo.cancelar("solicitud") is AppResult.Success)
        @Suppress("UNCHECKED_CAST")
        val fields = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
        verify(tx).update(eq(refs.getValue("horarios/$slotId")), fields.capture())
        assertEquals("libre", (fields.value as Map<*, *>)["Estado"])
        verify(tx).update(eq(refs.getValue("turnos/solicitud")), fields.capture())
        assertEquals("cancelado", (fields.value as Map<*, *>)["Estado"])
    }
    @Test fun `cancellation rejects another client without writes`() = runTest {
        editableBooking()
        `when`(docs.getValue("turnos/solicitud").getString("ClienteId")).thenReturn("otro")
        assertTrue(repo.cancelar("solicitud") is AppResult.Error)
        verify(tx, never()).update(any(DocumentReference::class.java), anyMap<String, Any>())
    }
    @Test fun `cancellation blocks slot when agenda changed`() = runTest {
        editableBooking()
        `when`(docs.getValue("barberos/barbero/agenda/semanal").data).thenReturn(mapOf("Lunes" to false))
        assertTrue(repo.cancelar("solicitud") is AppResult.Success)
        @Suppress("UNCHECKED_CAST")
        val fields = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
        verify(tx).update(eq(refs.getValue("horarios/$slotId")), fields.capture())
        assertEquals("bloqueado", (fields.value as Map<*, *>)["Estado"])
    }
    @Test fun `modification keeps booking identity and creation time`() = runTest {
        editableBooking()
        assertTrue(repo.modificar("solicitud", request) is AppResult.Success)
        val fields = ArgumentCaptor.forClass(Any::class.java)
        verify(tx).set(eq(refs.getValue("turnos/solicitud")), fields.capture())
        assertEquals(docs.getValue("turnos/solicitud").get("CreadoEn"), (fields.value as Map<*, *>)["CreadoEn"])
        verify(tx, never()).update(eq(refs.getValue("horarios/$slotId")), anyMap<String, Any>())
    }
    @Test fun `modification cannot take another occupied slot`() = runTest {
        editableBooking()
        val next = start + 2400000
        val other = mock(DocumentSnapshot::class.java)
        docs["horarios/barbero_$next"] = other
        `when`(other.exists()).thenReturn(true)
        `when`(other.getString("Estado")).thenReturn("ocupado")
        `when`(other.getString("TurnoId")).thenReturn("ajeno")
        assertTrue(repo.modificar("solicitud", request.copy(inicioMillis = next)) is AppResult.Error)
        verify(tx, never()).set(any(DocumentReference::class.java), any<Any>())
        verify(tx, never()).update(any(DocumentReference::class.java), anyMap<String, Any>())
    }

    @Test fun `reservation uses authenticated UID and atomically occupies slot with booking`() = runTest {
        setup()
        assertTrue(repo.reservar(request) is AppResult.Success)
        val fields = ArgumentCaptor.forClass(Any::class.java)
        verify(tx).set(eq(refs.getValue("turnos/solicitud")), fields.capture())
        val booking = fields.value as Map<*, *>
        assertEquals("ana", booking["ClienteId"])
        assertEquals(slotId, booking["HorarioId"])
        assertEquals("corte", booking["ServicioId"])
        verify(tx).update(refs.getValue("horarios/$slotId"), mapOf("Estado" to "ocupado", "TurnoId" to "solicitud"))
        verify(tx).update(refs.getValue("clientes/ana"), "Nombre", "Ana")
    }
    @Test fun `occupied slot produces no writes`() = runTest {
        setup(occupied = true)
        assertTrue(repo.reservar(request) is AppResult.Error)
        verify(tx, never()).set(any(DocumentReference::class.java), any<Any>())
        verify(tx, never()).update(any(DocumentReference::class.java), anyMap<String, Any>())
    }
    @Test fun `retry returns existing own booking without writes`() = runTest {
        setup(existing = true)
        assertEquals("solicitud", (repo.reservar(request) as AppResult.Success).data)
        verify(tx, never()).set(any(DocumentReference::class.java), any<Any>())
        verify(tx, never()).get(refs.getValue("horarios/$slotId"))
    }
    @Test fun `unauthenticated user cannot submit reservation`() = runTest {
        assertTrue(repo.reservar(request) is AppResult.Error)
        verifyNoInteractions(firestore)
    }
    @Test fun `barber saves own agenda with exact Firestore field names`() = runTest {
        setup()
        `when`(auth.currentUser!!.uid).thenReturn("barbero")
        val agenda = AgendaSemanal("America/Argentina/Buenos_Aires", List(6) {
            DiaAtencion(it == 0, listOf(FranjaAtencion("09:00", "13:00")))
        }, LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString())
        assertTrue(repo.guardarMiAgenda(agenda) is AppResult.Success)
        val fields = ArgumentCaptor.forClass(Any::class.java)
        verify(tx).set(eq(refs.getValue("barberos/barbero/agenda/${agenda.inicioSemana}")), fields.capture())
        val data = fields.value as Map<*, *>
        assertEquals(setOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado"), data.keys)
        assertEquals(mapOf("Estado" to true, "Franjas" to listOf(mapOf("Inicio" to "09:00", "Fin" to "13:00"))), data["Lunes"])
        verify(tx, times(1)).set(any(DocumentReference::class.java), any<Any>())
    }
    @Test fun `disabled barber cannot save agenda`() = runTest {
        setup()
        `when`(auth.currentUser!!.uid).thenReturn("barbero")
        `when`(docs.getValue("barberos/barbero").getBoolean("Estado")).thenReturn(false)
        val date = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        assertTrue(repo.guardarMiAgenda(AgendaSemanal("America/Argentina/Buenos_Aires", emptyList(), date)) is AppResult.Error)
        verify(tx, never()).set(any(DocumentReference::class.java), any<Any>())
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> completed(value: T): Task<T> {
        val task = mock(Task::class.java) as Task<T>
        `when`(task.isSuccessful).thenReturn(true)
        `when`(task.result).thenReturn(value)
        `when`(task.addOnCompleteListener(any(Executor::class.java), any<OnCompleteListener<T>>())).thenAnswer {
            it.getArgument<OnCompleteListener<T>>(1).onComplete(task)
            task
        }
        return task
    }
    @Test fun `publishing one week never retires slots from the other week`() = runTest {
        setup()
        `when`(auth.currentUser!!.uid).thenReturn("barbero")
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val monday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val agenda = AgendaSemanal(zone.id, List(6) { DiaAtencion(false, emptyList()) }, monday.toString())
        val barberRef = firestore.collection("barberos").document("barbero")
        val barberDoc = docs.getValue("barberos/barbero")
        `when`(barberDoc.reference).thenReturn(barberRef)
        doReturn(completed(barberDoc)).`when`(barberRef).get(Source.SERVER)
        val weeklyRef = barberRef.collection("agenda").document(monday.toString())
        val weeklyDoc = mock(DocumentSnapshot::class.java)
        `when`(weeklyDoc.exists()).thenReturn(true)
        for (day in listOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado")) {
            `when`(weeklyDoc.get(day)).thenReturn(mapOf("Estado" to false, "Franjas" to emptyList<Any>()))
        }
        doReturn(completed(weeklyDoc)).`when`(weeklyRef).get(Source.SERVER)
        val horarios = mock(CollectionReference::class.java)
        val query = mock(Query::class.java)
        val snapshot = mock(QuerySnapshot::class.java)
        `when`(firestore.collection("horarios")).thenReturn(horarios)
        `when`(horarios.whereEqualTo("BarberoId", "barbero")).thenReturn(query)
        doReturn(completed(snapshot)).`when`(query).get(Source.SERVER)
        val currentRef = mock(DocumentReference::class.java)
        val nextRef = mock(DocumentReference::class.java)
        val current = mock(DocumentSnapshot::class.java)
        val next = mock(DocumentSnapshot::class.java)
        `when`(current.reference).thenReturn(currentRef)
        `when`(next.reference).thenReturn(nextRef)
        `when`(current.id).thenReturn("actual")
        `when`(next.id).thenReturn("proxima")
        `when`(current.getTimestamp("Inicio")).thenReturn(Timestamp(Date(monday.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())))
        `when`(next.getTimestamp("Inicio")).thenReturn(Timestamp(Date(monday.plusWeeks(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())))
        `when`(current.getString("Estado")).thenReturn("libre")
        `when`(snapshot.documents).thenReturn(listOf(current, next))
        doReturn(current).`when`(tx).get(currentRef)
        val result = repo.publicarSemana(agenda, SemanaDisponible(monday.atStartOfDay(zone).toInstant().toEpochMilli(), emptyList()))
        assertTrue(result.toString(), result is AppResult.Success)
        verify(tx).update(currentRef, "Estado", "bloqueado")
        verify(tx, never()).get(nextRef)
    }
    @Test fun `loading next week reads its own dated agenda`() = runTest {
        setup()
        `when`(auth.currentUser!!.uid).thenReturn("barbero")
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val nextMonday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
        val barberRef = firestore.collection("barberos").document("barbero")
        val barber = docs.getValue("barberos/barbero")
        `when`(barber.reference).thenReturn(barberRef)
        doReturn(completed(barber)).`when`(barberRef).get(Source.SERVER)
        val nextRef = barberRef.collection("agenda").document(nextMonday.toString())
        val nextDoc = mock(DocumentSnapshot::class.java)
        `when`(nextDoc.exists()).thenReturn(true)
        for (day in listOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado")) {
            `when`(nextDoc.get(day)).thenReturn(mapOf("Estado" to false, "Franjas" to emptyList<Any>()))
        }
        doReturn(completed(nextDoc)).`when`(nextRef).get(Source.SERVER)
        val result = repo.obtenerMiAgenda(1) as AppResult.Success
        assertEquals(nextMonday.toString(), result.data.inicioSemana)
        assertTrue(result.data.dias.none { it.habilitado })
        verify(tx, never()).set(any(DocumentReference::class.java), any<Any>())
    }
}
