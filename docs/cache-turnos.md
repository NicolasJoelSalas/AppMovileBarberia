# Caché local de turnos

`core/database` contiene Room (`TurnosDatabase`, versión 1). La base `turnos.db`
guarda los turnos completos, incluidos nombres y precio cobrado, y la fecha de
última sincronización. El esquema exportado se versiona en `core/database/schemas`.

`CachedTurnoRepository` observa Room para alimentar la pantalla. En paralelo,
escucha `FirestoreTurnoRepository(serverOnly = true)` y reemplaza atómicamente
la copia local con cada respuesta completa del servidor. No usa snapshots
provenientes de la caché de Firestore ni escrituras pendientes para reemplazarla.
El listener incluye cambios de metadatos para recibir la confirmación del servidor
aunque los documentos sean idénticos a los de su caché.

Los datos se separan por UID y rol (cliente/barbero). Al cambiar de cuenta o cerrar
sesión se cancela la observación anterior. Las copias permanecen en almacenamiento
privado y solo se consultan con el alcance del usuario autenticado.

Una sincronización fallida conserva la última copia. Una respuesta vacía confirmada
sí elimina los turnos del alcance y registra esa lista vacía como sincronizada.
Sin descarga previa se informa que todavía no hay datos locales: no se interpreta
como una agenda vacía. La pantalla muestra cuándo se guardó la última copia y
permite reintentar si la actualización falla.

La sincronización ocurre mientras se observa la lista; no hay una tarea periódica
en segundo plano. La lectura funciona sin conexión luego de la primera descarga,
incluso al volver a abrir la aplicación. Reservas, cancelaciones, modificaciones y
marcado de realizado siguen usando las validaciones remotas existentes; no hay
una cola de escrituras offline.

## Verificación

- `CachedTurnoRepositoryTest`: lectura de caché, conservación ante errores,
  reemplazo por lista vacía, separación por cuenta/rol y cierre de sesión.
- `TurnosRoomCacheTest` (instrumentación): cierra y reabre la base real, verifica
  persistencia de todos los campos, aislamiento y persistencia de una lista vacía.
  Usa el almacenamiento del APK de pruebas para no modificar los datos de la app.
- Prueba manual: abrir los turnos con conexión, activar modo avión, cerrar y abrir
  la aplicación; comprobar tarjetas y recordatorios disponibles. Recuperar conexión
  y comprobar que los cambios realizados desde otra sesión se reflejan.
