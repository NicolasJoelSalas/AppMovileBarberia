# Inicio y trabajos de la barbería

La pestaña Inicio reemplaza Playground. Presenta las 50 publicaciones más recientes
para las cuentas autenticadas. Los barberos habilitados pueden agregar trabajos
y eliminar sus propias publicaciones. El cliente puede reservar con el barbero
de una publicación: se abre el calendario con ese barbero seleccionado.

## Capas

- `features:home`: pantalla, ViewModel, cámara del sistema, selector de imágenes,
  vista previa y ajuste del encuadre. El borrador y su identificador sobreviven a
  rotación y restauración del proceso mediante SavedStateHandle.
- `core:domain`: Publicacion, NuevaPublicacion, interfaz PublicacionRepository y
  casos de uso para observar, publicar y eliminar.
- `core:data`: FirestorePublicacionRepository autoriza al barbero, prepara la foto,
  coordina la subida y guarda el documento. PreparadorImagenTrabajo corrige EXIF,
  limita la resolución de decodificación y exporta JPEG 1080 × 1080 con calidad 85.
- `core:network`: contrato Retrofit de Cloudinary y cliente HTTP.
- `app`: configuración, inyección manual y navegación.

## Conectar Cloudinary

La integración está implementada, pero no se creó ni conectó una cuenta externa.
Firebase continúa en Spark; las imágenes se almacenan en Cloudinary, no en Firestore.

1. Creá tu cuenta en https://cloudinary.com/ y copiá el **Cloud name** de tu entorno.
2. En Settings → Upload → Upload presets, agregá un preset, por ejemplo
   `barberia_trabajos`, con Signing mode **Unsigned**.
3. Restringí Allowed formats a `jpg`, habilitá **Disallow public ID**, usá una
   carpeta exclusiva para trabajos y configurá la transformación de entrada
   a imagen cuadrada de 1080 × 1080. Dejá las imágenes con entrega pública.
   El servidor no debe transformar el archivo a otro formato: las reglas esperan JPG.
4. En el `local.properties` de la raíz, conservá `sdk.dir` y agregá:

   ```properties
   cloudinary.cloudName=TU_CLOUD_NAME
   cloudinary.uploadPreset=barberia_trabajos
   ```

   Ambos valores admiten letras, números, guion y guion bajo. No agregues API secret
   ni claves privadas. local.properties está ignorado por Git.
5. Sincronizá Gradle y recompilá/instalá la app: la configuración se incorpora a BuildConfig.
6. Publicá el contenido completo de `firestore.rules` en Firebase Console → Firestore → Reglas.
   Guardar el archivo local no modifica las reglas del proyecto remoto.

Documentación del proveedor: https://cloudinary.com/documentation/upload_presets
y https://cloudinary.com/documentation/android_image_and_video_upload

## Colección publicaciones

No hace falta crearla manualmente. La primera publicación crea el documento
`publicaciones/{SolicitudId}`, con un UUID estable durante el reintento:

```text
BarberoId: UID de Authentication
BarberoNombre: Nombre del perfil del barbero al publicar
ImagenUrl: URL HTTPS de Cloudinary, terminada en .jpg
ImagenPublicId: identificador del recurso en Cloudinary
Descripcion: texto, hasta 300 caracteres; puede estar vacío
CreadoEn: timestamp del servidor
```

Las reglas comprueban que el barbero esté habilitado, que publique con su UID y
nombre de perfil, y que solo elimine documentos propios. No se permiten ediciones
de publicaciones ni campos extra. Las cuentas sin sesión no pueden leerlas.

## Uso y verificación manual

Ingresá como barbero → Inicio → Agregar trabajo → Cámara o Galería. Ajustá el
encuadre, agregá una descripción opcional y publicá. Ingresá desde otra cuenta
para comprobar que aparece con foto, nombre y fecha. Como cliente, tocá Reservar
turno y verificá el barbero seleccionado. Otra cuenta no debe poder eliminarla.

Se usa la cámara externa del sistema y el Photo Picker, sin permisos generales de
cámara o almacenamiento. FileProvider solo comparte la carpeta de capturas en caché.
La pantalla bloquea salida y doble envío durante la publicación; fuera de la subida,
confirma antes de descartar el borrador. La subida requiere conexión.

## Límites de esta integración sin backend

Un preset unsigned es público: alguien que lo extraiga de la aplicación puede subir
archivos directamente a Cloudinary. Las reglas de Firebase protegen las publicaciones,
pero no autentican las subidas al proveedor. Restringí el preset y supervisá su uso.
Para exigir Authentication también en la subida, hace falta un backend que valide el
token y firme la petición; nunca incluir el API secret dentro del APK.

Eliminar una publicación borra su documento de Firestore y la oculta de Inicio.
La imagen permanece en Cloudinary: su eliminación segura requiere una firma de backend
o borrarla manualmente en Media Library, usando ImagenPublicId. También puede quedar
una imagen sin documento si la subida terminó y después se descartó el reintento.
Mientras vive la aplicación se reutiliza la imagen subida al reintentar un fallo de
Firestore. Tras cierre del proceso, se conserva el UUID del documento para evitar
duplicarlo, aunque podría repetirse la subida de la imagen.

Las reglas locales y las pruebas no sustituyen la prueba real con tu cuenta de
Cloudinary y Firebase. No se publicaron reglas ni se realizaron subidas remotas
durante la implementación.
