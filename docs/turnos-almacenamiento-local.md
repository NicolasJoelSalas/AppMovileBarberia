# Turnos del cliente: almacenamiento local

## Responsabilidades

- `core/database`: entidad de Room, DAO, base SQLite y mappers. Sigue siendo Kotlin/JVM y recibe una función de creación; no importa `Context`.
- `core/data`: sincroniza Firestore con Room y recuerda un perfil de cliente ya validado para permitir consultar sin conexión.
- `core/domain`: conserva los contratos y casos de uso. `CatalogoReservas.sinConexion` permite explicar cuándo la disponibilidad remota no está disponible.
- `features/turnos`: observa los modelos de dominio y conserva la lista ante errores de sincronización.
- `app`: construye las dependencias, aporta el builder de Room para Android y coordina la limpieza de sesión. El builder debe compilarse contra el runtime de la plataforma que lo ejecutará; el constructor de JVM difiere del de Android.

## Lectura y sincronización

Para `comoCliente = true`, la lista se obtiene de `TurnoDao.observarPorCliente(uid)`.
Un listener remoto actualiza la copia local con una transacción de reemplazo por cliente.
Los resultados de caché de Firebase y las escrituras pendientes no reemplazan la tabla: podrían representar una lista incompleta.
Las consultas remotas confirmadas, incluso una lista vacía, sí reemplazan los registros del cliente.
Los nombres de cliente y servicio se guardan junto al turno para poder mostrarlos sin red.

Un error de red conserva la lista y muestra un aviso con opción de reintentar.
La rama del barbero continúa consultando Firestore.
Reservar, modificar y cancelar requieren confirmación de Firestore; no se encolan escrituras offline en Room.

## Sesión y acceso sin conexión

El catálogo remoto confirma el perfil de cliente y guarda su UID y nombre en preferencias privadas.
Una sesión de Firebase existente con ese perfil previamente validado puede entrar a la aplicación sin recrear el perfil mediante una transacción de red.
Si el catálogo falla por falta de conexión, se permite acceder a Mis turnos con el catálogo local mínimo; no hay disponibilidad remota para reservar.
El primer acceso y la creación de perfiles requieren conexión. Los permisos remotos siguen siendo responsabilidad de Firestore.

Al salir se cancelan los ViewModels de la sesión, se invalidan sincronizaciones anteriores y se borran los turnos del UID. Luego se cierra Firebase.
Las consultas y la limpieza están limitadas al cliente correspondiente.

## Verificación

```powershell
.\gradlew.bat :core:database:test :core:data:testDebugUnitTest :features:turnos:testDebugUnitTest :features:auth:testDebugUnitTest :app:assembleDebug
```

Las pruebas de Room abren un archivo SQLite real, lo cierran y verifican persistencia al reabrirlo.
También cubren aislamiento de clientes, sincronización de listas vacías, conservación ante caché parcial, callbacks posteriores a la limpieza y presentación de errores sin perder la lista.

Prueba manual en Android: ingresar con conexión, abrir Mis turnos, cerrar la app, activar modo avión y volver a abrirla. Verificar los turnos guardados, reconectar y comprobar cambios. Cerrar sesión y entrar con otro cliente para verificar separación de cuentas.

La base comienza con versión 1 y `exportSchema = false`. Antes de modificar columnas en una versión distribuida, habilitar la exportación de esquemas y definir las migraciones correspondientes.
