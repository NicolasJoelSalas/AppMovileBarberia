package ar.edu.unaj.am.myapplication.di

import android.content.Context
import ar.edu.unaj.am.myapplication.notifications.*
import ar.edu.unaj.am.myapplication.features.notifications.PresentadorNotificaciones
import ar.edu.unaj.am.myapplication.core.domain.repository.*
import ar.edu.unaj.am.myapplication.core.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {
    @Provides @Singleton fun presentador(@ApplicationContext context: Context) = PresentadorNotificaciones(context)
    @Provides @Singleton fun programador(@ApplicationContext context: Context) = ProgramadorAvisosTurnos(context)
    @Provides @Singleton fun avisos(estado: EstadoAvisosRepository, programador: ProgramadorAvisosTurnos,
        presentador: PresentadorNotificaciones, consultar: ConsultarNovedadesTurnosUseCase,
        observar: ObservarNovedadesTurnosUseCase, auth: SesionActual) =
        AvisosTurnos(estado,programador,presentador,consultar,observar,usuarioActual={ auth.usuarioId() })
}
