package ar.edu.unaj.am.myapplication.core.database

import android.content.Context
import androidx.room.*
import ar.edu.unaj.am.myapplication.core.domain.model.Turno
import kotlinx.coroutines.flow.Flow

// El alcance incluye UID y rol: una cuenta nunca consulta la copia de otra.
@Entity(tableName = "turnos_cache", primaryKeys = ["scope", "id"])
data class TurnoCacheEntity(val scope: String, @Embedded val turno: Turno)

@Entity(tableName = "turnos_sync")
data class TurnosSyncEntity(@PrimaryKey val scope: String, val actualizadoEn: Long)

data class TurnosCache(
    @Embedded val sync: TurnosSyncEntity,
    @Relation(parentColumn = "scope", entityColumn = "scope")
    val turnos: List<TurnoCacheEntity>
)

@Dao
abstract class TurnosDao {
    @Transaction
    @Query("SELECT * FROM turnos_sync WHERE scope = :scope")
    abstract fun observe(scope: String): Flow<TurnosCache?>

    @Query("DELETE FROM turnos_cache WHERE scope = :scope")
    abstract suspend fun deleteScope(scope: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(turnos: List<TurnoCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSync(sync: TurnosSyncEntity)

    @Transaction
    open suspend fun replace(scope: String, turnos: List<Turno>) {
        deleteScope(scope)
        insert(turnos.map { TurnoCacheEntity(scope, it) })
        // Se registra también una lista vacía confirmada por el servidor.
        insertSync(TurnosSyncEntity(scope, System.currentTimeMillis()))
    }
}

@Database(entities = [TurnoCacheEntity::class, TurnosSyncEntity::class], version = 1)
abstract class TurnosDatabase : RoomDatabase() {
    abstract fun turnosDao(): TurnosDao

    companion object {
        fun create(context: Context): TurnosDatabase = Room.databaseBuilder(
            context.applicationContext, TurnosDatabase::class.java, "turnos.db"
        ).build()
    }
}
