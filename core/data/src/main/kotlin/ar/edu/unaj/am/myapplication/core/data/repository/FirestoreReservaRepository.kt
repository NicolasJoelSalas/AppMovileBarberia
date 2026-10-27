package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal
import ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import com.google.android.gms.tasks.Task
import ar.edu.unaj.am.myapplication.core.data.remote.ReservasApi
import ar.edu.unaj.am.myapplication.core.data.remote.FirestoreReservasApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Traduce documentos y ejecuta reservas con transacciones de Firestore, sin Cloud Functions. */
class FirestoreReservaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val sesionLocal: SesionClienteLocal? = null,
    private val sesionBarberoLocal: SesionBarberoLocal? = null,
    private val subirReferencia: (suspend (String) -> String)? = null,
    private val reloj: Clock = Clock.systemUTC(),
    private val api: ReservasApi = FirestoreReservasApi(firestore, auth, reloj)
) : ReservaRepository {
    private val politica = ar.edu.unaj.am.myapplication.core.domain.usecase.PoliticaReservas(reloj)
    // Mantiene la misma subida cuando una confirmación falla y se reintenta.
    private val referenciasPendientes = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun uid() = requireNotNull(auth.currentUser?.uid) { "Iniciá sesión." }
    private fun monday(zone: String, offset: Int): LocalDate = politica.lunes(ZoneId.of(zone),offset)
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
                .get(Source.SERVER).awaitReserva().documents.map { Barbero(it.id, it.getString("Nombre").orEmpty(), it.getString("ZonaHoraria") ?: "America/Argentina/Buenos_Aires") }
            val servicios = firestore.collection("servicios").get(Source.SERVER).awaitReserva().documents
                .filter { enabled(it) && it.getLong("Duracion") in 5L..240L &&
                    barberos.any { barbero -> barbero.id == it.getString("BarberoId") } }
                .map { Servicio(it.id, it.getString("NombreCorte").orEmpty(), requireNotNull(it.getLong("Duracion")).toInt(), it.getString("BarberoId"),
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

    override suspend fun publicarSemana(agenda: AgendaSemanal): AppResult<Int> = result {
        uid()
        val fields = listOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado")
            .zip(agenda.dias).associate { (name, day) -> name to mapOf(
                "Estado" to day.habilitado,
                "Franjas" to day.franjas.map { mapOf("Inicio" to it.inicio, "Fin" to it.fin) }) }
        val respuesta = api.ejecutar("publicarAgenda", mapOf("inicioSemana" to agenda.inicioSemana, "agenda" to fields))
        requireNotNull((respuesta["franjasPublicadas"] as? Number)?.toInt()) { "No se confirmó la publicación." }
    }

    override suspend fun consultarSemana(barberoId: String, servicioId: String, semanaOffset: Int,
        turnoId: String?): AppResult<SemanaDisponible> = result {
        uid()
        val respuesta = api.ejecutar("consultarSemana", mapOf("barberoId" to barberoId,
            "servicioId" to servicioId, "semanaOffset" to semanaOffset, "turnoId" to turnoId))
        val horarios = requireNotNull(respuesta["horarios"] as? List<*>) { "No se recibieron los horarios." }.map {
            val horario = requireNotNull(it as? Map<*, *>)
            HorarioDisponible(requireNotNull(horario["inicioMillis"] as? Number).toLong(),
                requireNotNull(horario["finMillis"] as? Number).toLong())
        }
        SemanaDisponible(requireNotNull(respuesta["inicioSemanaMillis"] as? Number).toLong(), horarios,
            requireNotNull(respuesta["zonaHoraria"] as? String))
    }

    override suspend fun cancelar(turnoId: String): AppResult<Unit> = result {
        uid()
        check(api.ejecutar("cancelarTurno", mapOf("turnoId" to turnoId))["cancelado"] == true) {
            "No se confirmó la cancelación."
        }
    }
    override suspend fun modificar(turnoId: String, solicitud: SolicitudReserva) = guardarReserva(solicitud, turnoId)
    override suspend fun reservar(solicitud: SolicitudReserva) = guardarReserva(solicitud)

    private suspend fun guardarReserva(solicitud: SolicitudReserva, editarId: String? = null): AppResult<String> = result {
        val user = uid()
        val referenciaUrl = solicitud.referenciaLocal?.let { local ->
            val key = "$user/${solicitud.solicitudId}/$local"
            referenciasPendientes[key] ?: requireNotNull(subirReferencia) {
                "No se pudo adjuntar la referencia. Podés reservar sin foto."
            }.invoke(local).also { referenciasPendientes[key] = it }
        }
        check(auth.currentUser?.uid == user) { "La sesión cambió. Iniciá sesión nuevamente." }
        val respuesta = api.ejecutar(if (editarId == null) "reservarTurno" else "modificarTurno", mapOf(
            "solicitudId" to solicitud.solicitudId, "turnoId" to editarId,
            "barberoId" to solicitud.barberoId, "servicioId" to solicitud.servicioId,
            "inicioMillis" to solicitud.inicioMillis, "finMillisEsperado" to solicitud.finMillisEsperado,
            "nombreCliente" to solicitud.nombreCliente, "referenciaCorteUrl" to referenciaUrl,
            "cambiarReferencia" to solicitud.cambiarReferencia))
        requireNotNull(respuesta["turnoId"] as? String) { "No se confirmó la reserva." }
    }

    private suspend fun <T> result(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { AppResult.Error(ar.edu.unaj.am.myapplication.core.data.errors.errorRemoto(e,ErrorReason.RESERVATION_OPERATION)) }
}

private suspend fun <T> Task<T>.awaitReserva(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) {
        if (continuation.isActive) {
            if (it.isSuccessful) continuation.resume(it.result)
            else continuation.resumeWithException(it.exception ?: IllegalStateException("Operación fallida"))
        }
    }
}
