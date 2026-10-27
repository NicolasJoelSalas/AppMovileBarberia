# Reservas y validación sin Blaze

La aplicación utiliza Firebase Auth, Firestore, Room y Cloudinary. Las reservas
no dependen de Cloud Functions ni requieren cambiar al plan Blaze.

## Comportamiento

- Servicios propios y activos de 5 a 240 minutos, incluso 41 o 75 minutos.
- Esta semana y la próxima, de lunes a sábado, respetando pausas y cierres.
- Cada reserva ocupa todos sus minutos dentro de una única franja publicada.
- Reserva y ocupación se actualizan en la misma transacción. Ante competencia por
  minutos superpuestos solo puede confirmarse una reserva.
- Cancelar libera el intervalo completo. Modificar conserva identidad y creación;
  si falla, no cambia el turno anterior. Puede cambiarse de servicio o barbero.
- Editar un servicio no modifica fechas de turnos existentes. Las confirmaciones
  con duración anterior se rechazan y requieren actualizar horarios.
- Al cambiar la agenda hay que publicarla nuevamente. Se cancelan reservas futuras
  que quedan fuera de las nuevas franjas; los turnos de otra semana se conservan.
- Se consultan turnos guardados sin conexión; los clientes también tienen publicaciones
  e imágenes locales. No se confirman reservas nuevas sin conexión.

## Implementación

`OcupacionDia` y `crearDiaAtencion` en `core:domain` representan la atención y la
ocupación de un día mediante cadenas de 0/1, con un carácter por minuto. Respetan
la zona horaria y la duración real del día, incluso ante cambios de horario estacional.

`core:data/remote/FirestoreReservasApi` utiliza las transacciones del SDK de Firestore.
`ReservaRepository` mantiene su contrato y las features siguen utilizando casos de uso.
`core:testing` conserva sus herramientas compartidas sin incluirlas en producción.

En `disponibilidad/{barberoId}_{inicioDiaMillis}` se guardan:

- `Inicio` y `Fin`: límites del día como timestamps.
- `Atencion`: minutos publicados por el barbero.
- `Ocupacion`: minutos que ya pertenecen a reservas.
- `Agenda`, `AgendaId`, `ZonaHoraria` y `BarberoId`: configuración publicada.
- `UltimoTurnoId`: vínculo opaco con la operación atómica más reciente.

El documento no contiene nombres ni UID de clientes. Las reglas verifican el
intervalo completo, la duración del servicio y el cambio exacto de ocupación.
No permiten liberar minutos ajenos, dejar minutos sin ocupar, modificar la
atención como cliente ni reutilizar una cancelación para borrar una reserva nueva.

## Publicación y compatibilidad

El barbero cambia a `ReservasVersion = 3` antes de inicializar su disponibilidad.
Esto cierra las escrituras de las versiones antiguas. Luego se consultan los
turnos existentes desde el servidor y se incluyen en las máscaras del día.
Las publicaciones siguientes conservan la ocupación y actualizan la atención.
Las reservas excluidas se cancelan mediante las mismas transacciones protegidas.

Si una publicación falla parcialmente, se puede reintentar: no borra las ocupaciones
existentes. Una semana no publicada informa que el barbero debe publicarla.
Los turnos anteriores sin `DiaId` se mantienen; después de publicar su semana
pueden modificarse o cancelarse con el flujo nuevo. Las apps anteriores deberán
actualizarse para reservar con un barbero migrado.

No cambiar la zona horaria con reservas futuras: la publicación lo rechaza cuando
sus documentos pertenecen a otro inicio de día. Primero se deben resolver esas reservas.

## Activación en Spark

1. Publicar `firestore.rules` en el proyecto Firebase correcto, desde la consola
   de Firestore o mediante:

```powershell
firebase deploy --project ID_DEL_PROYECTO --only firestore:rules
```

2. Instalar la aplicación actualizada.
3. Cada barbero debe guardar y publicar esta semana y la próxima.

No desplegar funciones ni habilitar facturación. El plan Spark conserva sus límites
de uso; cada reserva actualiza el turno y uno o dos documentos diarios en vez de
crear un documento por minuto.

## Pruebas

```powershell
.\gradlew.bat test :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas del dominio cubren distintas duraciones, descansos, cierre exacto,
liberación, modificaciones y zonas horarias. Las de `core:data` ejecutan el adaptador
real con documentos controlados y comprueban que no lea después de empezar a escribir
una transacción.

En `firestore-tests`, instalar dependencias con `pnpm install --frozen-lockfile` y
usar el emulador local:

```powershell
node node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --config ../firebase.json --project demo-barberia --only firestore "node --test --test-concurrency=1 disponibilidad.test.cjs reservas.test.cjs"
```

Esto prueba concurrencia real y autorización con el SDK de cliente, sin un servidor
privilegiado. Se usa exclusivamente el proyecto de demostración `demo-barberia`.

`AlmacenamientoOfflineTest` comprueba Room real, reapertura, aislamiento, ViewModel y
pantalla para cliente y barbero, inyectando una falla de sincronización. No cambia
el Wi-Fi ni sustituye la prueba manual de cerrar el proceso y abrir la app sin red.
`TurnosFirestoreReadTest` sigue siendo un diagnóstico remoto opcional y se omite
sin `-Pandroid.testInstrumentationRunnerArguments.validarFirestoreReal=true`.

Se retiraron el generador fijo de 40 minutos, la dependencia de Cloud Functions para reservas,
los módulos inactivos del template y sus pruebas/recursos de ejemplo. Se conservan
las migraciones y los datos históricos necesarios para la aplicación.

La carpeta `functions` y `firebase.functions.json` conservan el backend opcional de
notificaciones anterior. No se necesitan, no se despliegan y no son dependencias
de las reservas con Spark.
