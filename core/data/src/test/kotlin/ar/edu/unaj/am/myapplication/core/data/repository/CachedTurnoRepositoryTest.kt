package ar.edu.unaj.am.myapplication.core.data.repository

import ar.edu.unaj.am.myapplication.core.database.*
import ar.edu.unaj.am.myapplication.core.domain.model.*
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class CachedTurnoRepositoryTest {
    private class MemoryDao : TurnosDao() {
        val snapshots = MutableStateFlow<Map<String, TurnosCache>>(emptyMap())
        override fun observe(scope: String) = snapshots.map { it[scope] }.distinctUntilChanged()
        override suspend fun deleteScope(scope: String) = Unit
        override suspend fun insert(turnos: List<TurnoCacheEntity>) = Unit
        override suspend fun insertSync(sync: TurnosSyncEntity) = Unit
        override suspend fun replace(scope: String, turnos: List<Turno>) {
            snapshots.value = snapshots.value + (scope to TurnosCache(
                TurnosSyncEntity(scope, 123L), turnos.map { TurnoCacheEntity(scope, it) }))
        }
    }

    private val dao = MemoryDao()
    private val auth = mock(FirebaseAuth::class.java)
    private val user = mock(FirebaseUser::class.java)
    private val remoteEvents = MutableSharedFlow<AppResult<List<Turno>>>()
    private val remote = object : TurnoRepository {
        override fun observeTurnos(comoCliente: Boolean) = remoteEvents
        override suspend fun marcarRealizado(turnoId: String) = AppResult.Success(Unit)
    }
    private val turno = Turno("turno", "cliente", "barbero", "servicio", 100, 200,
        "confirmado", "Ana", "Corte")
    private lateinit var authListener: FirebaseAuth.AuthStateListener

    private fun repository(): CachedTurnoRepository {
        `when`(user.uid).thenReturn("usuario")
        `when`(auth.currentUser).thenReturn(user)
        doAnswer {
            authListener = it.getArgument(0)
            authListener.onAuthStateChanged(auth)
            null
        }.`when`(auth).addAuthStateListener(any(FirebaseAuth.AuthStateListener::class.java))
        return CachedTurnoRepository(remote, dao, auth)
    }

    @Test fun `cached details survive remote errors and a new repository instance`() = runTest {
        dao.replace("usuario:cliente", listOf(turno))
        val repo = repository()
        val results = mutableListOf<AppResult<List<Turno>>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeTurnos(true).collect { results.add(it) }
        }
        runCurrent()
        remoteEvents.emit(AppResult.Error(DomainError.Network))
        runCurrent()
        assertEquals(listOf(turno), (results.last() as AppResult.Success).data)
        assertEquals(DomainError.Network, repo.cacheInfo.value.error)
        job.cancel()
        val recreated = repository().observeTurnos(true).first()
        assertEquals(listOf(turno), (recreated as AppResult.Success).data)
    }

    @Test fun `server replacement removes deleted turns and persists confirmed empty list`() = runTest {
        dao.replace("usuario:cliente", listOf(turno))
        val results = mutableListOf<AppResult<List<Turno>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().observeTurnos(true).collect { results.add(it) }
        }
        runCurrent()
        remoteEvents.emit(AppResult.Success(emptyList()))
        runCurrent()
        assertEquals(emptyList<Turno>(), (results.last() as AppResult.Success).data)
        assertNotNull(dao.snapshots.value["usuario:cliente"])
    }

    @Test fun `another account or role cannot see cached turns`() = runTest {
        dao.replace("otro:cliente", listOf(turno))
        dao.replace("usuario:barbero", listOf(turno))
        assertTrue(repository().observeTurnos(true).first() is AppResult.Error)
    }

    @Test fun `sign out removes visible account data and cancels listener on disposal`() = runTest {
        dao.replace("usuario:cliente", listOf(turno))
        val results = mutableListOf<AppResult<List<Turno>>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().observeTurnos(true).collect { results.add(it) }
        }
        runCurrent()
        `when`(auth.currentUser).thenReturn(null)
        authListener.onAuthStateChanged(auth)
        runCurrent()
        assertTrue(results.last() is AppResult.Error)
        job.cancel()
        runCurrent()
        verify(auth).removeAuthStateListener(authListener)
    }
}
