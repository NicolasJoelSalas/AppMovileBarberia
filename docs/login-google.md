# Acceso y registro con Google

El botón «Continuar con Google» abre el selector de cuentas mediante Credential Manager.
LoginWithGoogleUseCase envía el token a AuthRepository; FirebaseAuthRepository autentica
con Firebase. El primer ingreso crea la cuenta; los siguientes reutilizan la existente.

## Configuración de Firebase

1. En Configuración del proyecto → Tus apps, seleccioná la aplicación Android
   `ar.edu.unaj.am.myapplication` y agregá la huella SHA-1 de la firma usada.
2. En Authentication → Método de acceso, habilitá Google, elegí el correo de soporte
   y guardá los cambios.
3. Descargá el nuevo `google-services.json` y reemplazá `app/google-services.json`.
   Debe contener un cliente OAuth de tipo web (`client_type: 3`). Google Services
   generará el recurso `default_web_client_id` a partir de ese cliente.
4. Sincronizá Gradle y volvé a instalar la app.

SHA-1 debug de este equipo (obtenida con `:app:signingReport`):

```text
A2:35:6C:E2:79:27:AB:A8:2E:35:85:FC:E0:F6:DA:B2:07:97:B3:4A
```

Para otras máquinas o una publicación, registrá también la huella de la firma
correspondiente. No uses el cliente Android como cliente web.

## Prueba manual

El enlace «No tengo cuenta · Registrarme» permite crear una cuenta con correo y
contraseña. Se valida el formato del correo, un mínimo de seis caracteres y la
confirmación. Firebase aplica además la política de contraseñas del proyecto y
rechaza correos ya registrados durante `createUserWithEmailAndPassword`.
Conservá la configuración de una cuenta por correo en Firebase Authentication
para evitar cuentas separadas entre proveedores. Google reutiliza una cuenta
existente según las reglas de vinculación de Firebase; no crea otra en cada ingreso.
Habilitá también el proveedor Correo electrónico/contraseña en Firebase.

- Usá un teléfono o emulador con Google Play Services y una cuenta de Google.
- Sin sesión previa en la app, tocá «Continuar con Google» y elegí una cuenta.
- Comprobá que se muestra la aplicación y que el usuario aparece en Authentication.
- Cancelá el selector: debe volver al formulario sin error y habilitar los botones.
- Registrá un correo nuevo con contraseña y comprobá su aparición en Authentication.
- Intentá registrar nuevamente el correo: debe mostrar el error sin abrir la app.
- Si falta el cliente web, se muestra que el acceso con Google no está disponible.

Referencia: https://firebase.google.com/docs/auth/android/google-signin
