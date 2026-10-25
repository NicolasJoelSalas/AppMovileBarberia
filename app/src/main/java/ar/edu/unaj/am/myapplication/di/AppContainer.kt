package ar.edu.unaj.am.myapplication.di

import android.content.Context
import ar.edu.unaj.am.myapplication.core.data.images.PreparadorImagenTrabajo
import ar.edu.unaj.am.myapplication.core.data.repository.FirestorePublicacionRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObservarPublicacionesUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.PublicarTrabajoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.EliminarPublicacionUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ModificarDescripcionPublicacionUseCase
import ar.edu.unaj.am.myapplication.BuildConfig
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreTurnoRepository
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreClienteRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.ClienteRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.EnsureClienteProfileUseCase
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreReservaRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.CargarCatalogoReservasUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ConsultarSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ReservarTurnoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.PublicarSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerMiAgendaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.GuardarYPublicarAgendaUseCase
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import ar.edu.unaj.am.myapplication.core.network.NetworkFactory
import ar.edu.unaj.am.myapplication.core.auth.repository.FirebaseAuthRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LogoutUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginWithGoogleUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegisterUseCase
import ar.edu.unaj.am.myapplication.core.data.repository.FirestorePerfilRepository
import ar.edu.unaj.am.myapplication.core.database.AppDatabase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerMiPerfilUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ActualizarMiNombreUseCase


class AppContainer(context: Context) {
    private val ubicacionRepository = ar.edu.unaj.am.myapplication.core.data.repository.FirestoreUbicacionBarberiaRepository()
    val obtenerMiUbicacion = ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerMiUbicacionUseCase(ubicacionRepository)
    val obtenerUbicacionBarberia = ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerUbicacionBarberiaUseCase(ubicacionRepository)
    val guardarMiUbicacion = ar.edu.unaj.am.myapplication.core.domain.usecase.GuardarMiUbicacionUseCase(ubicacionRepository)
    private val rutaRepository = ar.edu.unaj.am.myapplication.core.data.repository.OsrmRutaRepository(
        NetworkFactory.createRutasApi(BuildConfig.RUTAS_BASE_URL))
    val calcularRutaBarberia = ar.edu.unaj.am.myapplication.core.domain.usecase.CalcularRutaBarberiaUseCase(rutaRepository)
    private val sesionClienteLocal = ar.edu.unaj.am.myapplication.core.data.session.SesionClienteLocal(context)
    private val sesionBarberoLocal = ar.edu.unaj.am.myapplication.core.data.session.SesionBarberoLocal(context)
    private val perfilRepository = FirestorePerfilRepository()
    val obtenerMiPerfil = ObtenerMiPerfilUseCase(perfilRepository)
    val actualizarMiNombre = ActualizarMiNombreUseCase(perfilRepository)

    private val databaseFile = context.applicationContext
        .getDatabasePath("barberia_database")
        .also { file ->
            file.parentFile?.mkdirs()
        }

    private val database = AppDatabase.obtener {
        androidx.room3.Room.databaseBuilder<AppDatabase>(name = databaseFile.absolutePath)
            .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
            .addMigrations(ar.edu.unaj.am.myapplication.core.database.migration.MIGRATION_1_2,
                ar.edu.unaj.am.myapplication.core.database.migration.MIGRATION_2_3)
            .build()
    }
    private val turnoDao = database.turnoDao()
    private val publicacionesLocales = ar.edu.unaj.am.myapplication.core.data.repository.PublicacionesClienteLocal(
        database.publicacionDao(),
        ar.edu.unaj.am.myapplication.core.data.images.ImagenesPublicacionesLocales(
            java.io.File(context.applicationContext.noBackupFilesDir, "publicaciones")),
        usuarioActual = { clienteActualId() }
    )


    private val publicacionRepository = FirestorePublicacionRepository(
        PreparadorImagenTrabajo(context.applicationContext), NetworkFactory.createCloudinaryApi(),
        BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET,
        local = publicacionesLocales, sesionLocal = sesionClienteLocal)
    val observarPublicaciones = ObservarPublicacionesUseCase(publicacionRepository)
    val publicarTrabajo = PublicarTrabajoUseCase(publicacionRepository)
    val eliminarPublicacion = EliminarPublicacionUseCase(publicacionRepository)
    val modificarDescripcionPublicacion = ModificarDescripcionPublicacionUseCase(publicacionRepository)

    private val reservaRepository: ReservaRepository = FirestoreReservaRepository(
        sesionLocal = sesionClienteLocal, sesionBarberoLocal = sesionBarberoLocal,
        subirReferencia = ar.edu.unaj.am.myapplication.core.data.images.SubidorReferenciaCorte(
            PreparadorImagenTrabajo(context.applicationContext), NetworkFactory.createCloudinaryApi(),
            BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET)::subir)
    val cargarCatalogoReservas = CargarCatalogoReservasUseCase(reservaRepository)
    val consultarSemana = ConsultarSemanaUseCase(reservaRepository)
    val cancelarTurno = ar.edu.unaj.am.myapplication.core.domain.usecase.CancelarTurnoUseCase(reservaRepository)
    val modificarTurno = ar.edu.unaj.am.myapplication.core.domain.usecase.ModificarTurnoUseCase(reservaRepository)
    val reservarTurno = ReservarTurnoUseCase(reservaRepository)
    private val publicarSemana = PublicarSemanaUseCase(reservaRepository)
    val obtenerMiAgenda = ObtenerMiAgendaUseCase(reservaRepository)
    val guardarYPublicarAgenda = GuardarYPublicarAgendaUseCase(reservaRepository, publicarSemana)
    private val turnoRepository =
        FirestoreTurnoRepository(turnoDao = turnoDao)
    val observeTurnos = ObserveTurnosUseCase(turnoRepository)
    val marcarTurnoRealizado = ar.edu.unaj.am.myapplication.core.domain.usecase.MarcarTurnoRealizadoUseCase(turnoRepository)

    private val authRepository: AuthRepository =
        FirebaseAuthRepository()

    val login = LoginUseCase(authRepository)
    val logout = LogoutUseCase(authRepository)
    val loginWithGoogle = LoginWithGoogleUseCase(authRepository)
    val register = RegisterUseCase(authRepository)
    private val clienteRepository: ClienteRepository = FirestoreClienteRepository(
        sesionLocal = sesionClienteLocal, sesionBarberoLocal = sesionBarberoLocal)
    val ensureClienteProfile = EnsureClienteProfileUseCase(clienteRepository)


    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun clienteActualId(): String? = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    suspend fun limpiarSesion(uid: String) {
        try {
            turnoRepository.limpiarSesion(uid)
        } finally {
            try {
                publicacionesLocales.limpiar(uid)
            } finally {
                sesionClienteLocal.limpiar(uid)
                sesionBarberoLocal.limpiar(uid)
            }
        }
    }

}


