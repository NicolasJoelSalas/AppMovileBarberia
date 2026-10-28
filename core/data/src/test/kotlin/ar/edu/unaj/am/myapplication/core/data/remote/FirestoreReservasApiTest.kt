package ar.edu.unaj.am.myapplication.core.data.remote

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.crearDiaAtencion
import com.google.android.gms.tasks.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import java.time.*
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

/** Prueba el adaptador Android real con documentos controlados. Las reglas se prueban con emulador. */
class FirestoreReservasApiTest {
    private val firestore = mock(FirebaseFirestore::class.java)
    private val auth = mock(FirebaseAuth::class.java)
    private val user = mock(FirebaseUser::class.java)
    private val tx = mock(Transaction::class.java)
    private val documentos = mutableMapOf<String, Map<String, Any?>>()
    private val referencias = mutableMapOf<String, DocumentReference>()
    private val rutas = mutableMapOf<DocumentReference, String>()
    private val pendientes = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var usuario = "ana"
    private var escrituras = 0
    private var antesDeTransaccion: (() -> Unit)? = null
    private var fallarEscritura: ((String, Map<String, Any?>) -> Boolean)? = null
    private val fecha = LocalDate.parse("2026-11-02")
    private val zona = ZoneId.of("UTC")
    private val reloj = Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), zona)
    private val api = FirestoreReservasApi(firestore, auth, reloj)
    private val agenda = listOf("Lunes","Martes","Miercoles","Jueves","Viernes","Sabado").associateWith {
        mapOf("Estado" to true, "Franjas" to listOf(mapOf("Inicio" to "09:00", "Fin" to "13:00")))
    }
    private fun hora(h: Int, m: Int = 0) = fecha.atTime(h,m).atZone(zona).toInstant().toEpochMilli()
    private fun diaId(barbero: String = "barbero") = "${barbero}_${hora(0)}"
    private fun solicitud(minutos: Int = 60, inicio: Long = hora(9)) = mapOf<String, Any?>(
        "solicitudId" to "solicitud", "barberoId" to "barbero", "servicioId" to "corte",
        "inicioMillis" to inicio, "finMillisEsperado" to inicio + minutos * 60000L, "nombreCliente" to "Ana")

    init {
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.uid).thenAnswer { usuario }
        `when`(firestore.collection(anyString())).thenAnswer { coleccion(it.getArgument(0)) }
        `when`(tx.get(any(DocumentReference::class.java))).thenAnswer {
            check(pendientes.isEmpty()) { "No se puede leer después de escribir en una transacción." }
            snapshot(rutas.getValue(it.getArgument(0)))
        }
        doAnswer {
            val ref = it.getArgument<DocumentReference>(0)
            @Suppress("UNCHECKED_CAST")
            pendientes += rutas.getValue(ref) to (it.getArgument<Any>(1) as Map<String, Any?>)
            tx
        }.`when`(tx).set(any(DocumentReference::class.java), any<Any>())
        doAnswer {
            val path = rutas.getValue(it.getArgument<DocumentReference>(0))
            val anterior = pendientes.lastOrNull { p -> p.first == path }?.second ?: documentos[path].orEmpty()
            pendientes += path to (anterior + it.getArgument<Map<String, Any?>>(1))
            tx
        }.`when`(tx).update(any(DocumentReference::class.java), anyMap<String, Any>())
        doAnswer {
            val path = rutas.getValue(it.getArgument<DocumentReference>(0))
            pendientes += path to (documentos[path].orEmpty() + (it.getArgument<String>(1) to it.getArgument<Any>(2)))
            tx
        }.`when`(tx).update(any(DocumentReference::class.java), anyString(), any<Any>())
        `when`(firestore.runTransaction(any<Transaction.Function<Any>>())).thenAnswer {
            pendientes.clear()
            try {
                antesDeTransaccion?.invoke()
                val value = it.getArgument<Transaction.Function<Any>>(0).apply(tx)
                check(pendientes.none { (ruta, campos) -> fallarEscritura?.invoke(ruta, campos) == true }) {
                    "Conexión interrumpida antes de confirmar."
                }
                for ((ruta, campos) in pendientes) {
                    documentos[ruta] = campos.mapValues { (_, v) -> if (v is FieldValue) Timestamp(java.util.Date(reloj.millis())) else v }
                    escrituras++
                }
                completado(value)
            } finally { pendientes.clear() }
        }
        documentos["clientes/ana"] = mapOf("Nombre" to "Ana")
        documentos["barberos/barbero"] = mapOf("Estado" to true, "ZonaHoraria" to "UTC", "ReservasVersion" to 3L)
        documentos["barberos/barbero/agenda/2026-11-02"] = agenda
        documentos["servicios/corte"] = mapOf("Estado" to true, "Duracion" to 60L, "BarberoId" to "barbero")
        for (i in 0L..5L) {
            val d = crearDiaAtencion(fecha.plusDays(i),zona,DiaAtencion(true,listOf(FranjaAtencion("09:00","13:00"))))
            documentos["disponibilidad/barbero_${d.inicioMillis}"] = mapOf("Inicio" to Timestamp(java.util.Date(d.inicioMillis)),
                "Fin" to Timestamp(java.util.Date(d.inicioMillis+86400000)), "BarberoId" to "barbero", "Atencion" to d.atencion,
                "Ocupacion" to d.ocupacion,"Agenda" to agenda,"AgendaId" to "2026-11-02","ZonaHoraria" to "UTC")
        }
    }
    private fun referencia(path: String): DocumentReference = referencias.getOrPut(path) {
        val r = mock(DocumentReference::class.java)
        rutas[r] = path
        `when`(r.id).thenReturn(path.substringAfterLast('/'))
        `when`(r.collection(anyString())).thenAnswer { coleccion("$path/${it.getArgument<String>(0)}") }
        `when`(r.get(Source.SERVER)).thenAnswer { completado(snapshot(path)) }
        r
    }
    private fun coleccion(path: String): CollectionReference {
        val c = mock(CollectionReference::class.java)
        `when`(c.document(anyString())).thenAnswer { referencia("$path/${it.getArgument<String>(0)}") }
        `when`(c.whereEqualTo(anyString(), any<Any>())).thenAnswer {
            val campo = it.getArgument<String>(0); val valor = it.getArgument<Any>(1)
            val q = mock(Query::class.java)
            `when`(q.get(Source.SERVER)).thenAnswer {
                val s = mock(QuerySnapshot::class.java)
                val docs = documentos.filter { (k,v) -> k.startsWith("$path/") && k.removePrefix("$path/").count { it == '/' } == 0 && v[campo] == valor }
                    .keys.map { snapshot(it) }
                `when`(s.documents).thenReturn(docs)
                completado(s)
            }
            q
        }
        return c
    }
    private fun snapshot(path: String): DocumentSnapshot {
        val d = mock(DocumentSnapshot::class.java)
        val data = documentos[path]
        val ref = referencia(path)
        `when`(d.id).thenReturn(path.substringAfterLast('/'))
        `when`(d.reference).thenReturn(ref)
        `when`(d.exists()).thenReturn(data != null)
        `when`(d.data).thenReturn(data)
        `when`(d.get(anyString())).thenAnswer { data?.get(it.getArgument<String>(0)) }
        `when`(d.getString(anyString())).thenAnswer { data?.get(it.getArgument<String>(0)) as? String }
        `when`(d.getLong(anyString())).thenAnswer { (data?.get(it.getArgument<String>(0)) as? Number)?.toLong() }
        `when`(d.getBoolean(anyString())).thenAnswer { data?.get(it.getArgument<String>(0)) as? Boolean }
        `when`(d.getTimestamp(anyString())).thenAnswer { data?.get(it.getArgument<String>(0)) as? Timestamp }
        return d
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> completado(value: T): Task<T> {
        val task = mock(Task::class.java) as Task<T>
        `when`(task.isSuccessful).thenReturn(true)
        `when`(task.result).thenReturn(value)
        `when`(task.addOnCompleteListener(any(Executor::class.java), any<OnCompleteListener<T>>())).thenAnswer {
            it.getArgument<OnCompleteListener<T>>(1).onComplete(task); task
        }
        return task
    }
    @Test fun `native transaction stores actual duration and reuses successful request without writes`() = runTest {
        documentos["servicios/corte"] = documentos.getValue("servicios/corte") + ("Duracion" to 75L)
        api.ejecutar("reservarTurno",solicitud(75))
        val turno = documentos.getValue("turnos/solicitud")
        assertEquals(hora(10,15),(turno["Fin"] as Timestamp).toDate().time)
        val cantidad = escrituras
        api.ejecutar("reservarTurno",solicitud(75))
        assertEquals(cantidad,escrituras)
        assertEquals("1".repeat(75),(documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"] as String).substring(540,615))
    }
    @Test fun `failed edit preserves booking and occupancy and cancellation frees exact interval`() = runTest {
        api.ejecutar("reservarTurno",solicitud())
        api.ejecutar("reservarTurno",solicitud(inicio=hora(10))+("solicitudId" to "otra"))
        val antes = documentos.toMap()
        try { api.ejecutar("modificarTurno",solicitud(inicio=hora(9,30))+("turnoId" to "solicitud")); fail("Horario ocupado") }
        catch (_: IllegalArgumentException) { }
        assertEquals(antes,documentos)
        api.ejecutar("cancelarTurno",mapOf("turnoId" to "solicitud"))
        val mascara = documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"] as String
        assertEquals("0".repeat(60),mascara.substring(540,600))
        assertEquals("1".repeat(60),mascara.substring(600,660))
    }
    @Test fun `availability excludes own booking and includes the real finish of new service`() = runTest {
        api.ejecutar("reservarTurno",solicitud())
        documentos["servicios/corte"] = documentos.getValue("servicios/corte") + ("Duracion" to 90L)
        val semana = api.ejecutar("consultarSemana",mapOf("barberoId" to "barbero","servicioId" to "corte","turnoId" to "solicitud"))
        val primero = (semana["horarios"] as List<*>).first() as Map<*,*>
        assertEquals(hora(9),primero["inicioMillis"])
        assertEquals(hora(10,30),primero["finMillis"])
        api.ejecutar("modificarTurno",solicitud(90)+("turnoId" to "solicitud"))
        assertEquals(90,documentos.getValue("turnos/solicitud")["DuracionMinutos"])
    }
    @Test fun `publication seeds legacy bookings then cancels future appointments outside attention`() = runTest {
        usuario = "barbero"
        documentos.keys.filter { it.startsWith("disponibilidad/") }.toList().forEach { documentos.remove(it) }
        val vieja = mapOf<String,Any?>("ClienteId" to "ana","BarberoId" to "barbero","ServicioId" to "corte",
            "Inicio" to Timestamp(java.util.Date(hora(9))),"Fin" to Timestamp(java.util.Date(hora(10))),"Estado" to "confirmado")
        documentos["turnos/anterior"] = vieja
        api.ejecutar("publicarAgenda",mapOf("inicioSemana" to "2026-11-02","agenda" to agenda))
        assertEquals("1".repeat(60),(documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"] as String).substring(540,600))
        val nueva = agenda + ("Lunes" to mapOf("Estado" to false,"Franjas" to emptyList<Any>()))
        documentos["barberos/barbero/agenda/2026-11-02"] = nueva
        api.ejecutar("publicarAgenda",mapOf("inicioSemana" to "2026-11-02","agenda" to nueva))
        assertEquals("cancelado",documentos.getValue("turnos/anterior")["Estado"])
        assertEquals("0".repeat(1440),documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"])
    }

    @Test fun `interrupted publication blocks reservations and a fresh adapter resumes without losing bookings`() = runTest {
        api.ejecutar("reservarTurno", solicitud())
        api.ejecutar("reservarTurno", solicitud(inicio = hora(10)) + ("solicitudId" to "conservado"))
        val nueva = agenda + ("Lunes" to mapOf("Estado" to true,
            "Franjas" to listOf(mapOf("Inicio" to "10:00", "Fin" to "13:00"))))
        documentos["barberos/barbero/agenda/2026-11-02"] = nueva
        usuario = "barbero"
        fallarEscritura = { ruta, campos -> ruta == "turnos/solicitud" && campos["Estado"] == "cancelado" }
        try {
            api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
            fail("Debe informar la interrupción")
        } catch (_: IllegalStateException) { }
        assertTrue(documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == true })
        usuario = "ana"
        try {
            api.ejecutar("reservarTurno", solicitud(inicio = hora(11)) + ("solicitudId" to "bloqueado"))
            fail("No debe reservar durante una publicación incompleta")
        } catch (_: DomainValidationException) { }
        assertFalse(documentos.containsKey("turnos/bloqueado"))
        fallarEscritura = null
        usuario = "barbero"
        FirestoreReservasApi(firestore, auth, reloj).ejecutar("publicarAgenda",
            mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
        assertEquals("cancelado", documentos.getValue("turnos/solicitud")["Estado"])
        assertEquals("confirmado", documentos.getValue("turnos/conservado")["Estado"])
        val dia = documentos.getValue("disponibilidad/${diaId()}")
        assertEquals("0".repeat(60), (dia["Ocupacion"] as String).substring(540, 600))
        assertEquals("1".repeat(60), (dia["Ocupacion"] as String).substring(600, 660))
        assertTrue(documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == false })
        usuario = "ana"
        api.ejecutar("reservarTurno", solicitud(inicio = hora(11)) + ("solicitudId" to "recuperado"))
        assertEquals("confirmado", documentos.getValue("turnos/recuperado")["Estado"])
    }

    @Test fun `failure enabling the week never enables only some days and retry preserves cancellations`() = runTest {
        api.ejecutar("reservarTurno", solicitud())
        usuario = "barbero"
        val nueva = agenda + ("Lunes" to mapOf("Estado" to false, "Franjas" to emptyList<Any>()))
        documentos["barberos/barbero/agenda/2026-11-02"] = nueva
        fallarEscritura = { ruta, campos -> ruta.startsWith("disponibilidad/") && campos["PublicacionPendiente"] == false }
        try {
            api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
            fail("Debe fallar la confirmación final")
        } catch (_: IllegalStateException) { }
        assertEquals("cancelado", documentos.getValue("turnos/solicitud")["Estado"])
        assertTrue(documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == true })
        fallarEscritura = null
        api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
        assertTrue(documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == false })
        assertEquals("0".repeat(1440), documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"])
    }

    @Test fun `publication rechecks a booking cancelled after the initial query`() = runTest {
        api.ejecutar("reservarTurno", solicitud())
        val nueva = agenda + ("Lunes" to mapOf("Estado" to false, "Franjas" to emptyList<Any>()))
        documentos["barberos/barbero/agenda/2026-11-02"] = nueva
        usuario = "barbero"
        antesDeTransaccion = {
            if (documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == true }) {
                antesDeTransaccion = null
                documentos["turnos/solicitud"] = documentos.getValue("turnos/solicitud") + ("Estado" to "cancelado")
                val ruta = "disponibilidad/${diaId()}"
                documentos[ruta] = documentos.getValue(ruta) + ("Ocupacion" to "0".repeat(1440))
            }
        }
        api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
        assertEquals("cancelado", documentos.getValue("turnos/solicitud")["Estado"])
        assertEquals("0".repeat(1440), documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"])
    }

    @Test fun `failure preparing a day is recoverable after recreating the adapter`() = runTest {
        usuario = "barbero"
        val nueva = agenda + ("Miercoles" to mapOf("Estado" to false, "Franjas" to emptyList<Any>()))
        documentos["barberos/barbero/agenda/2026-11-02"] = nueva
        val martes = "disponibilidad/barbero_${hora(0) + 86400000}"
        fallarEscritura = { ruta, _ -> ruta == martes }
        try {
            api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
            fail("Debe informar qué publicación no pudo completar")
        } catch (_: IllegalStateException) { }
        assertEquals(true, documentos.getValue("disponibilidad/${diaId()}")["PublicacionPendiente"])
        usuario = "ana"
        try {
            api.ejecutar("consultarSemana", mapOf("barberoId" to "barbero", "servicioId" to "corte"))
            fail("No debe ofrecer una semana parcialmente publicada")
        } catch (_: DomainValidationException) { }
        fallarEscritura = null
        usuario = "barbero"
        FirestoreReservasApi(firestore, auth, reloj).ejecutar("publicarAgenda",
            mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
        assertTrue(documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all {
            it["PublicacionPendiente"] == false && it["Agenda"] == nueva
        })
    }

    @Test fun `a changed agenda aborts pending cancellations instead of applying an obsolete publication`() = runTest {
        api.ejecutar("reservarTurno", solicitud())
        val nueva = agenda + ("Lunes" to mapOf("Estado" to false, "Franjas" to emptyList<Any>()))
        val ruta = "barberos/barbero/agenda/2026-11-02"
        documentos[ruta] = nueva
        usuario = "barbero"
        antesDeTransaccion = {
            if (documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == true }) {
                antesDeTransaccion = null
                documentos[ruta] = agenda
            }
        }
        try {
            api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
            fail("La publicación anterior dejó de ser vigente")
        } catch (_: IllegalStateException) { }
        assertEquals("confirmado", documentos.getValue("turnos/solicitud")["Estado"])
        api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to agenda))
        assertEquals("confirmado", documentos.getValue("turnos/solicitud")["Estado"])
        assertEquals(false, documentos.getValue("disponibilidad/${diaId()}")["PublicacionPendiente"])
    }

    @Test fun `publication preserves a booking moved into the new attention after its initial query`() = runTest {
        api.ejecutar("reservarTurno", solicitud())
        val nueva = agenda + ("Lunes" to mapOf("Estado" to true,
            "Franjas" to listOf(mapOf("Inicio" to "10:00", "Fin" to "13:00"))))
        documentos["barberos/barbero/agenda/2026-11-02"] = nueva
        usuario = "barbero"
        antesDeTransaccion = {
            if (documentos.filterKeys { it.startsWith("disponibilidad/") }.values.all { it["PublicacionPendiente"] == true }) {
                antesDeTransaccion = null
                documentos["turnos/solicitud"] = documentos.getValue("turnos/solicitud") + mapOf(
                    "Inicio" to Timestamp(java.util.Date(hora(10))), "Fin" to Timestamp(java.util.Date(hora(11))))
                val ruta = "disponibilidad/${diaId()}"
                documentos[ruta] = documentos.getValue(ruta) +
                    ("Ocupacion" to ("0".repeat(600) + "1".repeat(60) + "0".repeat(780)))
            }
        }
        api.ejecutar("publicarAgenda", mapOf("inicioSemana" to "2026-11-02", "agenda" to nueva))
        assertEquals("confirmado", documentos.getValue("turnos/solicitud")["Estado"])
        assertEquals("1".repeat(60), (documentos.getValue("disponibilidad/${diaId()}")["Ocupacion"] as String).substring(600,660))
    }
}
