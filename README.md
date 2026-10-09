# Compose Academy

Proyecto base para aprender **Jetpack Compose + MVVM + Clean Architecture** en Android, con una arquitectura **modular desde el día 1**.

Es un proyecto **iterativo**: crece clase a clase. Cada clase suma un tema (DI, testing…) sobre la misma base, así que
el código que ves hoy es el punto de partida, no el final. Ver [Hoja de ruta](#hoja-de-ruta).

## La app

Tres tabs en una bottom bar:

| Tab | Qué muestra | Datos |
|---|---|---|
| **Tareas** | Master-detail: crear, completar, editar y borrar. Cada tarea es un apunte sobre un concepto de Compose | Simulados (API y base local falsas en memoria) |
| **Libros** | Master-detail con scroll infinito, portadas y detalle | API real: [Gutendex](https://gutendex.com/books/) |
| **Playground** | Pantalla libre donde el docente escribe los ejemplos de la clase en vivo | — |

> La tarea **"ERROR: Tarea corrupta"** fuerza un error de red al abrir su detalle: sirve para ver el estado `Error`.
> Libros necesita internet, y Gutendex a veces tarda en responder.

## Cómo correrlo

**Requisitos**
- Android Studio compatible con AGP 9.2.1 (si el IDE avisa de incompatibilidad, actualizalo).
- Android SDK Platform 37 (`compileSdk = 37`). El JDK 21 lo descarga Gradle solo.
- Un emulador o dispositivo con Android 8.0+ (`minSdk = 26`).

**Pasos**
1. Abrí la carpeta en Android Studio y esperá el *Gradle Sync*.
2. Elegí el módulo `app` y tocá ▶ Run.

**Por consola**
```bash
./gradlew assembleDebug   # compila la app
./gradlew test            # corre todos los tests unitarios
./gradlew lintDebug       # análisis estático
```

Para ver los requests de red en Logcat, filtrá por `tag:API_BOOKS` (solo en builds debug).

## Mapa del proyecto

```
:app                      Composition root: arma las dependencias (AppContainer) y la navegación
:features:todos           Feature Tareas  → list/ y detail/
:features:books           Feature Libros  → list/ y detail/
:features:playground      Pantalla de práctica
:core:ui                  Tema y componentes de UI compartidos
:core:domain              Modelos, contratos de repositorio y casos de uso (Kotlin puro)
:core:data                Implementación de los repositorios + mappers
:core:network             API remota (Retrofit real + fake)
:core:database            Base local (simulada en memoria)
```

Paquete base: `ar.edu.unaj.am.myapplication` (cada módulo agrega su sufijo: `.core.domain`, `.features.todos`…).

La regla que sostiene todo: **las features solo conocen el dominio**. Nunca ven la red ni la base de datos.
Detalle completo en [ARCHITECTURE.md](ARCHITECTURE.md).

## Cómo leer el código

Seguí un caso de uso de punta a punta, de la pantalla hacia los datos. Por ejemplo, **completar una tarea**:

1. `features/todos/.../list/TodoListScreen.kt` — la UI avisa el evento (`onToggle`).
2. `features/todos/.../list/TodoListViewModel.kt` — `onToggleTodo` lanza el caso de uso.
3. `core/domain/.../usecase/ToggleTodoUseCase.kt` — la regla de negocio.
4. `core/domain/.../repository/TodoRepository.kt` — el contrato (interfaz).
5. `core/data/.../repository/TodoRepositoryImpl.kt` — el *cómo*: red + base local.
6. `core/database/.../dao/InMemoryTodoDao.kt` — la base emite un `Flow` y la lista se actualiza sola.

## Documentación

- [ARCHITECTURE.md](ARCHITECTURE.md) — **qué** hay en cada capa y qué puede depender de qué.
- [docs/decisiones.md](docs/decisiones.md) — **por qué** se tomó cada decisión, qué cuesta y cuándo conviene revisarla.
- [docs/publicar-una-clase.md](docs/publicar-una-clase.md) — *para el docente*: qué es un tag, qué hacer con el Playground y cómo publicar cada clase.

## Hoja de ruta

| Clase | Tema | Tag |
|---|---|---|
| 1 | UI en Compose + MVVM + estado en composables + panorama de arquitectura | `clase-01-ui-mvvm` |
| 2 | Inyección de dependencias (reemplazar `AppContainer` por Hilt o Koin) | `clase-02-di` |
| 3 | Testing | `clase-03-testing` |
| … | A definir | |

Cada clase se publica con un **tag de git**: una etiqueta con nombre pegada al código tal como quedó al terminar esa clase.
Así, si te perdiste una clase (o querés ver el punto de partida de la siguiente), podés ver el proyecto exactamente como estaba:
```bash
git checkout clase-01-ui-mvvm
```

Ideas para próximas clases, ya preparadas en el código: reemplazar los fakes por Retrofit y Room reales,
un módulo `:core:testing` para compartir fakes entre tests, *convention plugins* para no repetir los `build.gradle.kts`,
Paging 3 y búsqueda.
# AppMovileBarberia
