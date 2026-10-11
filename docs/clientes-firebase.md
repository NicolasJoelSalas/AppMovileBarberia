# Perfil de clientes

La autenticación continúa en core:auth. ClienteRepository y
EnsureClienteProfileUseCase están en core:domain; FirestoreClienteRepository
está en core:data. AppContainer conecta estas dependencias.

Después del registro o ingreso (correo o Google), y al restaurar una sesión,
LoginViewModel completa el perfil antes de abrir la aplicación.

La transacción consulta barberos/{UID}. Si existe, no crea un cliente, incluso
si el barbero está deshabilitado. Si no existe, crea clientes/{UID} solamente
si falta, sin sobrescribir perfiles existentes.

Campos: Nombre (nombre proporcionado por Google, o vacío si Authentication
no tiene nombre), Correo y CreadoEn (timestamp del servidor). No se guarda
la contraseña. El formulario actual de correo no solicita un nombre.

Si falla Firestore, la cuenta de Authentication se conserva y la pantalla
ofrece Reintentar sin volver a registrar la cuenta.

## Configuración requerida

Copiar firestore.rules a Firebase Console > Firestore Database > Reglas y
publicar. Guardar el archivo local no publica las reglas en Firebase.
Se permite a cada cuenta consultar su propio documento de barbero y cliente.
Los barberos habilitados también pueden obtener documentos individuales de
clientes y servicios para mostrar los nombres en sus tarjetas; no pueden
listar clientes. La consulta de turnos mantiene el filtro por BarberoId.
Solo puede crear su propio perfil de cliente si no es barbero, y completar Nombre
si estaba vacío. No puede listar clientes ni modificar barberos. La lista de turnos
está limitada al barbero habilitado cuyo UID coincida con BarberoId, o al cliente
cuyo UID coincida con ClienteId. Cada cliente ve sus reservas en "Mis turnos" y
puede leer individualmente su propio comprobante para reintentar una reserva.
El barbero habilitado puede editar únicamente su Nombre, ZonaHoraria y agenda.
Ver docs/reservas-semanales.md para publicación de horarios y reglas de reservas.

Los barberos precargados necesitan usar como ID de documento su UID real
de Authentication; un documento de Firestore por sí solo no crea una cuenta
ni permite iniciar sesión.
