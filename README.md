# Barbería UNAJ

Aplicación Android de clientes y barberos, con Jetpack Compose, ViewModels,
Hilt, Firebase y persistencia local con Room.

## Funcionalidades

- Clientes: autenticación, publicaciones, reservas, modificación y cancelación,
  recordatorios y rutas hacia la barbería.
- Barberos: gestión de servicios, agenda semanal, turnos, ganancias y avisos.
- Consulta offline de turnos de la cuenta; publicaciones e imágenes locales para clientes.
- Servicios de 5 a 240 minutos. Las reservas respetan duración real, descansos,
  cierres y turnos existentes mediante transacciones y reglas de Firestore, sin Blaze.

## Estructura

| Módulo | Responsabilidad |
|---|---|
| `app` | Inicio, navegación, Hilt, sesión y recordatorios Android |
| `core:domain` | Modelos, contratos, validaciones y casos de uso Kotlin |
| `core:data` | Repositorios Firebase, caché local y adaptación de respuestas |
| `core:database` | Room, DAO, entidades y migraciones |
| `core:network` | Retrofit, rutas y carga/descarga de imágenes |
| `core:auth` | Adaptadores de autenticación Firebase |
| `core:ui` | Tema, componentes, mapas y mensajes compartidos |
| `core:testing` | Repositorios simulados, datos y reglas de pruebas |
| `features:auth` | Inicio de sesión y registro |
| `features:home` | Publicaciones |
| `features:turnos` | Reservas, agenda, listado y rutas |
| `features:cuenta` | Perfil, servicios, ubicación y ganancias |
| `features:notifications` | Avisos, tokens y trabajo en segundo plano |
| `firestore-tests` | Pruebas de las reglas de acceso |

Los tests se mantienen en cada módulo. `core:testing` se usa exclusivamente
como dependencia de pruebas. Ver [arquitectura](ARCHITECTURE.md).

## Ejecutar y comprobar

Requiere Android Studio compatible con las versiones de `gradle/libs.versions.toml`,
SDK 37, JDK 21 y dispositivo/emulador Android 8.0 o superior.
Configurar `local.properties` y la conexión Firebase de la aplicación.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
.\gradlew.bat :app:connectedDebugAndroidTest
```

Para las pruebas de reglas se utilizan Node y el emulador de Firestore desde
`firestore-tests`. Ver [validación y activación](docs/validacion-y-reservas.md).

**Las reservas no requieren Cloud Functions ni plan Blaze.** Para activar la versión
nueva, publicar las reglas de Firestore, actualizar la app y republicar las agendas.

## Documentación

- [Validación, duración variable y activación](docs/validacion-y-reservas.md)
- [Herramientas compartidas de pruebas](docs/testing-compartido.md)
- [Inyección de dependencias](docs/inyeccion-dependencias.md)
- [Turnos offline](docs/turnos-almacenamiento-local.md)
- [Publicaciones offline](docs/publicaciones-almacenamiento-local.md)
- [Gestión de servicios](docs/gestion-servicios.md)
- [Ubicación y rutas](docs/ubicacion-y-rutas.md)
- [Notificaciones](docs/notificaciones-fcm.md)
