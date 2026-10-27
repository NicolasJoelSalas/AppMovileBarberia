package ar.edu.unaj.am.myapplication.core.domain.model

/** Un carácter por minuto. No contiene identidades ni datos de los clientes. */
data class OcupacionDia(val inicioMillis: Long, val atencion: String, val ocupacion: String) {
    init {
        requireDomain(atencion.isNotEmpty() && atencion.length == ocupacion.length &&
            atencion.all { it == '0' || it == '1' } && ocupacion.all { it == '0' || it == '1' }, ErrorReason.AVAILABILITY_INVALID)
    }

    fun indices(inicio: Long, fin: Long): IntRange {
        requireDomain(inicio >= inicioMillis && fin > inicio && fin <= inicioMillis + atencion.length * 60000L &&
            (inicio - inicioMillis) % 60000L == 0L && (fin - inicioMillis) % 60000L == 0L, ErrorReason.BOOKING_OUTSIDE_RANGE)
        return ((inicio - inicioMillis) / 60000L).toInt() until ((fin - inicioMillis) / 60000L).toInt()
    }

    fun ocupar(inicio: Long, fin: Long): OcupacionDia {
        val rango = indices(inicio, fin)
        requireDomain(rango.all { atencion[it] == '1' }, ErrorReason.BOOKING_OUTSIDE_RANGE)
        requireDomain(rango.all { ocupacion[it] == '0' }, ErrorReason.BOOKING_TAKEN)
        return copy(ocupacion = ocupacion.replaceRange(rango.first, rango.last + 1, "1".repeat(rango.count())))
    }

    fun liberar(inicio: Long, fin: Long): OcupacionDia {
        val rango = indices(inicio, fin)
        requireDomain(rango.all { ocupacion[it] == '1' }, ErrorReason.AVAILABILITY_INVALID)
        return copy(ocupacion = ocupacion.replaceRange(rango.first, rango.last + 1, "0".repeat(rango.count())))
    }

    fun disponibles(duracion: Int, ahoraMillis: Long, inicioPreferido: Long? = null): List<HorarioDisponible> {
        requireDomain(duracion in 5..240, ErrorReason.SERVICE_FIELDS)
        // Al editar, conserva el inicio original si todavía entra, sin ofrecer
        // otros intervalos que se superpongan con esa opción.
        if (inicioPreferido != null && inicioPreferido > ahoraMillis) {
            val finPreferido = inicioPreferido + duracion * 60000L
            val resto = runCatching { ocupar(inicioPreferido, finPreferido) }.getOrNull()
            if (resto != null) return (resto.disponibles(duracion, ahoraMillis) +
                HorarioDisponible(inicioPreferido, finPreferido)).sortedBy { it.inicioMillis }
        }
        return buildList {
            var minuto = 0
            while (minuto < atencion.length) {
                if (atencion[minuto] != '1' || ocupacion[minuto] != '0') { minuto++; continue }
                val comienzo = minuto
                while (minuto < atencion.length && atencion[minuto] == '1' && ocupacion[minuto] == '0') minuto++
                var candidato = comienzo
                while (candidato + duracion <= minuto) {
                    val inicio = inicioMillis + candidato * 60000L
                    if (inicio > ahoraMillis) add(HorarioDisponible(inicio, inicio + duracion * 60000L))
                    candidato += duracion
                }
            }
        }
    }
}
