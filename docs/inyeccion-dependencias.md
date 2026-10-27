# Inyección de dependencias con Hilt

`ComposeAcademyApp` inicia Hilt con `@HiltAndroidApp`; `MainActivity` es un punto de entrada con `@AndroidEntryPoint`. Ya no existe `AppContainer`.

## Responsabilidades por capa

| Ubicación | Responsabilidad |
|---|---|
| `app/di/AppModule` | Valores de `BuildConfig`, archivo de Room y reloj de la aplicación |
| `app/di/AndroidDatabaseModule` | Construcción de Room Android con las migraciones existentes |
| `core/auth/di/AuthModule` | Firebase Auth, implementación de autenticación e identidad de sesión |
| `core/data/di/DataModule` | Firestore, vínculos de repositorios, sesiones locales y configuración de publicaciones y reservas |
| `core/network/di/NetworkModule` | Clientes de rutas y Cloudinary, conservando sus tiempos de espera y configuración HTTP |
| `core/database/di/DatabaseModule` | DAO obtenidos de la instancia compartida de Room |
| `features/notifications/di/NotificationsModule` | Firebase Messaging, registro del dispositivo y avisos |
| `features/turnos/di/TurnosModule` | Reloj monotónico utilizado al seguir la ubicación |

`@Inject constructor` declara cómo crear un objeto a partir de sus dependencias. `@Binds` elige la implementación de una interfaz. `@Provides` configura objetos externos o dependencias que necesitan valores especiales. Los calificadores distinguen las cadenas de configuración de rutas y Cloudinary.

Los repositorios con estado, las sesiones locales, los clientes HTTP, la base de datos y los servicios de notificaciones se comparten mediante `@Singleton`. El dominio sigue siendo Kotlin/JVM y solo incorpora la anotación estándar `javax.inject.Inject`.

## Pantallas y sesión

Los ViewModels se obtienen con `hiltViewModel()` y conservan el alcance del almacén de sesión o de la entrada de Navigation 3. Hilt proporciona `SavedStateHandle` a publicaciones. `AuthenticatedSessionViewModel` propaga la factory y los extras del Activity para permitir su creación dentro del almacén de sesión.

`TurnoListViewModel` recibe el rol mediante una factory asistida; `ComoLlegarViewModel` recibe el identificador del barbero de la misma manera. Los casos de uso y demás dependencias los resuelve Hilt. Los constructores siguen siendo utilizables directamente en las pruebas.

`GestorSesion` conserva la limpieza de repositorios, archivos y sesiones locales y la desvinculación de notificaciones al salir. Recibe sus dependencias por constructor y no crea el grafo de objetos. El servicio de mensajes y el Worker mantienen sus contratos con `Application`, que ahora entrega los objetos creados por Hilt.

## Verificación

La compilación de `:app:assembleDebug` comprueba la generación del grafo completo de Hilt. Las pruebas de dominio, datos y ViewModels comprueban las reglas existentes con dependencias falsas, sin escribir en Firestore ni subir imágenes a Cloudinary.

`HiltInjectionTest` verifica en Android la creación normal y asistida de ViewModels dentro del almacén de sesión: conserva las instancias al recrear la actividad y obtiene instancias nuevas tras limpiar la sesión. La prueba existente de idioma también comprueba la recreación de la actividad con Hilt habilitado.
