# Recordatorios de turnos

En **Mis turnos**, los clientes pueden tocar **Recordarme 1 hora antes** y confirmar la fecha y hora. El mismo botón permite cancelar el aviso. Solo se habilita para turnos confirmados o pendientes cuya hora de recordatorio aún no pasó.

El aviso es una notificación local con sonido y vibración, sujeta al volumen, No molestar y los ajustes del canal **Recordatorios de turnos** del teléfono. No es una alarma de Reloj que suena continuamente. La hora del diálogo y la notificación corresponde a la zona horaria del teléfono; el instante se calcula restando una hora al inicio del turno.

Android 13 o posterior solicita permiso de notificaciones. Android 12 o posterior puede requerir habilitar **Alarmas y recordatorios**. Si se deniega un permiso, no se confirma la activación. Los recordatorios se guardan en este teléfono, se recuperan tras reiniciarlo y se eliminan al cerrar sesión. No se transfieren mediante copias de seguridad.

Mientras se observan los turnos del cliente, se actualizan recordatorios de turnos reprogramados y se eliminan los de turnos cancelados, realizados o eliminados, incluso si corresponden a otra semana. Si el turno cambia en el servidor con la app cerrada o sin conexión, el recordatorio conserva la última información recibida hasta volver a sincronizar. No hay sincronización de recordatorios entre teléfonos. Forzar la detención de la app o revocar permisos puede impedir el aviso; al volver a abrir Mis turnos se recuperan los recordatorios futuros cuando los permisos están habilitados.

## Verificación en un teléfono o emulador

1. Iniciar sesión como cliente y reservar un turno para dentro de algo más de una hora. Activar el recordatorio y aceptar los permisos. Verificar la fecha/hora del diálogo.
2. Cerrar la app sin forzar su detención. Comprobar la notificación al cumplirse la hora indicada y que tocarla abra la app.
3. Cancelar un recordatorio y verificar que no aparezca. Volver a activarlo varias veces y comprobar que se entregue una sola notificación.
4. Probar un turno de la próxima semana y uno cuyo recordatorio caiga el día anterior. Verificar el instante programado en `adb shell dumpsys alarm`.
5. Denegar cada permiso por separado: no debe mostrarse como activado. Habilitarlos y reintentar. Probar notificaciones o canal desactivados y No molestar.
6. Reiniciar el teléfono con un recordatorio futuro y verificar que siga programado. Cerrar sesión y comprobar que se elimine.
7. Con Mis turnos abierto, reprogramar, cancelar y eliminar turnos desde los datos de prueba; verificar que se actualicen o cancelen sus recordatorios, incluso en la otra semana.
8. Verificar que el botón no aparezca para barberos, turnos cancelados o realizados, y que esté deshabilitado si ya pasó la hora del recordatorio.

Las pruebas unitarias cubren la fecha futura, cruces de medianoche/año, estados no admitidos, fechas ausentes y recordatorios vencidos. La entrega del aviso, los permisos y el sonido requieren la verificación en Android descrita arriba.
