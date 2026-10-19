package ar.edu.unaj.am.myapplication.di

import android.content.Context
import ar.edu.unaj.am.myapplication.core.data.images.PreparadorImagenTrabajo
import ar.edu.unaj.am.myapplication.core.data.repository.FirestorePublicacionRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObservarPublicacionesUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.PublicarTrabajoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.EliminarPublicacionUseCase
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
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerMiPerfilUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ActualizarMiNombreUseCase


class AppContainer(context: Context) {
    private val perfilRepository = FirestorePerfilRepository()
    val obtenerMiPerfil = ObtenerMiPerfilUseCase(perfilRepository)
    val actualizarMiNombre = ActualizarMiNombreUseCase(perfilRepository)
    private val publicacionRepository = FirestorePublicacionRepository(
        PreparadorImagenTrabajo(context.applicationContext), NetworkFactory.createCloudinaryApi(),
        BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET)
    val observarPublicaciones = ObservarPublicacionesUseCase(publicacionRepository)
    val publicarTrabajo = PublicarTrabajoUseCase(publicacionRepository)
    val eliminarPublicacion = EliminarPublicacionUseCase(publicacionRepository)

    private val reservaRepository: ReservaRepository = FirestoreReservaRepository()
    val cargarCatalogoReservas = CargarCatalogoReservasUseCase(reservaRepository)
    val consultarSemana = ConsultarSemanaUseCase(reservaRepository)
    val cancelarTurno = ar.edu.unaj.am.myapplication.core.domain.usecase.CancelarTurnoUseCase(reservaRepository)
    val modificarTurno = ar.edu.unaj.am.myapplication.core.domain.usecase.ModificarTurnoUseCase(reservaRepository)
    val reservarTurno = ReservarTurnoUseCase(reservaRepository)
    private val publicarSemana = PublicarSemanaUseCase(reservaRepository)
    val obtenerMiAgenda = ObtenerMiAgendaUseCase(reservaRepository)
    val guardarYPublicarAgenda = GuardarYPublicarAgendaUseCase(reservaRepository, publicarSemana)
    private val turnoRepository: TurnoRepository = FirestoreTurnoRepository()
    val observeTurnos = ObserveTurnosUseCase(turnoRepository)
    val marcarTurnoRealizado = ar.edu.unaj.am.myapplication.core.domain.usecase.MarcarTurnoRealizadoUseCase(turnoRepository)

    private val authRepository: AuthRepository =
        FirebaseAuthRepository()

    val login = LoginUseCase(authRepository)
    val logout = LogoutUseCase(authRepository)
    val loginWithGoogle = LoginWithGoogleUseCase(authRepository)
    val register = RegisterUseCase(authRepository)
    private val clienteRepository: ClienteRepository = FirestoreClienteRepository()
    val ensureClienteProfile = EnsureClienteProfileUseCase(clienteRepository)

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

}


