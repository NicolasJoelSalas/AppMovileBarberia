package ar.edu.unaj.am.myapplication.core.network.di

import ar.edu.unaj.am.myapplication.core.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RutasBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudinaryCloudName

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudinaryUploadPreset

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideRutasApi(@RutasBaseUrl baseUrl: String): RutasApi = NetworkFactory.createRutasApi(baseUrl)

    @Provides @Singleton
    fun provideCloudinaryApi(): CloudinaryApi = NetworkFactory.createCloudinaryApi()
}
