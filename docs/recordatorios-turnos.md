# Recordatorios automáticos de turnos

Los turnos confirmados o pendientes del cliente incluyen por defecto dos avisos locales: **24 horas antes** y **1 hora antes**. Se programan cuando la app recibe los turnos durante la sesión del cliente, también después de reservar desde Inicio. No hace falta abrir Mis turnos ni activar cada turno. Si una hora de aviso ya pasó, se omite; nunca se envía inmediatamente un aviso vencido.

Los timestamps del turno representan instantes universales (milisegundos UTC). Los avisos restan 24 horas y 1 hora a ese instante. El diálogo, las tarjetas y las notificaciones muestran la zona horaria local configurada en el teléfono. La notificación dice «Tu turno es a las HH:mm». Cambiar la zona horaria cambia la presentación, no el instante UTC de los avisos. Se utiliza el reloj del sistema en milisegundos UTC; no se consulta un servidor de hora externo. Conviene mantener la fecha, hora y zona horaria automáticas de Android. Un día antes significa 24 horas transcurridas, incluso al cruzar cambios de horario de verano.

Android 13 o posterior requiere permiso de notificaciones; Android 12 o posterior puede requerir Alarmas y recordatorios. La app explica y solicita los permisos una vez por cuenta en este teléfono al recibir el primer turno elegible. Si se deniegan, no muestra los avisos como activos. Se puede reintentar desde **Activar avisos del turno** o habilitar los permisos desde Ajustes. Al recuperarlos, se programan los avisos futuros predeterminados.

En Mis turnos, **Desactivar avisos** cancela ambos avisos de ese turno. Esa elección se guarda para que la sincronización o un reinicio no los reactive. **Activar avisos del turno** permite habilitarlos otra vez. Cada aviso tiene su propia identidad; enviar el de un día no cancela el de una hora. Los avisos enviados se registran para evitar duplicarlos al sincronizar o reiniciar. La actualización migra los recordatorios de la versión anterior.

El aviso es una notificación con sonido y vibración, sujeta al volumen, No molestar y los ajustes del canal Recordatorios de turnos. No es una alarma de Reloj que suena continuamente. Los recordatorios se guardan en este teléfono, se recuperan tras reiniciarlo y se eliminan al cerrar sesión. No se transfieren mediante copias de seguridad.

Durante la sesión del cliente, los avisos se actualizan si el turno cambia y se eliminan si se cancela, se realiza o se elimina. Si el turno cambia en el servidor con la app cerrada o sin conexión, se conserva la última información recibida hasta sincronizar. No hay sincronización entre teléfonos. Forzar la detención de la app o revocar permisos puede impedir el aviso.

## Verificación en Android

El cálculo de instantes está en `core:domain/usecase/RecordatorioTurno.kt`, con
anticipaciones definidas en `model/ConfiguracionRecordatorios.kt`. El contrato
`RecordatoriosRepository` se implementa con preferencias en `core:data`.
`app/reminders` integra AlarmManager, el receptor Android y la recuperación de
los avisos; las pantallas consumen las acciones calculadas por dominio.

1. Como cliente, reservar desde Inicio un turno a más de 24 horas. Aceptar los permisos. Comprobar dos alarmas distintas en `adb shell dumpsys alarm` sin entrar en Mis turnos.
2. Verificar un turno con menos de 24 horas pero más de una hora: debe tener solo el aviso de una hora. Un turno a menos de una hora no debe generar avisos vencidos.
3. Probar ambos avisos en un dispositivo de prueba ajustando el horario del turno y el reloj; confirmar que recibir el primero no cancela el segundo ni duplica avisos después de sincronizar.
4. Cambiar la zona horaria del teléfono a otra región. El instante programado debe conservarse y los textos deben mostrar la nueva hora local del teléfono. Probar medianoche y cambio de año.
5. Desactivar avisos y sincronizar o reiniciar: deben permanecer desactivados. Reactivarlos y comprobar que no se dupliquen.
6. Denegar permisos, concederlos desde Ajustes y volver a la app. Los avisos futuros deben recuperarse. Probar el canal desactivado y No molestar.
7. Reiniciar con avisos futuros, cerrar sesión y comprobar respectivamente su recuperación y eliminación.
8. Con la sesión abierta, reprogramar, cancelar y eliminar turnos de prueba de ambas semanas; comprobar la actualización de avisos. El botón no debe aparecer para barberos o turnos cancelados/realizados.

Las pruebas unitarias cubren ambos instantes UTC, avisos vencidos, estados no admitidos, fechas ausentes y cruces de medianoche/año, otras zonas horarias y cambios de horario de verano. La entrega, los permisos y el sonido requieren las pruebas en Android indicadas arriba.
