package ar.edu.unaj.am.myapplication.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE turnos ADD COLUMN referenciaCorteUrl TEXT")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS publicaciones (
                clienteId TEXT NOT NULL, id TEXT NOT NULL,
                barberoId TEXT NOT NULL, barberoNombre TEXT NOT NULL,
                imagenUrl TEXT NOT NULL, descripcion TEXT NOT NULL,
                creadoEnMillis INTEGER, imagenLocalPath TEXT,
                PRIMARY KEY(clienteId, id)
            )
        """.trimIndent())
    }
}
