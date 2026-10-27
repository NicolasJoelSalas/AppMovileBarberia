# Notificaciones de ABM de turnos

## Distribución

- `features:notifications`: SDK FCM, servicio receptor, canal Android, permiso y registro del dispositivo mediante casos de uso.
- `core:domain`: contrato `DispositivoRepository` y casos de uso de registro/desvinculación.
- `core:data`: implementación Firestore, limitada a barberos habilitados.
- `app`: composición de dependencias, coordinación con sesión y navegación a Mis turnos.
- `functions/src/`: backend TypeScript estricto con Firebase Admin y runtime Node.js 22; no se empaqueta en Android. Se compila a `functions/lib/`, ignorado por Git.

No se agregaron accesos de la feature a core:data ni credenciales de servidor al APK.

## Comportamiento

La función observa `turnos/{turnoId}`. Notifica altas, cancelaciones (la baja utilizada por la app), eliminaciones administrativas y cambios de cliente, servicio, horario, inicio, fin, estado o imagen de referencia. Una reasignación avisa al barbero anterior y al nuevo. Cambios exclusivamente contables o de metadatos no generan avisos.

Cada instalación tiene un identificador aleatorio persistido en preferencias y guarda su token en `barberos/{uid}/dispositivos/{instalacionId}`. Se sincroniza al abrir/reanudar la sesión y cuando FCM renueva el token. Un fallo se registra sin bloquear el ingreso y se reintenta en la siguiente reanudación. El permiso se solicita una vez a los barberos en Android 13+; si se rechaza, puede habilitarse desde Ajustes.

Al salir se intenta rotar el token y eliminar su asociación, con un máximo de ocho segundos para no bloquear el cierre sin conexión. Los mensajes son de datos y el receptor verifica el UID destinatario contra la sesión antes de mostrarlos. Así se descartan avisos dirigidos a una cuenta anterior incluso si falló la desvinculación remota. Tocar un aviso abre la lista de turnos actualizada, también cuando el turno ya no existe.

## Preparación y publicación

1. Usar el mismo proyecto Firebase del `google-services.json` de Android. No ejecutar `firebase init` sobre esta configuración porque los archivos ya están preparados.
2. Para publicar funciones, vincular facturación y habilitar el plan Blaze. Configurar alertas de presupuesto en Firebase/Google Cloud.
3. Instalar Node.js 22 o superior para las herramientas y Firebase CLI (`npm install -g firebase-tools`), e iniciar sesión con `firebase login`.
4. Desde la raíz ejecutar `npm ci --prefix functions` (incluyendo dependencias de desarrollo para compilar TypeScript).
5. Revisar la región `us-central1` de la función antes del primer despliegue y elegir una cercana a Firestore si corresponde.
6. Ejecutar `firebase deploy --project ID_DEL_PROYECTO --only firestore:rules,functions:notificarCambioTurno`. El paso `predeploy` compila TypeScript automáticamente y aborta si hay errores de tipos. Este paso publica reglas y recursos reales; los cambios locales por sí solos no activan el servicio.
7. Instalar la nueva app, ingresar como barbero habilitado, aceptar el permiso y comprobar que aparece un documento en su subcolección `dispositivos`.

## Verificación

- `npm test --prefix functions`: compila y prueba ABM, reasignación, cambios internos, validación de documentos remotos y manejo de códigos de error.
- `npm run check --prefix functions`: comprueba los tipos de todos los fuentes y pruebas sin generar JavaScript.
- `npm run build --prefix functions`: genera el código ejecutable en `lib/`. No editar estos archivos generados.
- En `firestore-tests`, ejecutar `npm test`: prueba las reglas incluyendo tokens propios/ajenos, esquema inválido y desvinculación de un barbero deshabilitado.
- `gradlew.bat :app:assembleDebug :core:domain:test`: compilación Android y pruebas de dominio.
- Con dos cuentas/dispositivos, crear, modificar, reasignar y cancelar una reserva. Probar barbero en primer y segundo plano, permiso rechazado, reinicio de la app y cierre/cambio de sesión. Una app detenida forzosamente desde Ajustes necesita volver a abrirse para recibir mensajes.

## Entrega y reintentos

Firestore puede invocar un evento varias veces y fuera de orden. Se registra el envío por evento, destinatario, instalación y token en `eventosNotificaciones`, una colección accesible solo por Admin. Una reserva temporal evita envíos concurrentes y los errores transitorios permiten reintentos. Los tokens definitivamente inválidos se eliminan sin borrar una renovación concurrente.

No existe garantía de envío exactamente una vez entre Firestore y FCM: si el proceso termina después de enviar y antes de guardar el resultado, el reintento puede repetir el mensaje. Android reutiliza el identificador del evento para reemplazar un aviso repetido. Los avisos no sustituyen la información actual de Firestore y pueden llegar tarde o fuera de orden. Los registros de envío se conservan; prever su limpieza/retención al operar el servicio a largo plazo.

FCM requiere un dispositivo o emulador con Google Play services y conectividad. Las pruebas locales no confirman una entrega push real: necesita despliegue y dispositivo.
