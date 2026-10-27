package ar.edu.unaj.am.myapplication.di

import android.content.Context
import ar.edu.unaj.am.myapplication.BuildConfig
import ar.edu.unaj.am.myapplication.core.database.di.DatabaseFile
import ar.edu.unaj.am.myapplication.core.network.di.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.time.ZoneId

/** Configuración que solo conoce la aplicación y su variante de build. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @RutasBaseUrl fun provideRutasBaseUrl(): String = BuildConfig.RUTAS_BASE_URL
    @Provides @CloudinaryCloudName fun provideCloudName(): String = BuildConfig.CLOUDINARY_CLOUD_NAME
    @Provides @CloudinaryUploadPreset fun provideUploadPreset(): String = BuildConfig.CLOUDINARY_UPLOAD_PRESET
    @Provides @DatabaseFile
    fun provideDatabaseFile(@ApplicationContext context: Context): File = context.getDatabasePath("barberia_database")
    @Provides fun provideClock(): Clock = Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"))
}
