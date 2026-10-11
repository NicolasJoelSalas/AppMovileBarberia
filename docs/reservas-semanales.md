# Reservas con Firebase Spark

No requiere Cloud Functions, Blaze ni módulos Android nuevos. Solo Firebase
Authentication y Cloud Firestore dentro de las cuotas de tu plan.

## Responsabilidades

- `core:domain`: modelos, contrato `ReservaRepository`, casos de uso y cálculo
  de horarios de 40 minutos mediante `GenerarSemanaUseCase`. Lunes a sábado,
  sin superponer franjas, respetando descansos, sin horarios pasados.
- `core:data`: `FirestoreReservaRepository` traduce documentos, publica horarios,
  consulta disponibilidad y confirma la reserva con una transacción de Firestore.
- `features:turnos`: pantalla, selección, confirmación y estado del ViewModel.
- `app`: `AppContainer` conecta implementaciones y casos de uso.
- `firestore.rules`: autorización y validación obligatoria de las escrituras.
- `firestore-tests`: pruebas locales de reglas. No es un módulo Android ni backend desplegable.

## Activación en tu Firebase

1. Abrí Firebase Console > Firestore Database > Reglas.
2. Reemplazá las reglas con el contenido completo de `firestore.rules` y publicalas.
   No uses permisos abiertos. Las reglas locales no se publican automáticamente.
3. Verificá el perfil del barbero y los servicios indicados más abajo.
4. Ejecutá la nueva versión de la aplicación con una cuenta de barbero habilitado.
5. En Turnos, abrí **Gestionar semana**, elegí **Semana actual** o **Próxima semana**,
   configurá sus días y franjas, y pulsá **Guardar y publicar semana**. Repetí para la
   otra semana si también querés ofrecerla. Si ya tenés la agenda actual, también podés usar
   **Publicar / actualizar horarios de esta semana** sin editarla.
6. Entrá como cliente, elegí la semana, barbero y servicio, actualizá disponibilidad y reservá.

La ventana de trabajo abarca la semana actual y la próxima. Cada semana se guarda
por separado y guardar publica únicamente la semana elegida. Cuando llega un nuevo
lunes, la próxima semana pasa a ser la actual, conservando su configuración y reservas.
El barbero debe configurar/publicar la nueva próxima semana; no hay publicación automática.
Publicar de nuevo conserva los horarios ocupados; retira los libres que dejaron
de corresponder. Si cambia la agenda o zona horaria, los horarios anteriores no
se pueden reservar hasta republicar. No hay generación automática en segundo plano.

La alternativa con CLI, sin desplegar funciones, es:

    firebase login
    firebase deploy --project barberia-2f570 --only firestore:rules

Este trabajo no publica reglas, modifica facturación ni crea datos en tu base real.

## Datos requeridos

- `barberos/{UID de Authentication}`: `Nombre` string, `Estado` boolean true,
  `ZonaHoraria` string `America/Argentina/Buenos_Aires`.
- `servicios/{ID}`: `NombreCorte` string, `Duracion` number **40**, `Activo` boolean true.
  Si existe `Estado`, ese boolean prevalece sobre `Activo`.
- `clientes/{UID}`: perfil generado en el registro/inicio de sesión. La confirmación
  completa `Nombre` si está vacío, conservando `Correo` y `CreadoEn`.
- `barberos/{UID}/agenda/{lunes en formato yyyy-MM-dd}`: mapas `Lunes`, `Martes`, `Miercoles`, `Jueves`,
  `Viernes`, `Sabado`. Cada uno contiene `Estado` boolean y `Franjas` array de mapas.
  Por ejemplo, las semanas del 12 y 19 de octubre se guardan en `agenda/2026-10-12`
  y `agenda/2026-10-19`. La aplicación crea estos documentos al guardar.
- El documento anterior `agenda/semanal` se conserva como plantilla inicial para
  semanas aún no configuradas. Editar una semana por fecha no modifica esa plantilla.

Ejemplo de día abierto:

```text
Lunes (map)
  Estado: true (boolean)
  Franjas (array)
    0 (map): Inicio: "09:00", Fin: "13:00"
    1 (map): Inicio: "16:00", Fin: "20:00"
```

Horas como strings HH:mm. Días cerrados: `Estado: false` y `Franjas: []`.
La pantalla Gestionar semana permite abrir/cerrar días y agregar, quitar o editar
franjas. Rechaza horas inválidas, franjas superpuestas y franjas menores a 40 minutos.
La configuración corresponde a las fechas de la semana elegida; cambiarla no afecta
la otra semana ni los turnos confirmados. La zona horaria se toma del perfil del barbero.
Si no hay documento para esa semana, se propone la plantilla `semanal`; si tampoco
existe la plantilla, todos los días comienzan cerrados. Los borradores de ambas semanas
se conservan al alternar el selector mientras el editor permanece abierto.
Si se guarda la agenda pero falla la publicación, muestra ambas situaciones por separado
y permite reintentar. La consola también se puede usar para editar la agenda.
No ofrezcas servicios con otra
duración: el flujo actual está diseñado para cortes de 40 minutos.

## Documentos generados

`horarios/{BarberoId}_{Inicio en milisegundos}`:

```text
BarberoId: UID del barbero
Inicio: timestamp
Fin: timestamp (Inicio + 40 minutos)
Estado: "libre" | "ocupado" | "bloqueado"
ZonaHoraria: string
Agenda: map con la agenda usada al publicar
AgendaId: lunes en formato yyyy-MM-dd, referencia al documento semanal
TurnoId: ID opaco del turno, solamente cuando está ocupado
```

Los horarios no guardan nombres, correos ni UID de clientes. Las cuentas
autenticadas pueden consultar disponibilidad; solo el dueño habilitado publica.
El ID determinista evita fabricar duplicados del mismo inicio al republicar.
La publicación solo retira horarios libres de la semana seleccionada. Los de la otra
semana y los ocupados se conservan.

## Actualizar desde la versión semanal anterior

Publicá nuevamente el archivo completo `firestore.rules`: ahora admite agendas por
fecha y valida `AgendaId`. Después entrá como barbero y guardá/publicá ambas semanas.
Los horarios libres antiguos sin `AgendaId` no se ofrecen hasta republicar; los turnos
confirmados anteriores no se borran ni se duplican. No hace falta recrear colecciones,
cambiar IDs de usuarios ni contratar Blaze.

`turnos/{SolicitudId}` (UUID estable mientras se reintenta una confirmación):

```text
ClienteId: UID de la cuenta autenticada
BarberoId: UID del barbero elegido
ServicioId: ID real de servicios
HorarioId: ID del horario ocupado
Inicio: timestamp
Fin: timestamp
Estado: "confirmado"
CreadoEn: timestamp del servidor
```

La transacción lee el horario, el perfil, el servicio y la agenda antes de escribir.
Ocupa el horario y crea el turno juntos. Las reglas usan `getAfter` para rechazar
operaciones incompletas, horas alteradas, suplantación de cliente, servicios o
barberos deshabilitados, horarios pasados y agendas desactualizadas. Dos clientes
compiten por el mismo documento: solamente uno confirma. El reintento devuelve
el comprobante existente sin crear otro.

Los barberos habilitados listan sus turnos con el filtro `BarberoId == UID`.
Los clientes listan únicamente sus propias reservas con `ClienteId == UID`.
La pestaña Turnos del cliente abre "Mis turnos", con día, horario y servicio.
El botón "Reservar turno" abre el calendario y, al confirmar, vuelve al listado.
El cliente también puede leer individualmente su propio comprobante para reintentar;
no puede consultar turnos de otros clientes. No se permite modificar
ni cancelar turnos desde este flujo. Tampoco se puede liberar un horario ocupado.

## Turnos anteriores

Al publicar se consultan los turnos existentes del barbero, también los cargados
manualmente: los activos bloquean cualquier horario que se superponga. Un turno
con Inicio/Fin inválidos debe corregirse antes de publicar. No se inventan ClienteId
ni ServicioId para completar documentos antiguos. Si cargás o cambiás turnos
manualmente después de publicar, volvé a publicar antes de ofrecer disponibilidad.

## Verificación local

Con el JDK de Android Studio en JAVA_HOME, ejecutá:

```powershell
.\gradlew.bat :app:assembleDebug :core:domain:test :core:data:testDebugUnitTest :features:turnos:testDebugUnitTest :features:auth:testDebugUnitTest
cd firestore-tests
pnpm install --ignore-scripts
pnpm test
```

Las pruebas de Firestore usan exclusivamente el proyecto local `demo-barberia`.
No necesitan login, Blaze ni acceso a la base de producción.

Prueba real después de publicar las reglas: publicar como barbero, reservar como
cliente, comprobar el turno con nombre/día/horario/servicio en la cuenta del barbero
y actualizar la disponibilidad desde otro cliente. El horario reservado no debe aparecer.
