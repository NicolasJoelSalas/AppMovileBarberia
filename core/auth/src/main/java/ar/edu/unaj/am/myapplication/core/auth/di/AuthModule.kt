package ar.edu.unaj.am.myapplication.core.auth.di

import ar.edu.unaj.am.myapplication.core.auth.repository.FirebaseAuthRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import ar.edu.unaj.am.myapplication.core.domain.repository.SesionActual
import ar.edu.unaj.am.myapplication.core.auth.repository.FirebaseSesionActual
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds abstract fun bindSesionActual(session: FirebaseSesionActual): SesionActual
    @Binds abstract fun bindAuth(repository: FirebaseAuthRepository): AuthRepository

    companion object {
        @Provides @Singleton fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    }
}
