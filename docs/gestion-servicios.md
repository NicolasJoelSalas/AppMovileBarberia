# Gestión de servicios del barbero

Desde **Mi cuenta → Mis servicios**, un barbero habilitado puede crear, editar, desactivar y reactivar servicios. La pantalla utiliza `features:cuenta`, casos de uso de `core:domain` y `FirestoreServicioRepository` de `core:data`; `app` conecta las dependencias y la navegación.

## Datos y permisos

Cada documento en `servicios` contiene `BarberoId`, `NombreCorte`, `Precio` (ARS), `Duracion` (minutos), `Estado` y `CreadoEn`. El identificador no cambia cuando se edita o desactiva. La baja modifica `Estado` a `false`, sin borrar documentos referenciados por turnos.

Se valida nombre de hasta 120 caracteres, precio no negativo con hasta dos decimales y duración entera entre 5 y 240 minutos. Los casos de uso representan los importes en centavos; el repositorio mantiene el campo numérico `Precio` existente. Las reglas verifican que solo el propietario habilitado pueda crear o cambiar sus servicios, e impiden borrar documentos, cambiar el propietario y agregar campos arbitrarios.

Los documentos anteriores sin `BarberoId` quedan excluidos de nuevas reservas. No se asignan automáticamente a una cuenta, porque podrían estar siendo usados por varios barberos. No aparecen en **Mis servicios**. Para convertir un servicio anterior en propio, se debe asignar explícitamente `BarberoId` al UID correcto y normalizar `Estado` (los antiguos pueden usar `Activo`), conservando el identificador y los demás datos. Esta migración administrativa no se ejecuta desde la aplicación.

## Reservas y duración

Las reservas utilizan la duración real del servicio activo, entre 5 y 240 minutos.
El servicio debe pertenecer al barbero seleccionado. La disponibilidad se calcula
con la agenda publicada y los turnos existentes, respetando pausas y cierres.
Un cambio de duración no altera turnos ya reservados; una confirmación con duración
antigua se rechaza y requiere actualizar. Ver [activación y pruebas](validacion-y-reservas.md).

La baja no cancela los turnos existentes. Los importes de turnos ya realizados se conservan en `PrecioCobrado`; los pendientes mantienen el comportamiento previo de cobrar el precio vigente al completarse.

## Idiomas

La interfaz y sus validaciones tienen recursos en español e inglés. Los nombres ingresados se muestran tal como se guardan. El diccionario de traducciones de nombres de servicios queda pendiente: conviene asociar una clave estable o nombres por idioma al documento, sin usar el nombre editable como identificador.

## Verificación y publicación

Ejecutar los tests de `core:domain`, `features:cuenta`, `features:turnos`, `core:data` y las reglas con `npm test` en `firestore-tests`. Publicar las reglas de `firestore.rules` antes de utilizar el alta o la edición contra Firebase real. No se requiere una migración de Room. Para reservas de duración variable hay que publicar las reglas actualizadas y las agendas; no se requiere Blaze.
