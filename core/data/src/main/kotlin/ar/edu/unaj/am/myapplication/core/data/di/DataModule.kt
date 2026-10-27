package ar.edu.unaj.am.myapplication.core.data.di

import android.content.Context
import ar.edu.unaj.am.myapplication.core.data.repository.*
import ar.edu.unaj.am.myapplication.core.data.images.*
import ar.edu.unaj.am.myapplication.core.data.session.*
import ar.edu.unaj.am.myapplication.core.database.dao.PublicacionDao
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import ar.edu.unaj.am.myapplication.core.network.CloudinaryApi
import ar.edu.unaj.am.myapplication.core.network.di.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun apariencia(value: PreferenciasApariencia): AparienciaRepository
    @Binds abstract fun recordatorios(value: ar.edu.unaj.am.myapplication.core.data.notifications.PreferenciasRecordatorios): RecordatoriosRepository
    @Binds abstract fun ubicacionDispositivo(value: ar.edu.unaj.am.myapplication.core.data.location.UbicacionDispositivoAndroid): UbicacionDispositivoRepository
    @Binds abstract fun capturas(value: CapturasTemporales): CapturasTemporalesRepository
    @Binds abstract fun tokens(value: ar.edu.unaj.am.myapplication.core.data.notifications.FirebaseTokenNotificaciones): TokenNotificacionesRepository
    @Binds abstract fun permisoNotificaciones(value: ar.edu.unaj.am.myapplication.core.data.notifications.PreferenciasPermisoNotificaciones): PermisoNotificacionesRepository
    @Binds abstract fun estadoAvisos(value: ar.edu.unaj.am.myapplication.core.data.notifications.PreferenciasAvisosTurnos): EstadoAvisosRepository
    @Binds abstract fun bindTurno(repository: FirestoreTurnoRepository): TurnoRepository
    @Binds abstract fun bindCliente(repository: FirestoreClienteRepository): ClienteRepository
    @Binds abstract fun bindServicio(repository: FirestoreServicioRepository): ServicioRepository
    @Binds abstract fun bindUbicacionBarberia(repository: FirestoreUbicacionBarberiaRepository): UbicacionBarberiaRepository
    @Binds abstract fun bindDispositivo(repository: FirestoreDispositivoRepository): DispositivoRepository
    @Binds abstract fun bindPerfil(repository: FirestorePerfilRepository): PerfilRepository
    @Binds abstract fun bindNovedadesTurnos(repository: FirestoreNovedadesTurnosRepository): NovedadesTurnosRepository
    @Binds abstract fun bindRuta(repository: OsrmRutaRepository): RutaRepository

    companion object {
        @Provides @Singleton fun messaging(): com.google.firebase.messaging.FirebaseMessaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
        @Provides @Singleton
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides @Singleton
        fun provideArchivos(@ApplicationContext context: Context): ArchivosPublicaciones =
            ImagenesPublicacionesLocales(File(context.noBackupFilesDir, "publicaciones"))

        @Provides @Singleton
        fun providePublicacionesLocales(dao: PublicacionDao, archivos: ArchivosPublicaciones,
            auth: FirebaseAuth): PublicacionesClienteLocal =
            PublicacionesClienteLocal(dao, archivos, usuarioActual = { auth.currentUser?.uid })

        @Provides @Singleton
        fun provideSubidor(preparador: PreparadorImagenTrabajo, api: CloudinaryApi,
            @CloudinaryCloudName cloudName: String, @CloudinaryUploadPreset preset: String): SubidorReferenciaCorte =
            SubidorReferenciaCorte(preparador, api, cloudName, preset)

        @Provides @Singleton
        fun provideReservas(firestore: FirebaseFirestore, auth: FirebaseAuth,
            cliente: SesionClienteLocal, barbero: SesionBarberoLocal,
            subidor: SubidorReferenciaCorte, reloj: java.time.Clock): ReservaRepository =
            FirestoreReservaRepository(firestore, auth, cliente, barbero, subidor::subir, reloj)

        @Provides @Singleton
        fun providePublicaciones(preparador: PreparadorImagenTrabajo, api: CloudinaryApi,
            @CloudinaryCloudName cloudName: String, @CloudinaryUploadPreset preset: String,
            firestore: FirebaseFirestore, auth: FirebaseAuth, local: PublicacionesClienteLocal,
            sesion: SesionClienteLocal): PublicacionRepository =
            FirestorePublicacionRepository(preparador, api, cloudName, preset, firestore, auth, local, sesion)
    }
}
