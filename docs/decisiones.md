# Decisiones de arquitectura

Para cada decisión: **qué** hicimos, **por qué**, **qué nos cuesta** y **cuándo conviene revisarla**.
La arquitectura no es gratis: todo lo de acá tiene un costo, y es parte de la lección saber cuándo vale la pena.

Índice: [1 Modularizar](#1-modularizar-desde-el-día-1) · [2 Regla de dependencias](#2-regla-de-dependencias) ·
[3 Un módulo por feature](#3-un-módulo-por-feature) · [4 Dominio en Kotlin puro](#4-dominio-en-kotlin-puro) ·
[5 Repositorio como interfaz](#5-el-repositorio-es-una-interfaz) · [6 Casos de uso](#6-casos-de-uso) ·
[7 Un modelo por capa](#7-un-modelo-por-capa-y-mappers) · [8 Errores como valores](#8-errores-como-valores) ·
[9 Hilt](#9-hilt-inyección-de-dependencias-por-constructor) · [10 Route y Screen](#10-route-y-screen) ·
[11 UiState y eventos](#11-uistate-sealed-y-eventos-como-estado) · [12 Estrategia de datos](#12-estrategia-de-datos-por-feature) ·
[13 Navigation 3](#13-navigation-3) · [14 Datos simulados](#14-datos-simulados)

---

## 1. Modularizar desde el día 1

**Qué:** el proyecto tiene 9 módulos de Gradle en vez de un solo `app`.

**Por qué:**
- Es mucho más barato separar al principio que desarmar un monolito después.
- Gradle **impide** violar la arquitectura: si `:features:todos` no declara `:core:data` como dependencia, no puede importar nada de ahí. En un solo módulo, la regla es una promesa; en varios, el compilador la hace cumplir.
- Los módulos independientes compilan en paralelo y se recompilan solo cuando cambian.

**Cuesta:** más archivos de configuración (hoy los `build.gradle.kts` de los módulos Android son casi idénticos) y más cosas para navegar.

**Revisar:** si el proyecto fuera de una sola pantalla y una sola persona, un módulo alcanzaría. Los *convention plugins* reducen la repetición de configuración.

## 2. Regla de dependencias

**Qué:** las capas externas dependen de las internas, nunca al revés.

| Módulo | Puede depender de |
|---|---|
| `:app` | todos (es el que los conecta) |
| `:features:*` | `:core:domain`, `:core:ui` |
| `:core:ui` | `:core:domain` |
| `:core:data` | `:core:domain`, `:core:network`, `:core:database` |
| `:core:database` | `:core:domain` |
| `:core:network`, `:core:domain` | nada |

**Por qué:** lo que más cambia (UI, librerías de red, base de datos) queda afuera; lo que menos cambia (las reglas del negocio) queda en el centro y no se entera de nada de lo de afuera.

## 3. Un módulo por feature

**Qué:** cada feature es **una capacidad del usuario** (Tareas, Libros) y contiene sus pantallas en paquetes: `list/` y `detail/`.

**Por qué:** Clean Architecture habla de capas y no dice cuántas pantallas van en un módulo; eso es una decisión de **cohesión**: lo que cambia junto, vive junto. La lista y el detalle de una tarea comparten modelo, flujo y navegación, y se modifican juntos. La documentación de Android respalda esto:
- Define un feature como *"an isolated part of an app's functionality that usually corresponds to a screen or series of closely related screens"* ([patrones de modularización](https://developer.android.com/topic/modularization/patterns)).
- Aconseja: *"If two modules heavily rely on knowledge of each other, it may be a good sign that they should actually act as one system"* (misma página).
- Advierte que un módulo demasiado fino (*"too fine-grained"*) trae *"increased build complexity and boilerplate code"* ([modularización, sección "Common pitfalls"](https://developer.android.com/topic/modularization)). Y el extremo opuesto también es un problema: módulos demasiado grandes terminan siendo otro monolito.

**Cuándo separar un detalle en su propio módulo** (tiene que haber una razón que se pueda nombrar):
1. Se abre desde **varios lugares** (por ejemplo, el detalle de un libro desde Libros, Favoritos y Búsqueda).
2. Lo mantienen **equipos distintos**.
3. Hay un motivo medible de **tiempo de build** o de carga opcional.

Hoy ninguna se cumple, por eso no están separados. Cuando aparezca el segundo punto de entrada, se extrae: es barato porque cada pantalla ya es `Route`/`Screen` y navega por callbacks.

## 4. Dominio en Kotlin puro

**Qué:** `:core:domain` es un módulo JVM (`kotlin("jvm")`): sin Android, sin Compose, sin Room, sin Retrofit.

**Por qué:** no *puede* importar nada de Android porque no lo tiene en el classpath. Sus tests corren en la JVM, en milisegundos, sin emulador.

**Cuesta:** los modelos de dominio no pueden llevar anotaciones de librerías; por eso existen los mappers (decisión 7).

## 5. El repositorio es una interfaz

**Qué:** `TodoRepository` y `BookRepository` (interfaces) viven en `:core:domain`; las implementaciones viven en `:core:data`.

**Por qué:** es la **inversión de dependencias**: el dominio define *qué* necesita y la capa de datos se adapta. Así `:core:data` depende de `:core:domain` y no al revés, y en los tests se reemplaza por un fake sin red ni base de datos.

**Cuesta:** una interfaz con una sola implementación parece de más. Se justifica porque ya tenemos **dos** estrategias distintas (decisión 12) y siempre una tercera en los tests.

## 6. Casos de uso

**Qué:** clases de una sola responsabilidad con `operator fun invoke()`.

**Por qué:** concentran las **reglas de negocio** fuera de la UI y de los datos. Ejemplos reales del proyecto: `AddTodoUseCase` valida el título y recorta espacios; `ObserveTodosUseCase` ordena las pendientes primero.

**Cuesta, y conviene decirlo:** varios casos de uso de este proyecto son un simple pasamanos al repositorio (`GetTodoUseCase`, `RefreshTodosUseCase`, `DeleteTodoUseCase`, `GetBooksUseCase`…). Es *boilerplate*. Muchos equipos omiten los triviales y llaman al repositorio directo desde el ViewModel; otros prefieren la consistencia de pasar siempre por un caso de uso. Acá elegimos la consistencia porque el objetivo es enseñar el patrón completo.

**Revisar:** si un caso de uso no tiene lógica y no la va a tener, es válido eliminarlo.

## 7. Un modelo por capa y mappers

**Qué:** el mismo concepto existe en tres formas: `TodoDto` (cómo lo manda el servidor), `TodoEntity` (cómo se guarda) y `Todo` (cómo lo entiende el negocio). Los mappers convierten entre ellas.

**Por qué:** cada capa evoluciona por separado. Si el servidor renombra `completed` a `done`, solo cambia el DTO y su mapper. El mapper también adapta formatos: `"Austen, Jane"` se convierte en `"Jane Austen"`.

**Cuesta:** más clases y más código repetitivo. Es el costo más visible de Clean Architecture.

## 8. Errores como valores

**Qué:** los repositorios y casos de uso devuelven `AppResult<T>` (`Success` / `Error(DomainError)`) en lugar de lanzar excepciones.

**Por qué:** una excepción es invisible en la firma de la función: nada obliga a manejarla. Con un tipo, el `when` del ViewModel es exhaustivo y el compilador exige tratar el error. Además la UI nunca ve un `IOException` ni un código HTTP: ve `DomainError.Network`, y decide qué texto mostrar.

## 9. Hilt: inyección de dependencias por constructor

**Qué:** Hilt genera la construcción y conexión de objetos. `AppContainer` fue eliminado; los casos de uso y los repositorios sencillos reciben sus dependencias mediante `@Inject constructor`.

**Por qué:** cada capa declara qué implementación proporciona y cómo configura sus dependencias. `@Binds` conecta contratos con implementaciones; `@Provides` configura Room, Firebase, las APIs HTTP, Cloudinary y los servicios de notificaciones. Los tests pueden seguir construyendo los objetos con fakes.

**Alcances:** las dependencias con estado compartido son `@Singleton`, para reutilizar repositorios, sesiones locales y la misma base de datos. Los ViewModels conservan el alcance de sesión o de entrada de Navigation 3. Los argumentos conocidos al navegar se proporcionan con inyección asistida.

**Sesión:** `GestorSesion` conserva la coordinación de limpieza que antes estaba en el contenedor. No es un contenedor alternativo: recibe sus dependencias de Hilt y no construye objetos. La aplicación mantiene los contratos de acceso para el servicio de mensajes y el Worker, usando instancias proporcionadas por Hilt.

## 10. Route y Screen

**Qué:** cada pantalla se divide en `XxxRoute` (con estado, conoce al ViewModel) y `XxxScreen` (sin estado: recibe datos y lambdas).

**Por qué:** es *state hoisting*. `Screen` no depende del ViewModel, así que se ve en `@Preview`, se testea y se reutiliza. Los previews del proyecto (Success, Error, Loading, Empty) salen de esta separación.

## 11. UiState sealed y eventos como estado

**Qué:** el estado de una pantalla es una `sealed interface` (`Loading` / `Success` / `Error`) expuesta como `StateFlow`. Los eventos de una sola vez (un snackbar, volver atrás tras guardar) se modelan **como estado**: un campo (`userMessage`, `isSaved`) que la UI consume y avisa que consumió.

**Por qué:** la pantalla está en un solo estado a la vez y el `when` exhaustivo impide olvidar uno. Para los eventos, la [guía de Android sobre eventos de UI](https://developer.android.com/topic/architecture/ui-layer/events) indica que *"ViewModel events should always result in a UI state update"* y advierte que con `Channel` u otros streams, cuando el ViewModel vive más que la UI, *"these solutions don't guarantee the delivery and processing of those events"*. Con estado, en cambio, hay más garantías de entrega y es más fácil de testear.

**Cuesta:** requiere el paso extra de "consumir" el evento (`onUserMessageShown()`).

## 12. Estrategia de datos por feature

**Qué:** Tareas es **offline-first** (la base local es la única fuente de verdad y la red la actualiza); Libros es **red + caché en memoria** (la lista ya trae el libro completo, así que el detalle lo reutiliza).

**Por qué:** muestra que la estrategia de datos es una decisión del *repositorio*. Los ViewModels de las dos features son prácticamente iguales: no saben de dónde vienen los datos. El caché de Libros surgió de un problema real: el endpoint de detalle de Gutendex tardaba ~35 segundos.

## 13. Navigation 3

**Qué:** no hay `NavController`. El back stack es una lista de *keys* tipadas (`@Serializable … : NavKey`) y navegar es modificarla.

**Por qué:** es consistente con el resto de Compose: la UI es función del estado, y el back stack es un estado más. Los argumentos son propiedades tipadas (`key.bookId`), sin strings ni `getInt("id")`. Cada tab tiene su propio back stack y su propio `ViewModelStore`, así que cambiar de tab no pierde estado.

**Cuesta:** `NavigationState` (la lógica de tabs) lo escribimos nosotros: son unas 60 líneas visibles, en lugar de configuración interna de la librería.

## 14. Datos simulados

**Qué:** la API y la base de Tareas son falsas (`FakeTodoApi`, `InMemoryTodoDao`) detrás de las mismas interfaces que tendrían las reales (`TodoApi`, `TodoDao`).

**Por qué:** esta primera clase es de UI y MVVM; conectar Room y un backend agrega ruido. Pero la arquitectura ya está preparada: reemplazarlos es cambiar `AppContainer`. Libros sí usa Retrofit real, para mostrar cómo se ve.

**Ojo:** los fakes están en el `main` de `:core:network`, no en un módulo de tests. Es una simplificación para la clase; en un proyecto real irían separados.

---

## Lo que dejamos afuera a propósito

| Tema | Por qué no está todavía | Dónde encaja |
|---|---|---|
| Hilt / Koin | Primero entender la inyección a mano | Clase 2 |
| Testing en profundidad | Hay algunos tests de ejemplo (dominio, mappers, ViewModels) | Clase 3 |
| Room real | Detrás de `TodoDao` ya está su lugar | A definir |
| Convention plugins | Primero sentir la repetición de los `build.gradle.kts` | A definir |
| Paging 3 | La paginación manual de Libros enseña la idea de fondo | A definir |
