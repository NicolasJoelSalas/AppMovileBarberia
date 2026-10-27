package ar.edu.unaj.am.myapplication.core.testing

import ar.edu.unaj.am.myapplication.core.domain.model.ServicioBarbero

/** Datos deterministas; cada prueba puede cambiar solamente los campos que necesita. */
fun servicioBarbero(
    id: String = "",
    nombre: String = "Corte",
    precioCentavos: Long = 1_250_050,
    duracionMinutos: Int = 40,
    activo: Boolean = true
) = ServicioBarbero(id, nombre, precioCentavos, duracionMinutos, activo)
