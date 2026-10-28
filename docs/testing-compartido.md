# Herramientas compartidas de pruebas

`:core:testing` es un módulo Kotlin/JVM, siguiendo el template de la materia.
Contiene utilidades reutilizables; cada test permanece en el módulo que verifica.

## Estructura

Las clases compartidas están en
`core/testing/src/main/kotlin/ar/edu/unaj/am/myapplication/core/testing/`:

- `MainDispatcherRule`: instala un dispatcher de prueba como `Dispatchers.Main`
  antes de cada test y lo restaura al finalizar, incluso ante errores.
- `FakeServicioRepository`: registra servicios y cambios de estado; permite
  simular errores y operaciones pendientes mediante `CompletableDeferred`.
- `FakePublicacionRepository`: emite publicaciones mediante `MutableStateFlow`
  y registra altas, modificaciones y eliminaciones, sin acceder a Firebase.
- `TestData`: construye servicios de ejemplo con valores deterministas y campos
  configurables por cada prueba.

Se utilizan en las pruebas de `core:domain` y de las features `auth`, `turnos`,
`home` y `cuenta`. Las simulaciones específicas de una prueba permanecen junto
a ella; no hace falta convertirlas todas en herramientas compartidas.

## Dependencias

Los consumidores declaran exclusivamente una dependencia de pruebas:

```kotlin
testImplementation(project(":core:testing"))
```

Las utilidades viven en `src/main` para que otros módulos puedan importarlas.
No se debe agregar `implementation(project(":core:testing"))`: las herramientas
de prueba no deben formar parte del APK de producción.

El módulo depende del código principal de `core:domain`, JUnit y coroutines.
El código principal de dominio no depende de testing; solamente lo usan sus tests.
No depende de features, Android, Room ni Firebase.

## Coroutines

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

El valor predeterminado es `UnconfinedTestDispatcher`, como en las pruebas
existentes de los ViewModels. Si una prueba controla los avances de tiempo,
puede conservar su dispatcher:

```kotlin
private val dispatcher = StandardTestDispatcher()

@get:Rule
val mainDispatcherRule = MainDispatcherRule(dispatcher)
```

Esto reemplaza los pares repetidos de `@Before`/`@After` que instalaban y
restauraban el hilo principal. Cada prueba conserva su propia instancia de
la regla y de los repositorios simulados.

## Ubicación y ejecución

- Tests locales: `src/test` del módulo correspondiente.
- Tests con dispositivo o emulador Android: `src/androidTest`.
- Reglas de Firestore: `firestore-tests`.

Las pruebas de reglas se ejecutan con `pnpm install --frozen-lockfile` y `pnpm test`
desde `firestore-tests`; el script inicia el emulador con `demo-barberia`.
Para pruebas Android se necesita un dispositivo o emulador. El diagnóstico
`TurnosFirestoreReadTest` requiere habilitación explícita y sesión real; no debe
confundirse con las pruebas locales. Ver [comandos y validación](validacion-y-reservas.md).

Para ejecutar las pruebas locales de los módulos adaptados, desde la raíz:

```powershell
.\gradlew.bat :core:domain:test :features:auth:testDebugUnitTest :features:turnos:testDebugUnitTest :features:home:testDebugUnitTest :features:cuenta:testDebugUnitTest
```
