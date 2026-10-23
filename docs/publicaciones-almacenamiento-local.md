# Publicaciones e imágenes sin conexión para clientes

Al abrir Inicio con conexión, se sincronizan automáticamente las últimas 50 publicaciones
del feed y se descargan sus fotos. Cada foto que termina de guardarse puede verse sin red,
incluso después de reiniciar la app, mientras se conserve la sesión de cliente previamente validada.
No se guardan fotos nuevas si la aplicación no pudo descargarlas antes de perder la conexión.

## Estructura

- `core/domain/model/Publicacion`: conserva la URL remota y agrega una ruta local opcional.
- `core/database/model/PublicacionEntity`, `dao/PublicacionDao`, `mapper/PublicacionEntityMapper`:
  Room guarda los datos del trabajo y la ruta, separados por cliente. La imagen no es un BLOB.
- `core/database/migration/Migraciones`: migración 1 → 2 que agrega la tabla sin borrar turnos.
- `core/network/DescargadorImagenPublicacion`: descarga cancelable por HTTPS desde Cloudinary,
  con timeout y límite de 4 MB por foto. No sigue redirecciones.
- `core/data/images/ImagenesPublicacionesLocales`: guarda archivos en el directorio privado
  persistente `noBackupFilesDir/publicaciones`, con nombres derivados de hashes del UID y URL.
- `core/data/repository/PublicacionesClienteLocal`: conecta Firestore, Room y archivos.
- `app/di/AppContainer`: crea las dependencias y registra la migración en el builder Android.
- `features/home/HomeScreen`: muestra el archivo local cuando está disponible y conserva el feed
  guardado junto a un aviso si no puede actualizarse.

## Sincronización y limpieza

La caché se habilita únicamente para un perfil de cliente ya confirmado. Si el catálogo termina
de validar el cliente después de abrir Inicio, el observador cambia automáticamente al flujo local.
Los barberos conservan su flujo remoto de publicación y edición.

Solo una lista confirmada por el servidor reemplaza los metadatos locales. Los snapshots de caché
de Firebase o con escrituras pendientes no se usan para eliminar la copia completa de Room.
Se reutilizan las fotos cuyo archivo ya existe y cuya URL no cambió. Las fotos faltantes se
descargan progresivamente; si alguna falla, la publicación conserva su URL y puede reintentarse
con el botón Actualizar. No hace falta permiso para acceder a la galería ni almacenamiento externo.

Al sincronizar se eliminan los archivos que ya no corresponden al feed actual. Al cerrar sesión
se borran los metadatos y fotos del cliente. Una descarga pendiente no puede recrearlos después
de la limpieza. Esto no elimina las fotos originales de Cloudinary ni las guarda en la galería.

## Verificación

```powershell
.\gradlew.bat :core:database:test :core:data:testDebugUnitTest :features:home:testDebugUnitTest :app:assembleDebug
```

Las pruebas cubren migración con conservación de turnos, persistencia al reabrir SQLite y archivos,
aislamiento de clientes, reutilización y reemplazo de fotos, errores de descarga y salida durante
una descarga. El descargador se prueba con respuestas HTTP simuladas, sin usar la red real.

Prueba manual: ingresar como cliente, abrir Inicio y esperar que se descarguen las fotos.
Cerrar la app, activar modo avión y volver a Inicio. Deben verse las imágenes descargadas y
un aviso de falta de conexión. Al reconectar, usar Actualizar. Cerrar sesión y comprobar la limpieza.
