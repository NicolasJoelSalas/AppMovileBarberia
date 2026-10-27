package ar.edu.unaj.am.myapplication.core.domain.model

/** Un carácter por minuto. No contiene identidades ni datos de los clientes. */
data class OcupacionDia(val inicioMillis: Long, val atencion: String, val ocupacion: String) {
    init {
        require(atencion.isNotEmpty() && atencion.length == ocupacion.length &&
            atencion.all { it == '0' || it == '1' } && ocupacion.all { it == '0' || it == '1' }) {
            "La disponibilidad del día es inválida. El barbero debe volver a publicarla."
        }
    }

    fun indices(inicio: Long, fin: Long): IntRange {
        require(inicio >= inicioMillis && fin > inicio && fin <= inicioMillis + atencion.length * 60000L &&
            (inicio - inicioMillis) % 60000L == 0L && (fin - inicioMillis) % 60000L == 0L) {
            "El servicio no entra en el día elegido."
        }
        return ((inicio - inicioMillis) / 60000L).toInt() until ((fin - inicioMillis) / 60000L).toInt()
    }

    fun ocupar(inicio: Long, fin: Long): OcupacionDia {
        val rango = indices(inicio, fin)
        require(rango.all { atencion[it] == '1' }) { "El servicio no entra en la franja elegida." }
        require(rango.all { ocupacion[it] == '0' }) { "Ese horario acaba de ocuparse. Elegí otro." }
        return copy(ocupacion = ocupacion.replaceRange(rango.first, rango.last + 1, "1".repeat(rango.count())))
    }

    fun liberar(inicio: Long, fin: Long): OcupacionDia {
        val rango = indices(inicio, fin)
        require(rango.all { ocupacion[it] == '1' }) { "La reserva no coincide con la ocupación guardada." }
        return copy(ocupacion = ocupacion.replaceRange(rango.first, rango.last + 1, "0".repeat(rango.count())))
    }

    fun disponibles(duracion: Int, ahoraMillis: Long, inicioPreferido: Long? = null): List<HorarioDisponible> {
        require(duracion in 5..240) { "La duración debe ser de 5 a 240 minutos." }
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
