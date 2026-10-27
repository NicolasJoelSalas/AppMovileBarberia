package ar.edu.unaj.am.myapplication.di

import ar.edu.unaj.am.myapplication.core.database.AppDatabase
import ar.edu.unaj.am.myapplication.core.database.di.DatabaseFile
import ar.edu.unaj.am.myapplication.core.database.migration.MIGRATION_1_2
import ar.edu.unaj.am.myapplication.core.database.migration.MIGRATION_2_3
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** El builder debe compilar contra Room Android; core:database es Kotlin/JVM. */
@Module
@InstallIn(SingletonComponent::class)
object AndroidDatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@DatabaseFile file: File): AppDatabase = AppDatabase.obtener {
        file.parentFile?.mkdirs()
        Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
}
