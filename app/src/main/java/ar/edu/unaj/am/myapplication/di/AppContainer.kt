package ar.edu.unaj.am.myapplication.di

import android.util.Log
import android.content.Context
import ar.edu.unaj.am.myapplication.core.data.images.PreparadorImagenTrabajo
import ar.edu.unaj.am.myapplication.core.data.repository.FirestorePublicacionRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObservarPublicacionesUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.PublicarTrabajoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.EliminarPublicacionUseCase
import ar.edu.unaj.am.myapplication.BuildConfig
import ar.edu.unaj.am.myapplication.core.data.repository.BookRepositoryImpl
import ar.edu.unaj.am.myapplication.core.data.repository.TodoRepositoryImpl
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreTurnoRepository
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreClienteRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.ClienteRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.EnsureClienteProfileUseCase
import ar.edu.unaj.am.myapplication.core.domain.repository.ReservaRepository
import ar.edu.unaj.am.myapplication.core.data.repository.FirestoreReservaRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.CargarCatalogoReservasUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ConsultarSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ReservarTurnoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.PublicarSemanaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObtenerMiAgendaUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.GuardarYPublicarAgendaUseCase
import ar.edu.unaj.am.myapplication.core.domain.repository.TurnoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTurnosUseCase
import ar.edu.unaj.am.myapplication.core.database.dao.InMemoryTodoDao
import ar.edu.unaj.am.myapplication.core.database.dao.TodoDao
import ar.edu.unaj.am.myapplication.core.domain.repository.BookRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.TodoRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.AddTodoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.DeleteTodoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.GetBookUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.GetBooksUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.GetTodoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ObserveTodosUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.RefreshTodosUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.ToggleTodoUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.UpdateTodoUseCase
import ar.edu.unaj.am.myapplication.core.network.BookApi
import ar.edu.unaj.am.myapplication.core.network.NetworkFactory
import ar.edu.unaj.am.myapplication.core.network.TodoApi
import ar.edu.unaj.am.myapplication.core.network.fake.FakeTodoApi
import ar.edu.unaj.am.myapplication.core.auth.repository.FirebaseAuthRepository
import ar.edu.unaj.am.myapplication.core.domain.repository.AuthRepository
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LogoutUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.LoginWithGoogleUseCase
import ar.edu.unaj.am.myapplication.core.domain.usecase.RegisterUseCase

/**
 * Inyección de dependencias MANUAL: acá se decide qué implementación concreta usa cada contrato.
 *
 * - ¿Queremos la API real? Cambiamos FakeTodoApi por Retrofit.create(TodoApi::class.java).
 * - ¿Queremos Room? Cambiamos InMemoryTodoDao por database.todoDao().
 *
 * Ni el dominio ni las features se enteran del cambio. En proyectos grandes este archivo
 * lo reemplaza una librería como Hilt o Koin, pero la idea es exactamente la misma.
 */
class AppContainer(context: Context) {
    private val publicacionRepository = FirestorePublicacionRepository(
        PreparadorImagenTrabajo(context.applicationContext), NetworkFactory.createCloudinaryApi(),
        BuildConfig.CLOUDINARY_CLOUD_NAME, BuildConfig.CLOUDINARY_UPLOAD_PRESET)
    val observarPublicaciones = ObservarPublicacionesUseCase(publicacionRepository)
    val publicarTrabajo = PublicarTrabajoUseCase(publicacionRepository)
    val eliminarPublicacion = EliminarPublicacionUseCase(publicacionRepository)

    private val reservaRepository: ReservaRepository = FirestoreReservaRepository()
    val cargarCatalogoReservas = CargarCatalogoReservasUseCase(reservaRepository)
    val consultarSemana = ConsultarSemanaUseCase(reservaRepository)
    val reservarTurno = ReservarTurnoUseCase(reservaRepository)
    val publicarSemana = PublicarSemanaUseCase(reservaRepository)
    val obtenerMiAgenda = ObtenerMiAgendaUseCase(reservaRepository)
    val guardarYPublicarAgenda = GuardarYPublicarAgendaUseCase(reservaRepository, publicarSemana)
    private val turnoRepository: TurnoRepository = FirestoreTurnoRepository()
    val observeTurnos = ObserveTurnosUseCase(turnoRepository)
    val calcularResumenBarbero = ar.edu.unaj.am.myapplication.core.domain.usecase.CalcularResumenBarberoUseCase()
    val marcarTurnoRealizado = ar.edu.unaj.am.myapplication.core.domain.usecase.MarcarTurnoRealizadoUseCase(turnoRepository)

    // ---------- Tareas: API simulada + base local en memoria (offline-first) ----------

    private val todoApi: TodoApi = FakeTodoApi()
    private val todoDao: TodoDao = InMemoryTodoDao()
    private val todoRepository: TodoRepository = TodoRepositoryImpl(todoApi, todoDao)

    val observeTodos = ObserveTodosUseCase(todoRepository)
    val refreshTodos = RefreshTodosUseCase(todoRepository)
    val getTodo = GetTodoUseCase(todoRepository)
    val addTodo = AddTodoUseCase(todoRepository)
    val updateTodo = UpdateTodoUseCase(todoRepository)
    val toggleTodo = ToggleTodoUseCase(todoRepository)
    val deleteTodo = DeleteTodoUseCase(todoRepository)

    // ---------- Libros: API real de Gutendex con Retrofit (network-only) ----------

    // En Logcat: filtrar por tag:API_BOOKS. Solo en debug; en release no se loguea nada.
    private val bookApi: BookApi = NetworkFactory.createBookApi(
        logger = if (BuildConfig.DEBUG) { message -> Log.d(API_BOOKS_TAG, message) } else null
    )
    private val bookRepository: BookRepository = BookRepositoryImpl(bookApi)

    val getBooks = GetBooksUseCase(bookRepository)
    val getBook = GetBookUseCase(bookRepository)
    private val authRepository: AuthRepository =
        FirebaseAuthRepository()

    val login = LoginUseCase(authRepository)
    val logout = LogoutUseCase(authRepository)
    val loginWithGoogle = LoginWithGoogleUseCase(authRepository)
    val register = RegisterUseCase(authRepository)
    private val clienteRepository: ClienteRepository = FirestoreClienteRepository()
    val ensureClienteProfile = EnsureClienteProfileUseCase(clienteRepository)

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

}

private const val API_BOOKS_TAG = "API_BOOKS"
