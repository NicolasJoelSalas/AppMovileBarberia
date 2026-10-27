package ar.edu.unaj.am.myapplication.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/** Dependencias provistas por app; la feature no depende de core:data ni de Firebase Auth. */
interface TurnosWorkerDependencies {
    val avisosTurnos: AvisosTurnos
}

class TurnosWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val dependencies = applicationContext as? TurnosWorkerDependencies ?: return Result.failure()
        if (!dependencies.avisosTurnos.sesionValida(uid)) return Result.success()
        return try {
            dependencies.avisosTurnos.consultar(uid)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BarberiaAvisos", "No se pudieron consultar los cambios; se reintentará", e)
            Result.retry()
        }
    }
}
