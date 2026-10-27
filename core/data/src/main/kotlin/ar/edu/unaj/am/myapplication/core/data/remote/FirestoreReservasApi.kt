package ar.edu.unaj.am.myapplication.core.data.remote

import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.crearDiaAtencion
import ar.edu.unaj.am.myapplication.core.domain.usecase.PoliticaReservas
import ar.edu.unaj.am.myapplication.core.domain.usecase.nombreClienteReservaValido
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.time.*
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reservas para el plan Spark: transacciones móviles y validación de intervalos en las reglas. */
class FirestoreReservasApi(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val reloj: Clock = Clock.systemUTC()
) : ReservasApi {
    private val politica = PoliticaReservas(reloj)
    override suspend fun ejecutar(operacion: String, datos: Map<String, Any?>): Map<String, Any?> {
        val uid = requireNotNull(auth.currentUser?.uid) { "Iniciá sesión." }
        return when (operacion) {
            "publicarAgenda" -> publicar(uid, datos)
            "consultarSemana" -> consultar(uid, datos)
            "reservarTurno" -> guardar(uid, datos, false)
            "modificarTurno" -> guardar(uid, datos, true)
            "cancelarTurno" -> cancelar(uid, identificador(datos["turnoId"]))
            else -> error("Operación de reserva desconocida.")
        }
    }
    private fun identificador(value: Any?): String = (value as? String)?.also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Identificador inválido." }
    } ?: error("Falta el identificador.")
    private fun barber(id: String) = firestore.collection("barberos").document(id)
    private fun diaRef(id: String) = firestore.collection("disponibilidad").document(id)
    private fun turnoRef(id: String) = firestore.collection("turnos").document(id)
    private fun diaId(barbero: String, inicio: Long, zona: ZoneId) =
        "${barbero}_${Instant.ofEpochMilli(inicio).atZone(zona).toLocalDate().atStartOfDay(zona).toInstant().toEpochMilli()}"
    private fun inicio(t: DocumentSnapshot) = requireNotNull(t.getTimestamp("Inicio")) { "El turno no tiene fecha de inicio." }.toDate().time
    private fun fin(t: DocumentSnapshot) = requireNotNull(t.getTimestamp("Fin")) { "El turno no tiene fecha de fin." }.toDate().time
    private fun activo(t: DocumentSnapshot) = estadoTurno(t.get("Estado").toString()) != EstadoTurno.CANCELADO
    private fun editable(t: DocumentSnapshot) {
        requireDomain(t.exists(), ErrorReason.BOOKING_NOT_OWNED)
        politica.validarEditable(t.get("Estado").toString(), inicio(t))
    }
    private fun ocupacion(doc: DocumentSnapshot) = OcupacionDia(inicio(doc),
        requireNotNull(doc.getString("Atencion")) { "El barbero debe publicar el día." },
        requireNotNull(doc.getString("Ocupacion")) { "El barbero debe publicar el día." })
    private fun habilitado(b: DocumentSnapshot): ZoneId {
        requireDomain(b.exists() && b.getBoolean("Estado") == true,ErrorReason.BARBER_DISABLED)
        return ZoneId.of(requireNotNull(b.getString("ZonaHoraria")))
    }
    private fun duracion(s: DocumentSnapshot, barberoId: String): Int {
        requireDomain(s.exists() && (s.get("Estado") ?: s.get("Activo")) == true && s.getString("BarberoId") == barberoId,ErrorReason.SERVICE_SELECTION)
        return politica.duracion(requireNotNull(s.getLong("Duracion")).toInt())
    }
    private fun lunes(zona: ZoneId, offset: Int) = politica.lunes(zona,offset)
    private fun vigente(tx: Transaction, doc: DocumentSnapshot, b: DocumentSnapshot) {
        requireDomain(doc.exists() && doc.getString("ZonaHoraria") == b.getString("ZonaHoraria"),ErrorReason.SCHEDULE_REPUBLISH)
        val agenda = tx.get(b.reference.collection("agenda").document(requireNotNull(doc.getString("AgendaId"))))
        requireDomain(doc.get("Agenda") == agenda.data,ErrorReason.SCHEDULE_REPUBLISH)
    }

    private suspend fun publicar(uid: String, datos: Map<String, Any?>): Map<String, Any?> {
        // Primero cierra las escrituras de las apps antiguas; luego la consulta ya no puede perder una reserva nueva.
        firestore.runTransaction { tx ->
            val b = tx.get(barber(uid)); habilitado(b)
            if (b.getLong("ReservasVersion") != 3L) tx.update(b.reference, "ReservasVersion", 3)
        }.esperar()
        val b = barber(uid).get(Source.SERVER).esperar()
        val zona = habilitado(b)
        val fecha = LocalDate.parse(datos["inicioSemana"] as? String ?: error("Volvé a cargar la semana."))
        politica.validarSemana(fecha,zona)
        val fuente = barber(uid).collection("agenda").document(fecha.toString())
        val mapa = datos["agenda"] as? Map<*, *> ?: error("Falta la agenda.")
        val nombres = listOf("Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado")
        val turnos = firestore.collection("turnos").whereEqualTo("BarberoId", uid).get(Source.SERVER).esperar().documents
            .filter { activo(it) && fin(it) > reloj.millis() }
        var franjas = 0
        for ((offset, nombre) in nombres.withIndex()) {
            val configuracion = mapa[nombre] as? Map<*, *> ?: error("Configurá $nombre.")
            val rangos = (configuracion["Franjas"] as? List<*>)?.map { value ->
                val rango = value as? Map<*, *> ?: error("Franja inválida.")
                FranjaAtencion(rango["Inicio"] as String, rango["Fin"] as String)
            } ?: error("Configurá las franjas de $nombre.")
            val habilitado = configuracion["Estado"] as? Boolean ?: error("Configurá $nombre.")
            val nuevo = crearDiaAtencion(fecha.plusDays(offset.toLong()), zona, DiaAtencion(habilitado, rangos))
            val referencia = diaRef("${uid}_${nuevo.inicioMillis}")
            val propios = turnos.filter { inicio(it) >= nuevo.inicioMillis && inicio(it) < nuevo.inicioMillis + nuevo.atencion.length * 60000L }
            // La inicialización incluye reservas anteriores, aun si ahora quedan fuera de una franja.
            var inicial = nuevo.copy(atencion = "1".repeat(nuevo.atencion.length))
            for (t in propios) {
                requireDomain(t.getString("DiaId") == null || t.getString("DiaId") == referencia.id,
                    ErrorReason.SCHEDULE_ZONE_BOOKINGS)
                inicial = inicial.ocupar(inicio(t), fin(t))
            }
            firestore.runTransaction { tx ->
                val actual = tx.get(referencia)
                val barbero = tx.get(barber(uid)); habilitado(barbero)
                check(barbero.getString("ZonaHoraria") == zona.id && tx.get(fuente).data == mapa) {
                    "La agenda cambió. Volvé a cargarla."
                }
                val campos = mapOf("BarberoId" to uid, "Inicio" to Timestamp(java.util.Date(nuevo.inicioMillis)),
                    "Fin" to Timestamp(java.util.Date(nuevo.inicioMillis + nuevo.atencion.length * 60000L)),
                    "Atencion" to nuevo.atencion,
                    "Ocupacion" to if (actual.exists()) ocupacion(actual).ocupacion else inicial.ocupacion,
                    "Agenda" to mapa, "AgendaId" to fecha.toString(), "ZonaHoraria" to zona.id)
                tx.set(referencia, campos)
            }.esperar()
            for (t in propios) {
                if (politica.cancelarAlPublicar(t.get("Estado").toString(),inicio(t),fin(t),nuevo))
                    cancelar(uid, t.id)
            }
            if (habilitado) franjas += rangos.size
        }
        return mapOf("franjasPublicadas" to franjas)
    }

    private suspend fun consultar(uid: String, datos: Map<String, Any?>): Map<String, Any?> {
        val id = identificador(datos["barberoId"])
        val b = barber(id).get(Source.SERVER).esperar()
        val zona = habilitado(b)
        val minutos = duracion(firestore.collection("servicios").document(identificador(datos["servicioId"]))
            .get(Source.SERVER).esperar(), id)
        val fecha = lunes(zona, (datos["semanaOffset"] as? Number)?.toInt() ?: 0)
        val propio = (datos["turnoId"] as? String)?.let {
            turnoRef(identificador(it)).get(Source.SERVER).esperar().also { t ->
                requireDomain(t.getString("ClienteId") == uid,ErrorReason.BOOKING_NOT_OWNED); editable(t)
            }
        }
        val horarios = (0L..5L).flatMap { offset ->
            val referencia = diaRef("${id}_${fecha.plusDays(offset).atStartOfDay(zona).toInstant().toEpochMilli()}")
            firestore.runTransaction { tx ->
                val dia = tx.get(referencia)
                vigente(tx, dia, tx.get(barber(id)))
                var mapa = ocupacion(dia)
                if (propio != null && propio.getString("BarberoId") == id && inicio(propio) >= mapa.inicioMillis &&
                    inicio(propio) < mapa.inicioMillis + mapa.atencion.length * 60000L) mapa = mapa.liberar(inicio(propio), fin(propio))
                val inicioOriginal = propio?.takeIf { it.getString("BarberoId") == id }?.let { inicio(it) }
                mapa.disponibles(minutos, reloj.millis(), inicioOriginal)
            }.esperar()
        }
        return mapOf("inicioSemanaMillis" to fecha.atStartOfDay(zona).toInstant().toEpochMilli(), "zonaHoraria" to zona.id,
            "horarios" to horarios.map { mapOf("inicioMillis" to it.inicioMillis, "finMillis" to it.finMillis) })
    }

    private suspend fun guardar(uid: String, datos: Map<String, Any?>, editar: Boolean): Map<String, Any?> {
        val id = identificador(if (editar) datos["turnoId"] else datos["solicitudId"])
        val barberoId = identificador(datos["barberoId"])
        val servicioId = identificador(datos["servicioId"])
        val inicio = requireNotNull(datos["inicioMillis"] as? Number).toLong()
        return firestore.runTransaction { tx ->
            val anterior = tx.get(turnoRef(id))
            if (!editar && anterior.exists()) {
                check(anterior.getString("ClienteId") == uid && anterior.getString("BarberoId") == barberoId &&
                    anterior.getString("ServicioId") == servicioId && inicio(anterior) == inicio && activo(anterior)) {
                    "La solicitud corresponde a otra reserva."
                }
                mapOf("turnoId" to id)
            } else {
                check(!tx.get(barber(uid)).exists()) { "Solo un cliente puede reservar." }
                val cliente = tx.get(firestore.collection("clientes").document(uid))
                check(cliente.exists()) { "Completá tu perfil de cliente." }
                if (editar) { requireDomain(anterior.getString("ClienteId") == uid,ErrorReason.BOOKING_NOT_OWNED); editable(anterior) }
                val b = tx.get(barber(barberoId)); val zona = habilitado(b)
                val minutos = duracion(tx.get(firestore.collection("servicios").document(servicioId)), barberoId)
                val fin = inicio + minutos * 60000L
                requireDomain((datos["finMillisEsperado"] as? Number)?.toLong() == fin,ErrorReason.SERVICE_CHANGED)
                politica.validarInicioReserva(inicio)
                val fecha = politica.semanaDe(inicio,zona)
                politica.validarSemana(fecha,zona)
                val nuevoRef = diaRef(diaId(barberoId, inicio, zona))
                val nuevo = tx.get(nuevoRef); vigente(tx, nuevo, b)
                val anteriorId = if (editar) anterior.getString("DiaId") ?: diaId(requireNotNull(anterior.getString("BarberoId")),
                    inicio(anterior), habilitado(tx.get(barber(requireNotNull(anterior.getString("BarberoId")))))) else null
                val previo = if (anteriorId != null && anteriorId != nuevoRef.id) tx.get(diaRef(anteriorId)) else null
                val nombre = (datos["nombreCliente"] as? String).orEmpty().trim()
                requireDomain(nombreClienteReservaValido(nombre), ErrorReason.BOOKING_FIELDS)
                var mapa = ocupacion(nuevo)
                if (anteriorId == nuevoRef.id) mapa = mapa.liberar(inicio(anterior), fin(anterior))
                val ocupado = mapa.ocupar(inicio, fin)
                val liberado = previo?.let { ocupacion(it).liberar(inicio(anterior), fin(anterior)) }
                val campos = mutableMapOf<String, Any>("ClienteId" to uid, "BarberoId" to barberoId, "ServicioId" to servicioId,
                    "Inicio" to Timestamp(java.util.Date(inicio)), "Fin" to Timestamp(java.util.Date(fin)),
                    "Estado" to "confirmado", "HorarioId" to "${barberoId}_$inicio", "DiaId" to nuevoRef.id,
                    "AgendaId" to requireNotNull(nuevo.getString("AgendaId")), "ReservaVersion" to 3,
                    "DuracionMinutos" to minutos, "CreadoEn" to if (editar) requireNotNull(anterior.get("CreadoEn")) else FieldValue.serverTimestamp())
                if (anteriorId != null) campos["DiaAnteriorId"] = anteriorId
                val foto = if (editar && datos["cambiarReferencia"] != true) anterior.getString("ReferenciaCorteUrl")
                    else datos["referenciaCorteUrl"] as? String
                if (foto != null) campos["ReferenciaCorteUrl"] = foto
                tx.set(turnoRef(id), campos)
                tx.update(nuevoRef, mapOf("Ocupacion" to ocupado.ocupacion, "UltimoTurnoId" to id))
                if (previo != null && liberado != null) tx.update(previo.reference, mapOf("Ocupacion" to liberado.ocupacion, "UltimoTurnoId" to id))
                if (cliente.getString("Nombre").isNullOrBlank()) tx.update(cliente.reference, "Nombre", nombre)
                mapOf("turnoId" to id)
            }
        }.esperar()
    }

    private suspend fun cancelar(uid: String, id: String): Map<String, Any?> = firestore.runTransaction { tx ->
        val t = tx.get(turnoRef(id))
        val comoBarbero = t.getString("BarberoId") == uid
        requireDomain(t.exists() && (comoBarbero || t.getString("ClienteId") == uid),ErrorReason.BOOKING_NOT_OWNED)
        if (estadoTurno(t.get("Estado").toString()) == EstadoTurno.CANCELADO) mapOf("cancelado" to true) else {
            editable(t)
            val barberoId = requireNotNull(t.getString("BarberoId"))
            val b = tx.get(barber(barberoId))
            if (comoBarbero) habilitado(b)
            val zona = ZoneId.of(requireNotNull(b.getString("ZonaHoraria")))
            val referencia = diaRef(t.getString("DiaId") ?: diaId(barberoId, inicio(t), zona))
            val dia = tx.get(referencia)
            requireDomain(dia.exists(),ErrorReason.SCHEDULE_REPUBLISH)
            val libre = ocupacion(dia).liberar(inicio(t), fin(t))
            tx.update(t.reference, mapOf("Estado" to "cancelado", "CanceladoEn" to FieldValue.serverTimestamp(),
                "CanceladoPor" to uid, "DiaId" to referencia.id, "DiaAnteriorId" to referencia.id))
            tx.update(referencia, mapOf("Ocupacion" to libre.ocupacion, "UltimoTurnoId" to id))
            mapOf("cancelado" to true)
        }
    }.esperar()
}

private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(Executor { it.run() }) { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación fallida."))
        }
    }
}
