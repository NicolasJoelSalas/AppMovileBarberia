package ar.edu.unaj.am.myapplication.core.domain.model

/** Disponibilidad de acciones de negocio, independiente de los botones y del rol de la pantalla. */
data class AccionesTurno(
    val editable: Boolean = false,
    val completable: Boolean = false,
    val admiteFinalizacion: Boolean = false,
    val admiteRecordatorios: Boolean = false,
    val recordatorioHabilitado: Boolean = false,
    val permiteConsultarRuta: Boolean = false
)
