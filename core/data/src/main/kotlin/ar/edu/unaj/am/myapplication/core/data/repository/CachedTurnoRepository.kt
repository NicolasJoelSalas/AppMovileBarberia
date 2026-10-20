package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.database.TurnosDao
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Room alimenta la pantalla; el servidor reemplaza cada alcance de UID y rol. */
class CachedTurnoRepository(
    private val remote: TurnoRepository,
    private val dao: TurnosDao,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : TurnoRepository {
    private val info = MutableStateFlow(TurnosCacheInfo())
    override val cacheInfo: StateFlow<TurnosCacheInfo> = info.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeTurnos(comoCliente: Boolean): Flow<AppResult<List<Turno>>> = callbackFlow<String?> {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged().flatMapLatest { uid ->
        info.value = TurnosCacheInfo()
        if (uid == null) flowOf(AppResult.Error(DomainError.Unknown("Iniciá sesión para consultar tus turnos.")))
        else observeScope(uid, comoCliente)
    }

    private fun observeScope(uid: String, comoCliente: Boolean): Flow<AppResult<List<Turno>>> = channelFlow {
        val scope = "$uid:${if (comoCliente) "cliente" else "barbero"}"
        launch {
            dao.observe(scope).collect { cache ->
                if (cache != null) {
                    info.value = info.value.copy(actualizadoEn = cache.sync.actualizadoEn)
                    send(AppResult.Success(cache.turnos.map { it.turno }.sortedBy { it.inicioMillis ?: Long.MAX_VALUE }))
                } else {
                    send(AppResult.Error(DomainError.Unknown(
                        "Todavía no hay turnos guardados en este dispositivo. Conectate para descargarlos."
                    )))
                }
            }
        }
        launch {
            try {
                remote.observeTurnos(comoCliente).collect { result ->
                    if (auth.currentUser?.uid != uid) return@collect
                    when (result) {
                        is AppResult.Success -> {
                            dao.replace(scope, result.data)
                            info.value = info.value.copy(error = null)
                        }
                        is AppResult.Error -> info.value = info.value.copy(error = result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                info.value = info.value.copy(error = DomainError.Unknown("No se pudo actualizar la copia local."))
            }
        }
    }

    override suspend fun marcarRealizado(turnoId: String): AppResult<Unit> = remote.marcarRealizado(turnoId)
}
