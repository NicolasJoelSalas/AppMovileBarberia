# Arquitectura

Este documento describe **qué** hay en cada capa y qué puede depender de qué.
Para el **porqué** de cada decisión, sus costos y cuándo revisarla, ver [docs/decisiones.md](docs/decisiones.md).
Para correr el proyecto, ver el [README](README.md).

## Visión general

Tres tabs en una bottom bar:

| Tab | Fuente de datos | Estrategia |
|---|---|---|
| Tareas | `FakeTodoApi` + `InMemoryTodoDao` (simulados) | Offline-first: la base local es la fuente de verdad |
| Libros | [Gutendex](https://gutendex.com/books/) real con Retrofit | Red + caché en memoria |
| Playground | — | Pantalla libre para escribir ejemplos en vivo en clase |

Tareas y Libros usan exactamente el mismo patrón (UiState, ViewModel, casos de uso, repositorio):
solo cambia la implementación del repositorio en `:core:data`.

## Módulos y dependencias

```
                              :app
              (composition root: DI + navegación)
                 │          │            │
        :features:todos  :features:books  :features:playground
                 └────┬─────┘                   │
                      ▼                         │
                  :core:ui ◄────────────────────┘
                      │
                      ▼
                :core:domain  ◄──────  :core:data ──► :core:network
                      ▲                     │
                      └──── :core:database ◄┘
```

| Módulo | Puede depender de |
|---|---|
| `:app` | todos (es el único que los conecta) |
| `:features:*` | `:core:domain`, `:core:ui` |
| `:core:ui` | `:core:domain` |
| `:core:data` | `:core:domain`, `:core:network`, `:core:database` |
| `:core:database` | `:core:domain` |
| `:core:network`, `:core:domain` | nada |

Las features **no ven** `:core:data`, `:core:network` ni `:core:database`: Gradle no las deja importar nada de ahí.

## Layer Definitions

### App — `:app`

- `ComposeAcademyApp` (`@HiltAndroidApp`) inicia el grafo de Hilt. `di/AppModule` aporta la configuración de la variante de build y la ruta de la base de datos.
- Cada capa declara sus dependencias: `AuthModule`, `DataModule`, `NetworkModule`, `DatabaseModule` y `NotificationsModule`. Los constructores con `@Inject` reciben las dependencias; `@Binds` conecta interfaces e implementaciones y `@Provides` configura servicios externos.
- Los repositorios, la base de datos y los servicios con estado compartido tienen alcance `@Singleton`. `session/GestorSesion` coordina la limpieza al salir sin crear dependencias.
- Los ViewModels usan `@HiltViewModel` y se obtienen mediante `hiltViewModel()`. Los parámetros de navegación de turnos y ubicación usan `@AssistedInject`. El almacén de sesión conserva la factory y los extras del Activity para mantener la integración con Hilt y `SavedStateHandle`.
- `navigation/`: **Navigation 3**. No hay `NavController`: el back stack es una lista de keys tipadas (`@Serializable … : NavKey`) que modificamos nosotros
  - `Destinations`: una key por pantalla; los argumentos son propiedades (`BookDetailDestination(bookId)`)
  - `NavigationState`: tab actual + un `NavBackStack` por tab; `navigate()` = `add`, `goBack()` = `removeAt(lastIndex)`
  - `AppNavDisplay`: `entryProvider { entry<BookDetailDestination> { key -> … key.bookId … } }` + `NavDisplay`
  - Cada entrada tiene su propio `ViewModelStore` (`rememberViewModelStoreNavEntryDecorator`), y los back stacks de todos los tabs quedan decorados, así que cambiar de tab no pierde ViewModels ni scroll
- `ui/AppShell`: `NavigationBar` armada desde `TopLevelDestination.entries`, oculta fuera de la pantalla principal de cada tab
- Es el único módulo que conoce todas las capas

### Presentation — `:features:*`

Un módulo por feature (una capacidad del usuario), con sus pantallas en paquetes `list/` y `detail/`:

- **`:features:todos`**: lista con pull-to-refresh, alta, completar y swipe para borrar; detalle con modo lectura/edición y validación
- **`:features:books`**: lista con scroll infinito (`derivedStateOf` + `LaunchedEffect`); detalle con portada (Coil), temas (`FlowRow`) y link externo (`LocalUriHandler`)
- **`:features:playground`**: pantalla libre para pruebas en vivo en clase (sin ViewModel ni dominio)

Convenciones de todas las features:
- Patrón **Route / Screen**: `XxxRoute` (con estado, conoce al ViewModel) → `XxxScreen` (sin estado, con `@Preview`)
- Las pantallas observan `StateFlow<UiState>` con `collectAsStateWithLifecycle()`
- Los ViewModels tienen un `MutableStateFlow<UiState>` privado y exponen un `StateFlow<UiState>` público
- `UiState` como `sealed interface` (`Loading` / `Success` / `Error`), con `when` exhaustivo
- Eventos de una sola vez guiados por estado (`isSaved`, `userMessage` + `onUserMessageShown()`), sin `Channel`
- Estado efímero de UI (diálogos, formulario de alta) en `rememberSaveable`, no en el ViewModel
- Los ViewModels reciben sus casos de uso por constructor
- Sin dependencia de `:core:data`, `:core:network` ni `:core:database`

### Shared UI — `:core:ui`

- `MyApplicationTheme`, `LoadingContent`, `ErrorContent` (con slot `extraAction`), `MessageContent`
- `DomainError.asMessage()`: traduce errores de dominio a string resources

### Domain — `:core:domain`

- Módulo Kotlin puro: sin Android. Los constructores usan la anotación estándar `javax.inject.Inject`; no depende de las APIs de Hilt.
- Casos de uso de una sola responsabilidad con `operator fun invoke()`, algunos con reglas de negocio (validación del título, pendientes primero)
- `TodoRepository` y `BookRepository` son interfaces; las implementaciones viven en `:core:data`
- `AppResult<T>` + `DomainError`: los errores viajan como valores, no como excepciones
- Modelos de dominio (`Todo`, `Book`) sin anotaciones ni dependencias de librerías

### Data — `:core:data`

- `TodoRepositoryImpl`: **offline-first**. El DAO es la única fuente de verdad y la red actualiza el caché
- `BookRepositoryImpl`: red + caché en memoria (el detalle reutiliza el libro que ya trajo la lista)
- Mappers DTO → dominio / entidad, y `NetworkResult` → `AppResult` (por ejemplo `"Austen, Jane"` → `"Jane Austen"`)

### Network — `:core:network`

- `BookApi`: Retrofit real contra Gutendex, `BookDto` con `@Serializable` / `@SerialName`
- `NetworkFactory`: OkHttp (timeouts, logger inyectable) + converter kotlinx.serialization. El logger lo provee `:app` (tag `API_BOOKS`, solo en debug)
- `TodoApi` + `FakeTodoApi`: API simulada con latencia. La tarea con id `99` fuerza un error de red para mostrar el estado `Error`
- `NetworkResult<T>` (`HttpError`, `NetworkError`, `ParseError`) y `safeApiCall {}`

### Database — `:core:database` (simulado)

- `TodoDao` (interfaz, como un `@Dao` de Room) + `InMemoryTodoDao`, que expone un `Flow` reactivo
- `TodoEntity` + `TodoEntityMapper` (entidad → dominio)

## Tests

```bash
./gradlew test
```

| Módulo | Test | Qué verifica |
|---|---|---|
| `:core:domain` | `TodoUseCasesTest` | validación, orden, toggle |
| `:core:data` | `BookMapperTest`, `BookRepositoryImplTest` | mappers y caché |
| `:features:todos` | `TodoListViewModelTest` | carga, error, alta reactiva, refresh fallido |
| `:features:books` | `BookListViewModelTest` | paginación, última página, error al cargar más |

## Próximos pasos

Ver la [hoja de ruta en el README](README.md#hoja-de-ruta) y las decisiones que dejamos afuera a propósito en
[docs/decisiones.md](docs/decisiones.md#lo-que-dejamos-afuera-a-propósito).
