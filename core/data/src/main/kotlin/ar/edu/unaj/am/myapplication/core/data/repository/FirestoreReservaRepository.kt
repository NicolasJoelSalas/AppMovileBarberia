package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal
import ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Traduce documentos y ejecuta operaciones atómicas. No calcula franjas de atención. */
class FirestoreReservaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val sesionLocal: SesionClienteLocal? = null,
    private val sesionBarberoLocal: SesionBarberoLocal? = null,
    private val subirReferencia: (suspend (String) -> String)? = null
) : ReservaRepository {
    // Mantiene la misma subida cuando una confirmación falla y se reintenta.
    private val referenciasPendientes = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun uid() = requireNotNull(auth.currentUser?.uid) { "Iniciá sesión." }
    private fun slotId(barbero: String, inicio: Long) = "${barbero}_$inicio"
    private fun monday(zone: String, offset: Int): LocalDate {
        require(offset in 0..1) { "Elegí la semana actual o la próxima." }
        return LocalDate.now(ZoneId.of(zone)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(offset.toLong())
    }
    private fun agendaId(slot: DocumentSnapshot) = slot.getString("AgendaId") ?: "semanal"
    private fun enabled(doc: DocumentSnapshot) = (doc.get("Estado") as? Boolean)
        ?: (doc.get("Activo") as? Boolean) ?: false
    private fun agenda(doc: DocumentSnapshot, zone: String): AgendaSemanal {
        if (!doc.exists()) return AgendaSemanal(zone, List(6) { DiaAtencion(false, emptyList()) })
        return AgendaSemanal(zone, listOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado").map { name ->
            val day = doc.get(name) as? Map<*, *> ?: error("Falta configurar $name en la agenda.")
            val active = day["Estado"] as? Boolean ?: error("$name.Estado debe ser boolean.")
            val spans = (day["Franjas"] as? List<*>
                ?: if (!active) emptyList<Any>() else error("Configurá las franjas de $name.")).map {
                val span = it as? Map<*, *> ?: error("Las franjas deben ser mapas con Inicio y Fin.")
                FranjaAtencion(span["Inicio"] as? String ?: error("Falta Inicio en $name."),
                    span["Fin"] as? String ?: error("Falta Fin en $name."))
            }
            DiaAtencion(active, spans)
        })
    }

    override suspend fun cargarCatalogo(): AppResult<CatalogoReservas> {
        val resultado = cargarCatalogoRemoto()
        val user = auth.currentUser?.uid
        if (resultado is AppResult.Error && resultado.error == DomainError.Network &&
            user != null && sesionBarberoLocal?.estaValidado(user) == true) {
            return AppResult.Success(CatalogoReservas(true, emptyList(), emptyList(), "", sinConexion = true))
        }
        return if (resultado is AppResult.Error && resultado.error == DomainError.Network &&
            user != null && sesionLocal?.estaValidado(user) == true) {
            AppResult.Success(CatalogoReservas(false, emptyList(), emptyList(), sesionLocal.nombre(user), sinConexion = true))
        } else resultado
    }

    private suspend fun cargarCatalogoRemoto(): AppResult<CatalogoReservas> = result {
        val uid = uid()
        val barbero = firestore.collection("barberos").document(uid).get(Source.SERVER).awaitReserva()
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Iniciá sesión nuevamente." }
        if (barbero.exists()) {
            sesionLocal?.limpiar(uid)
            if (barbero.getBoolean("Estado") == true) sesionBarberoLocal?.guardar(uid)
            else sesionBarberoLocal?.limpiar(uid)
            CatalogoReservas(true, emptyList(), emptyList(), "")
        } else {
            sesionBarberoLocal?.limpiar(uid)
            val barberos = firestore.collection("barberos").whereEqualTo("Estado", true)
                .get(Source.SERVER).awaitReserva().documents.map { Barbero(it.id, it.getString("Nombre").orEmpty()) }
            val servicios = firestore.collection("servicios").get(Source.SERVER).awaitReserva().documents
                .filter { enabled(it) && it.getLong("Duracion") == 40L && !it.getString("BarberoId").isNullOrBlank() }
                .map { Servicio(it.id, it.getString("NombreCorte").orEmpty(), 40, it.getString("BarberoId"),
                    importeCentavos(it.get("Precio"))) }
            val nombre = firestore.collection("clientes").document(uid).get(Source.SERVER)
                .awaitReserva().getString("Nombre").orEmpty()
            sesionLocal?.guardar(uid, nombre)
            CatalogoReservas(false, barberos, servicios, nombre)
        }
    }

    override suspend fun obtenerMiAgenda(semanaOffset: Int): AppResult<AgendaSemanal> = result {
        val barber = firestore.collection("barberos").document(uid()).get(Source.SERVER).awaitReserva()
        check(barber.getBoolean("Estado") == true) { "Solo un barbero habilitado puede publicar horarios." }
        val zone = barber.getString("ZonaHoraria") ?: error("Configurá ZonaHoraria del barbero.")
        val date = monday(zone, semanaOffset).toString()
        val weekly = barber.reference.collection("agenda").document(date).get(Source.SERVER).awaitReserva()
        val source = if (weekly.exists()) weekly else barber.reference.collection("agenda").document("semanal")
            .get(Source.SERVER).awaitReserva()
        agenda(source, zone).copy(inicioSemana = date)
    }

    override suspend fun obtenerMisTurnos(): AppResult<List<Turno>> = result {
        val uid = uid()
        firestore.collection("turnos").whereEqualTo("BarberoId", uid).get(Source.SERVER).awaitReserva().documents.map {
            Turno(it.id, it.getString("ClienteId").orEmpty(), uid, it.getString("ServicioId").orEmpty(),
                it.getTimestamp("Inicio")?.toDate()?.time, it.getTimestamp("Fin")?.toDate()?.time,
                it.get("Estado").toString(), referenciaCorteUrl = it.getString("ReferenciaCorteUrl"))
        }
    }

    override suspend fun guardarMiAgenda(agenda: AgendaSemanal): AppResult<Unit> = result {
        val barberRef = firestore.collection("barberos").document(uid())
        val date = requireNotNull(agenda.inicioSemana) { "Volvé a cargar la semana antes de guardarla." }
        check(date == monday(agenda.zonaHoraria, 0).toString() || date == monday(agenda.zonaHoraria, 1).toString()) {
            "La semana cambió. Volvé a cargar la agenda."
        }
        val agendaRef = barberRef.collection("agenda").document(date)
        val names = listOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado")
        val fields = names.zip(agenda.dias).associate { (name, day) ->
            name to mapOf("Estado" to day.habilitado,
                "Franjas" to day.franjas.map { mapOf("Inicio" to it.inicio, "Fin" to it.fin) })
        }
        firestore.runTransaction<Unit> { tx ->
            val barber = tx.get(barberRef)
            check(barber.getBoolean("Estado") == true) { "Solo un barbero habilitado puede editar su agenda." }
            check(barber.getString("ZonaHoraria") == agenda.zonaHoraria) {
                "La zona horaria cambió. Volvé a cargar tu agenda."
            }
            tx.set(agendaRef, fields)
            Unit
        }.awaitReserva()
    }

    override suspend fun publicarSemana(agenda: AgendaSemanal, semana: SemanaDisponible): AppResult<Int> = result {
        val uid = uid()
        val barber = firestore.collection("barberos").document(uid).get(Source.SERVER).awaitReserva()
        val date = requireNotNull(agenda.inicioSemana) { "Volvé a cargar la semana." }
        var source = barber.reference.collection("agenda").document(date).get(Source.SERVER).awaitReserva()
        if (!source.exists()) {
            when (val saved = guardarMiAgenda(agenda)) {
                is AppResult.Error -> error("No se pudo guardar la configuración semanal antes de publicar.")
                is AppResult.Success -> Unit
            }
            source = barber.reference.collection("agenda").document(date).get(Source.SERVER).awaitReserva()
        }
        check(agenda(source, barber.getString("ZonaHoraria").orEmpty()).copy(inicioSemana = date) == agenda) {
            "La agenda cambió. Volvé a publicar la semana."
        }
        val desired = semana.horarios.associateBy { slotId(uid, it.inicioMillis) }
        val old = firestore.collection("horarios").whereEqualTo("BarberoId", uid)
            .get(Source.SERVER).awaitReserva().documents
        val weekStart = semana.inicioSemanaMillis
        val weekEnd = LocalDate.parse(date).plusWeeks(1).atStartOfDay(ZoneId.of(semana.zonaHoraria)).toInstant().toEpochMilli()
        // La lista publicada excluye reservas existentes: comprobar la franja, no solo desired.
        fun perteneceALaAgenda(doc: DocumentSnapshot): Boolean {
            val inicio = doc.getTimestamp("Inicio")?.toDate()?.toInstant()
                ?.atZone(ZoneId.of(semana.zonaHoraria)) ?: return false
            val fin = doc.getTimestamp("Fin")?.toDate()?.toInstant()
                ?.atZone(ZoneId.of(semana.zonaHoraria)) ?: return false
            return agenda.contieneTurno(inicio, fin)
        }
        for (doc in old.filter {
            val start = it.getTimestamp("Inicio")?.toDate()?.time
            start != null && start >= weekStart && start < weekEnd && it.id !in desired
        }) {
            firestore.runTransaction { tx ->
                val actual = tx.get(doc.reference)
                val vigente = tx.get(source.reference)
                check(vigente.data == source.data) { "La agenda cambió. Volvé a publicar la semana." }
                if (actual.getString("Estado") == "ocupado" && !perteneceALaAgenda(actual) &&
                    (actual.getTimestamp("Inicio")?.toDate()?.time ?: 0L) > System.currentTimeMillis()) {
                    val turnoRef = firestore.collection("turnos").document(requireNotNull(actual.getString("TurnoId")))
                    val turno = tx.get(turnoRef)
                    check(turno.getString("HorarioId") == actual.id && turno.getString("BarberoId") == uid)
                    check(turno.getString("Estado")?.lowercase() in listOf("confirmado", "pendiente"))
                    tx.update(turnoRef, mapOf("Estado" to "cancelado", "CanceladoEn" to FieldValue.serverTimestamp()))
                    tx.update(doc.reference, mapOf("Estado" to "bloqueado", "TurnoId" to FieldValue.delete()))
                } else if (actual.getString("Estado") == "libre") {
                    tx.update(doc.reference, "Estado", "bloqueado")
                }
            }.awaitReserva()
        }
        var count = 0
        for ((id, slot) in desired) {
            val ref = firestore.collection("horarios").document(id)
            val published = firestore.runTransaction { tx ->
                val existing = tx.get(ref)
                if (existing.getString("Estado") == "ocupado") false else {
                    val data = mapOf("BarberoId" to uid, "Inicio" to Timestamp(Date(slot.inicioMillis)),
                        "Fin" to Timestamp(Date(slot.finMillis)), "Estado" to "libre",
                        "Agenda" to source.data, "AgendaId" to date, "ZonaHoraria" to semana.zonaHoraria)
                    if (existing.data != data) tx.set(ref, data)
                    true
                }
            }.awaitReserva()
            if (published) count++
        }
        count
    }

    override suspend fun consultarSemana(barberoId: String, servicioId: String, semanaOffset: Int): AppResult<SemanaDisponible> = result {
        uid()
        val barber = firestore.collection("barberos").document(barberoId).get(Source.SERVER).awaitReserva()
        val service = firestore.collection("servicios").document(servicioId).get(Source.SERVER).awaitReserva()
        check(barber.getBoolean("Estado") == true && enabled(service) && service.getLong("Duracion") == 40L &&
            service.getString("BarberoId") == barberoId) {
            "Elegí un barbero habilitado y un servicio de 40 minutos."
        }
        val zoneName = barber.getString("ZonaHoraria") ?: error("Falta ZonaHoraria del barbero.")
        val zone = ZoneId.of(zoneName)
        val now = Instant.now()
        val monday = monday(zoneName, semanaOffset)
        val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = monday.plusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val agenda = barber.reference.collection("agenda").document(monday.toString()).get(Source.SERVER).awaitReserva()
        val legacy = barber.reference.collection("agenda").document("semanal").get(Source.SERVER).awaitReserva()
        val slots = firestore.collection("horarios").whereEqualTo("BarberoId", barberoId)
            .get(Source.SERVER).awaitReserva().documents.mapNotNull {
                val start = it.getTimestamp("Inicio")?.toDate()?.time ?: return@mapNotNull null
                val end = it.getTimestamp("Fin")?.toDate()?.time ?: return@mapNotNull null
                if (it.getString("AgendaId") == null || it.getString("Estado") != "libre" || start <= now.toEpochMilli() || start < weekStart ||
                    end > weekEnd || end - start != 2400000L ||
                    it.get("Agenda") != (if (agendaId(it) == "semanal") legacy.data else agenda.data) ||
                    (agendaId(it) != "semanal" && agendaId(it) != monday.toString()) ||
                    it.getString("ZonaHoraria") != zoneName) null else HorarioDisponible(start, end)
            }.sortedBy { it.inicioMillis }
        SemanaDisponible(weekStart, slots, zoneName)
    }

    private fun validarEditable(turno: DocumentSnapshot) {
        check(turno.getString("Estado")?.lowercase() in listOf("confirmado", "pendiente") &&
            (turno.getTimestamp("Inicio")?.toDate()?.time ?: 0L) > System.currentTimeMillis()) {
            "Solo podés cambiar turnos activos que todavía no comenzaron."
        }
    }

    override suspend fun cancelar(turnoId: String): AppResult<Unit> = result {
        val user = uid()
        val ref = firestore.collection("turnos").document(turnoId)
        firestore.runTransaction { tx ->
            val turno = tx.get(ref)
            check(turno.getString("ClienteId") == user) { "El turno no te pertenece." }
            if (turno.getString("Estado") != "cancelado") {
                validarEditable(turno)
                val horario = firestore.collection("horarios").document(requireNotNull(turno.getString("HorarioId")))
                val slot = tx.get(horario)
                val barber = firestore.collection("barberos").document(requireNotNull(turno.getString("BarberoId")))
                val agenda = tx.get(barber.collection("agenda").document(agendaId(slot)))
                val barberDoc = tx.get(barber)
                check(slot.getString("TurnoId") == turnoId && slot.getString("Estado") == "ocupado")
                tx.update(horario, mapOf("Estado" to if (slot.get("Agenda") == agenda.data &&
                    slot.getString("ZonaHoraria") == barberDoc.getString("ZonaHoraria")) "libre" else "bloqueado",
                    "TurnoId" to FieldValue.delete()))
                tx.update(ref, mapOf("Estado" to "cancelado", "CanceladoEn" to FieldValue.serverTimestamp()))
            }
        }.awaitReserva()
        Unit
    }

    override suspend fun modificar(turnoId: String, solicitud: SolicitudReserva) = guardarReserva(solicitud, turnoId)
    override suspend fun reservar(solicitud: SolicitudReserva) = guardarReserva(solicitud)

    private suspend fun guardarReserva(solicitud: SolicitudReserva, editarId: String? = null): AppResult<String> = result {
        val uid = uid()
        require(solicitud.solicitudId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Solicitud inválida." }
        val turno = firestore.collection("turnos").document(editarId ?: solicitud.solicitudId)
        val horario = firestore.collection("horarios").document(slotId(solicitud.barberoId, solicitud.inicioMillis))
        val cliente = firestore.collection("clientes").document(uid)
        val barber = firestore.collection("barberos").document(solicitud.barberoId)
        val service = firestore.collection("servicios").document(solicitud.servicioId)
        val referenciaUrl = solicitud.referenciaLocal?.let { local ->
            val key = "$uid/${solicitud.solicitudId}/$local"
            referenciasPendientes[key] ?: requireNotNull(subirReferencia) {
                "No se pudo adjuntar la referencia. Podés reservar sin foto."
            }.invoke(local).also { referenciasPendientes[key] = it }
        }
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Iniciá sesión nuevamente." }
        firestore.runTransaction { tx ->
            val previous = tx.get(turno)
            if (previous.exists() && editarId == null) {
                check(previous.getString("ClienteId") == uid && previous.getString("BarberoId") == solicitud.barberoId &&
                    previous.getString("ServicioId") == solicitud.servicioId && previous.getString("HorarioId") == horario.id) {
                    "La solicitud corresponde a otra reserva."
                }
                turno.id
            } else {
                val slot = tx.get(horario)
                if (editarId != null) {
                    check(previous.getString("ClienteId") == uid) { "El turno no te pertenece." }
                    validarEditable(previous)
                }
                val anteriorRef = if (editarId != null) firestore.collection("horarios").document(requireNotNull(previous.getString("HorarioId"))) else null
                val anterior = anteriorRef?.let { tx.get(it) }
                val anteriorBarber = anterior?.let { firestore.collection("barberos").document(requireNotNull(it.getString("BarberoId"))) }
                val anteriorAgenda = anteriorBarber?.let { tx.get(it.collection("agenda").document(agendaId(requireNotNull(anterior)))) }
                val anteriorBarberDoc = anteriorBarber?.let { tx.get(it) }
                val profile = tx.get(cliente)
                val barberDoc = tx.get(barber)
                val serviceDoc = tx.get(service)
                val agendaDoc = tx.get(barber.collection("agenda").document(agendaId(slot)))
                check(profile.exists()) { "Completá tu registro como cliente." }
                check(barberDoc.getBoolean("Estado") == true && enabled(serviceDoc) && serviceDoc.getLong("Duracion") == 40L &&
                    serviceDoc.getString("BarberoId") == solicitud.barberoId) {
                    "El barbero o el servicio ya no están disponibles."
                }
                check(slot.exists() && (slot.getString("Estado") == "libre" || (editarId != null && slot.getString("TurnoId") == turno.id)) && slot.get("Agenda") == agendaDoc.data &&
                    slot.getString("ZonaHoraria") == barberDoc.getString("ZonaHoraria") &&
                    (slot.getTimestamp("Inicio")?.toDate()?.time ?: 0) > System.currentTimeMillis()) {
                    "Ese horario ya no está disponible. Actualizá la semana."
                }
                if (anteriorRef != null && anteriorRef.id != horario.id) {
                    check(anterior?.getString("TurnoId") == turno.id && anterior.getString("Estado") == "ocupado")
                    tx.update(anteriorRef, mapOf("Estado" to if (anterior.get("Agenda") == anteriorAgenda?.data &&
                        anterior.getString("ZonaHoraria") == anteriorBarberDoc?.getString("ZonaHoraria")) "libre" else "bloqueado",
                        "TurnoId" to FieldValue.delete()))
                }
                if (anteriorRef?.id != horario.id) tx.update(horario, mapOf("Estado" to "ocupado", "TurnoId" to turno.id))
                tx.set(turno, mapOf("ClienteId" to uid, "BarberoId" to solicitud.barberoId,
                    "ServicioId" to solicitud.servicioId, "HorarioId" to horario.id,
                    "ReferenciaCorteUrl" to (if (editarId != null && !solicitud.cambiarReferencia)
                        previous.getString("ReferenciaCorteUrl") else referenciaUrl),
                    "Inicio" to slot.getTimestamp("Inicio"), "Fin" to slot.getTimestamp("Fin"),
                    "Estado" to "confirmado", "CreadoEn" to (if (editarId != null) previous.get("CreadoEn") else FieldValue.serverTimestamp())))
                if (profile.getString("Nombre") == "") tx.update(cliente, "Nombre", solicitud.nombreCliente.trim())
                turno.id
            }
        }.awaitReserva()
    }

    private suspend fun <T> result(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        val message = if (e is FirebaseFirestoreException) when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No tenés permiso. Revisá las reglas publicadas de Firestore."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Necesitás conexión para consultar o reservar turnos."
            else -> "No se pudo confirmar la operación. Actualizá y reintentá."
        } else e.message ?: "No se pudo completar la operación."
        AppResult.Error(if (e is FirebaseFirestoreException && e.code in listOf(
                FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
            DomainError.Network else DomainError.Unknown(message))
    }
}

internal fun AgendaSemanal.contieneTurno(inicio: ZonedDateTime, fin: ZonedDateTime): Boolean {
    val dia = dias.getOrNull(inicio.dayOfWeek.value - 1) ?: return false
    return dia.habilitado && inicio.toLocalDate() == fin.toLocalDate() && dia.franjas.any {
        inicio.toLocalTime() >= LocalTime.parse(it.inicio) && fin.toLocalTime() <= LocalTime.parse(it.fin)
    }
}

private suspend fun <T> Task<T>.awaitReserva(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) {
        if (continuation.isActive) {
            if (it.isSuccessful) continuation.resume(it.result)
            else continuation.resumeWithException(it.exception ?: IllegalStateException("Operación fallida"))
        }
    }
}
