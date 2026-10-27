# Arquitectura

La aplicación separa presentación, dominio y datos. Los módulos activos se registran
 en `settings.gradle.kts` y se describen en el [README](README.md).

## Dependencias

- `app` integra todas las capas mediante Hilt y organiza la navegación con Navigation 3.
- `features:*` utiliza contratos/casos de uso de `core:domain` y componentes de `core:ui`.
- `core:data` implementa repositorios usando `core:domain`, `core:database` y `core:network`.
- `core:auth` adapta Firebase Auth a los contratos de dominio.
- `core:database` conserva entidades Room y sus transformaciones al dominio.
- `core:domain` es Kotlin puro y no depende de Android ni Firebase.
- `core:testing` comparte herramientas, consumidas únicamente mediante dependencias de prueba.

Las features no acceden directamente a implementaciones de repositorios. Los
ViewModels reciben sus casos de uso por constructor, publican estado observable y
 las pantallas lo recogen respetando el ciclo de vida.

## Reservas y agenda

`ReservaRepository` define las operaciones y `FirestoreReservaRepository` adapta
el catálogo y la configuración. `FirestoreReservasApi` ejecuta las transacciones
directamente con el SDK de Firestore, sin Cloud Functions ni Blaze.

`OcupacionDia` y `crearDiaAtencion`, en dominio, representan cada minuto de atención
y ocupación. Las reglas de Firestore comprueban el cambio exacto del intervalo
completo junto con el turno. Las publicaciones conservan reservas existentes e
inicializan las anteriores a este formato. Los turnos mantienen sus fechas cuando
se edita un servicio; una confirmación con duración vieja se rechaza.

Las agendas se guardan por semana en `barberos/{uid}/agenda/{lunes}` y la
ocupación en `disponibilidad/{barberoId}_{inicioDiaMillis}`. Publicar una agenda
cancela los turnos futuros excluidos y activa `ReservasVersion = 3` para impedir
escrituras de las apps antiguas. Las operaciones respetan los límites de Firestore.

## Persistencia y sesión

Room conserva turnos separados por cliente/barbero. Los errores de sincronización
no borran datos previamente confirmados. La caché de publicaciones y los archivos
privados de imágenes se utilizan para clientes. El contenido visual del barbero
 puede permanecer en las cachés propias de Firestore/Coil, sin utilizar ese flujo Room.

Los reportes se calculan a partir de turnos guardados y los importes cobrados. El
cierre de sesión cancela observaciones/trabajos y evita registrar tokens tardíos.

## Pruebas

- `src/test` de cada módulo: dominio, ViewModels y adaptadores con dependencias controladas.
- `core:database`: SQLite real, persistencia, aislamiento y migraciones.
- `core:network`: comunicación HTTP local, respuestas, errores y cancelación.
- `app/src/androidTest`: interfaz, idiomas, Hilt y recorrido Room–ViewModel–pantalla al
  fallar la sincronización y reabrir la base, para cliente y barbero.
- `firestore-tests`: transacciones concurrentes, autorización y protección de los intervalos.

[Cómo verificar y activar](docs/validacion-y-reservas.md). Las pruebas de interfaz
con datos controlados no sustituyen la comprobación final de conectividad en un celular.
