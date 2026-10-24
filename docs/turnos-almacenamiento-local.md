# Turnos del cliente y del barbero: almacenamiento local

## Responsabilidades

- `core/database`: entidad de Room, DAO, base SQLite y mappers. Sigue siendo Kotlin/JVM y recibe una función de creación; no importa `Context`.
- `core/data`: sincroniza Firestore con Room y recuerda el rol previamente validado de cliente o barbero para permitir consultar sin conexión.
- `core/domain`: conserva los contratos y casos de uso. `CatalogoReservas.sinConexion` permite explicar cuándo la disponibilidad remota no está disponible.
- `features/turnos`: observa los modelos de dominio y conserva la lista ante errores de sincronización.
- `app`: construye las dependencias, aporta el builder de Room para Android y coordina la limpieza de sesión. El builder debe compilarse contra el runtime de la plataforma que lo ejecutará; el constructor de JVM difiere del de Android.

## Lectura y sincronización

Para `comoCliente = true`, la lista se obtiene de `TurnoDao.observarPorCliente(uid)`.
Un listener remoto actualiza la copia local con una transacción de reemplazo por cliente.
Los resultados de caché de Firebase y las escrituras pendientes no reemplazan la tabla: podrían representar una lista incompleta.
Las consultas remotas confirmadas, incluso una lista vacía, sí reemplazan los registros del cliente.
Los nombres de cliente y servicio se guardan junto al turno para poder mostrarlos sin red.
Cada respuesta confirmada se guarda en Room antes de consultar esos nombres. Si falla una lectura auxiliar,
se conservan los nombres previamente guardados y el turno sigue disponible. La actualización posterior de
nombres solo modifica esas columnas: no sobrescribe estados ni importes que hayan cambiado entretanto.

Un error de red conserva la lista y muestra un aviso con opción de reintentar.
El reporte de ganancias también conserva sus datos y permite cambiar de semana o mes usando los turnos
locales mientras hay un error de actualización. Reintentar no elimina la lista o el reporte ya visible.
Para `comoCliente = false`, se observa `TurnoDao.observarPorBarbero(uid)` y se sincroniza exclusivamente la consulta `BarberoId == uid` de Firestore.
El reemplazo y la limpieza del barbero se filtran por su UID; no modifican los turnos de otros barberos.
Se reutiliza la misma tabla y entidad de turnos, sin cambios de esquema ni nueva migración.
Reservar, modificar y cancelar requieren confirmación de Firestore; no se encolan escrituras offline en Room.
Marcar un turno como realizado también requiere conexión: no queda una acción pendiente en Room.

## Sesión y acceso sin conexión

El catálogo remoto confirma el perfil de cliente y guarda su UID y nombre en preferencias privadas.
Una sesión de Firebase existente con ese perfil previamente validado puede entrar a la aplicación sin recrear el perfil mediante una transacción de red.
Si el catálogo falla por falta de conexión, se permite acceder a Mis turnos con el catálogo local mínimo; no hay disponibilidad remota para reservar.
El primer acceso y la creación de perfiles requieren conexión. Los permisos remotos siguen siendo responsabilidad de Firestore.

Para el barbero, `SesionBarberoLocal` recuerda el UID solo después de que el catálogo remoto confirma que el documento existe y tiene `Estado == true`.
Esto permite restaurar una sesión ya iniciada y mostrar la navegación de barbero aunque el catálogo falle por falta de red.
Un error de permisos no se convierte en un catálogo offline. La configuración de agenda y las publicaciones del barbero conservan sus operaciones remotas.
El guardado local de publicaciones e imágenes continúa limitado al cliente mediante `SesionClienteLocal`.

Al salir se cancelan los ViewModels de la sesión, se invalidan sincronizaciones anteriores y se borran los turnos del UID. Luego se cierra Firebase.
Las consultas y la limpieza están limitadas a los turnos asociados al UID de esa sesión, tanto como cliente como barbero.

## Verificación

```powershell
.\gradlew.bat :core:database:test :core:data:testDebugUnitTest :features:turnos:testDebugUnitTest :features:auth:testDebugUnitTest :app:assembleDebug
```

Las pruebas de Room abren un archivo SQLite real, lo cierran y verifican persistencia al reabrirlo.
También cubren aislamiento de clientes, sincronización de listas vacías, conservación ante caché parcial, callbacks posteriores a la limpieza y presentación de errores sin perder la lista.
Las pruebas del barbero cubren persistencia al reabrir SQLite, nombres e importes sin red, aislamiento de otros barberos, catálogo offline de un rol previamente confirmado y conservación de la lista ante errores de sincronización.

Prueba manual en Android: ingresar con conexión, abrir Mis turnos, cerrar la app, activar modo avión y volver a abrirla. Verificar los turnos guardados, reconectar y comprobar cambios. Cerrar sesión y entrar con otro cliente para verificar separación de cuentas.
Repetir con una cuenta de barbero: abrir Turnos con conexión, reiniciar sin red y entrar a Turnos. Comprobar nombres, servicios y estados; reconectar y actualizar. Al cerrar sesión y entrar con otro barbero no deben mostrarse los turnos de la cuenta anterior.

La versión actual de la base es 2 (la migración 1 → 2 agregó las publicaciones del cliente), con `exportSchema = false`. El soporte de turnos del barbero solo agrega consultas al DAO. Antes de modificar columnas en una versión distribuida, habilitar la exportación de esquemas y definir las migraciones correspondientes.
