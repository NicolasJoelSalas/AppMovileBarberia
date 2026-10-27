package ar.edu.unaj.am.myapplication.features.notifications.di

import android.content.Context
import ar.edu.unaj.am.myapplication.features.notifications.*
import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.google.firebase.messaging.FirebaseMessaging

@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {
    @Provides @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides @Singleton
    fun provideAvisos(@ApplicationContext context: Context, consultar: ConsultarNovedadesTurnosUseCase,
        observar: ObservarNovedadesTurnosUseCase, auth: SesionActual): AvisosTurnos =
        AvisosTurnos(context, consultar, observar, usuarioActual = { auth.usuarioId() })

    @Provides @Singleton
    fun provideRegistro(@ApplicationContext context: Context, registrar: RegistrarDispositivoUseCase,
        desvincular: DesvincularDispositivoUseCase, messaging: FirebaseMessaging): RegistroNotificaciones =
        RegistroNotificaciones(context, registrar, desvincular, messaging)
}
