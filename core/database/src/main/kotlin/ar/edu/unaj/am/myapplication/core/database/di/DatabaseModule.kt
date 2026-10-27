package ar.edu.unaj.am.myapplication.core.database.di

import ar.edu.unaj.am.myapplication.core.database.AppDatabase
import ar.edu.unaj.am.myapplication.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DatabaseFile

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides fun provideTurnoDao(database: AppDatabase): TurnoDao = database.turnoDao()
    @Provides fun providePublicacionDao(database: AppDatabase): PublicacionDao = database.publicacionDao()
}
