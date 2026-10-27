package ar.edu.unaj.am.myapplication.features.turnos.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MonotonicClock

@Module
@InstallIn(SingletonComponent::class)
object TurnosModule {
    @Provides @MonotonicClock
    fun provideMonotonicClock(): () -> Long = { System.nanoTime() / 1_000_000 }
}
