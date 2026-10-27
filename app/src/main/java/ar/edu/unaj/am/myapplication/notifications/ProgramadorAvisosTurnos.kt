package ar.edu.unaj.am.myapplication.notifications

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class ProgramadorAvisosTurnos(private val context: Context) {
    fun iniciar(uid: String) {
        val request = PeriodicWorkRequestBuilder<TurnosWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("uid" to uid)).addTag("avisos_turnos").build()
        // Actualiza también el tipo del Worker de instalaciones anteriores a esta separación.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("avisos_turnos_$uid",ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    fun detener() { WorkManager.getInstance(context).cancelAllWorkByTag("avisos_turnos") }
}
