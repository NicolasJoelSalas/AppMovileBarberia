# Reservas semanales

La agenda se configura de lunes a sábado para esta semana o la próxima, con varias
franjas por día y pausas entre ellas. Las franjas duran al menos cinco minutos,
 usan horas HH:mm, no se superponen y no atraviesan medianoche.

Los servicios duran entre 5 y 240 minutos. La ocupación publicada resta turnos existentes y
ofrece intervalos consecutivos según la duración del servicio, sin opciones superpuestas. Si una reserva
termina a las 09:41, la siguiente puede comenzar a las 09:41. Cada opción muestra
su fin real. Las cancelaciones liberan todo el intervalo y las modificaciones se
confirman atómicamente: ante un conflicto se mantiene la reserva anterior.

Cambiar la agenda guardada obliga a publicarla nuevamente. La publicación cancela
los turnos futuros que quedan fuera de sus franjas y mantiene los de la otra semana.
Cambiar un servicio no redimensiona automáticamente turnos ya confirmados.

El cálculo está en `OcupacionDia` y `crearDiaAtencion` de `core:domain`.
`FirestoreReservasApi` de `core:data` ejecuta transacciones y las reglas comprueban
los intervalos completos. No se necesitan Cloud Functions ni plan Blaze.
No se guardan reservas nuevas sin conexión; se permite consultar los datos locales
ya sincronizados. No hay una cola que confirme reservas al recuperar conexión.

Ver [pruebas, compatibilidad y despliegue](validacion-y-reservas.md).
