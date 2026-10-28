# Idiomas de la aplicación

La interfaz admite español e inglés. En **Mi cuenta → Idioma** se puede elegir **Español** o **English**. La elección se aplica inmediatamente y se conserva al cerrar la aplicación. Antes de elegir, la app sigue el idioma del dispositivo y muestra el idioma efectivo en el selector. En Android 13 o superior se comparte con Ajustes → Aplicaciones → BarberApp → Idioma.

La actividad usa AppCompat y `setApplicationLocales`: una lista vacía restablece el idioma del dispositivo. Android guarda la preferencia desde Android 13; en versiones anteriores, AppCompat la guarda mediante `autoStoreLocales`. No se duplica en preferencias propias. La pantalla puede recrearse al cambiar el idioma; los ViewModels y el estado guardable se conservan. Los recordatorios usan `ContextCompat.getContextForLanguage` para resolver sus textos incluso fuera de la actividad.

## Agregar o modificar un texto

- Guardar el texto en `src/main/res/values/strings.xml` del módulo que lo muestra. Estos recursos son el respaldo en español.
- Mantener la misma versión española en `src/main/res/values-es/strings.xml`. La variante explícita permite elegir español cuando el dispositivo tiene varias preferencias, por ejemplo español primero e inglés segundo.
- Agregar la misma clave en `src/main/res/values-en/strings.xml` con su traducción al inglés.
- En Compose, usar `stringResource(R.string.clave)`. Para cantidades con singular/plural, usar `pluralStringResource` y `<plurals>`.
- Para datos variables, usar parámetros como `%1$s` y `%2$d`; no concatenar palabras traducidas con valores.
- En callbacks de Compose, obtener `LocalResources.current` durante la composición y usar `resources.getString(...)` dentro del callback. Fuera de Compose, usar el contexto Android disponible.

Los mensajes guardados en los ViewModels se representan mediante `UiMessage`: conservan la referencia al recurso y sus argumentos, y se resuelven al mostrarlos. Las validaciones del dominio usan `DomainError.Known` y `ErrorReason`, sin depender de Android. Su texto se define en los recursos de `core/ui` y se selecciona en `DomainErrorMessage.kt`.

No traducir identificadores persistidos, estados de Firestore, claves de preferencias, nombres de personas ni descripciones ingresadas por usuarios. Los mensajes externos desconocidos tampoco se traducen automáticamente. Las fechas usan el idioma vigente; los importes del negocio siguen expresándose en ARS.

## Comprobaciones

El idioma se gestiona en `core:ui/theme/LanguagePreference.kt` y desde Mi cuenta.
La preferencia de apariencia se conserva por separado mediante
`AparienciaRepository` y su implementación en `core:data`; no reemplaza el
almacenamiento de idioma administrado por AppCompat y Android.

`LocalizationResourcesTest` verifica con Android la selección de español e inglés, variantes regionales, el respaldo para idiomas no admitidos y los parámetros y plurales de recordatorios. Se ejecuta en un dispositivo o emulador con:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=ar.edu.unaj.am.myapplication.LocalizationResourcesTest"
```

`LanguagePickerTest` comprueba las dos opciones, la ausencia de la opción del dispositivo y la cancelación del diálogo de Mi cuenta. `ApplicationLanguageTest` verifica el cambio efectivo entre inglés y español, su conservación al recrear la actividad y el regreso al idioma del dispositivo mediante la API de Android; restaura la preferencia anterior al terminar.

Al agregar otro idioma, crear sus recursos en cada módulo y declararlo en `app/src/main/res/xml/locales_config.xml`. Revisar además los textos largos en botones, formularios y diálogos.
