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
Solo puede crear su propio perfil de cliente si no es barbero. No puede
modificar barberos ni listar clientes. El acceso a turnos sigue limitado
al barbero habilitado cuyo UID coincida con BarberoId.

Los barberos precargados necesitan usar como ID de documento su UID real
de Authentication; un documento de Firestore por sí solo no crea una cuenta
ni permite iniciar sesión.
