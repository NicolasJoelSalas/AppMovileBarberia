# Avisos gratuitos de turnos y recepción FCM

## Funcionamiento actual sin Blaze

Los cambios de turnos generan **notificaciones locales**, sin solicitar envíos FCM desde Android. FCM sigue integrado como receptor: necesita un emisor autorizado externo para recibir push; no detecta cambios de Firestore por sí mismo. No se incluyen credenciales Admin en la app.

- `features:notifications`: `AvisosTurnos` compara versiones por usuario y muestra avisos; `TurnosWorker` usa WorkManager. También conserva el servicio receptor FCM, el canal y el permiso Android.
- `core:domain`: contrato de consulta/escucha, casos de uso y clasificación de ABM.
- `core:data`: consulta Firestore con las credenciales de la sesión, filtrando `BarberoId`. Solo utiliza respuestas confirmadas por el servidor.
- `app`: proporciona dependencias al Worker desde Application y activa los avisos después de confirmar el rol de barbero.

La escucha informa cambios mientras el proceso de la app sigue vivo. WorkManager consulta en segundo plano con intervalo mínimo de 15 minutos y requisito de conexión. Android decide la hora real y puede retrasarlo; no son avisos push inmediatos con el proceso cerrado. El trabajo persiste tras la muerte del proceso y los reinicios, pero una detención forzada desde Ajustes requiere volver a abrir la app.

La primera respuesta establece una base silenciosa. Las siguientes detectan altas, cambios de cliente/servicio/horario/estado/imagen, cancelaciones y turnos que desaparecen de la agenda (eliminación o reasignación). Nombres auxiliares e importes no generan avisos. La base se guarda por UID y se comparte entre escucha y Worker para evitar repetir avisos por la misma versión. Al salir se cancela el trabajo y se vuelve a verificar la sesión antes de mostrar cada aviso. Tocar el aviso abre la lista de turnos.

Comparar versiones solo permite ver el resultado final entre consultas: no garantiza un aviso por cada cambio intermedio. Los datos de comparación se guardan en preferencias privadas. Las lecturas consumen la cuota de Firestore Spark: no hay facturación Blaze, pero al agotar la cuota se interrumpe la sincronización. Cada consulta periódica lee todos los turnos del barbero; revisar este costo si crece su historial.

## Puesta en marcha

1. Instalar la nueva compilación Android.
2. Ingresar como barbero habilitado y permitir las notificaciones. En Android 13+ se solicita el permiso una vez; si fue rechazado, habilitarlo en Ajustes de la app. Verificar también el canal “Cambios en turnos”.
3. Esperar la primera carga de turnos antes de hacer la prueba.
4. Desde otra cuenta crear, modificar y cancelar una reserva. En la app del barbero abierta el aviso llega con la respuesta del servidor; con el proceso cerrado, esperar una ejecución periódica de Android.
5. Cerrar sesión y verificar que dejan de mostrarse avisos de esa cuenta.

No se necesita publicar ninguna función ni mantener una computadora encendida. Las reglas existentes de turnos ya autorizan al barbero a leer su agenda. Para conservar el registro opcional de tokens FCM, publicar `firestore.rules` desde la consola o con `firebase deploy --project barberia-2f570 --only firestore:rules`. Un fallo de registro FCM no detiene los avisos locales.

## Backend opcional conservado

`functions/src/` conserva la implementación TypeScript anterior como alternativa futura que **requiere Blaze**. Está excluida de `firebase.json`, por lo que no se despliega con la configuración predeterminada. Su configuración separada está en `firebase.functions.json`. No habilitar ambas rutas de avisos sin deduplicación adicional: podrían notificar el mismo cambio dos veces.

## Verificación

- `gradlew.bat :app:assembleDebug :core:domain:test`: compila Android y comprueba detección de ABM y exclusión de datos auxiliares.
- `npm test` dentro de `firestore-tests`: reglas de acceso a turnos/dispositivos.
- Prueba con dos sesiones: primer plano, segundo plano, permiso rechazado, sin conexión, reinicio y cierre de sesión. Los errores de consulta se registran como `BarberiaAvisos`; el Worker reintenta y la escucha se reconecta.
