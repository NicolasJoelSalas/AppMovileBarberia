# Turnos previstos del 12 al 17 de octubre de 2026

Pendiente de cargar en Firestore: la consola requiere iniciar sesión.
Zona horaria: America/Argentina/Buenos_Aires (UTC-3).

Cada turno dura 40 minutos, de 07:00 a 07:40.
BarberoId: dzQCTMgz8mUxZnL4kGAZYYKAxvz1.
ServicioId: hWV3pl2L8OOzQd6nPytf.

| Día | Fecha | ClienteId |
| --- | --- | --- |
| Lunes | 2026-10-12 | hWV3pl2L8OOzQd6nPytf |
| Martes | 2026-10-13 | NoAAtUgeiMafeKd3UqMJs22fZxs2 |
| Miércoles | 2026-10-14 | j20kU3cdBCgZw8FoCTy4qWy0nHl2 |
| Jueves | 2026-10-15 | 6EilCr2keJYxlbqrpqtGmAJHaJf2 |
| Viernes | 2026-10-16 | RBprmgKsshOtepqrz2rsUSZO3fv2 |
| Sábado | 2026-10-17 | sdp70Zp9p4TCajipidTBPyurUui2 |

Campos de cada documento en turnos:

- ClienteId, BarberoId y ServicioId: string.
- Inicio y Fin: timestamp de la fecha correspondiente (07:00 y 07:40 UTC-3).
- Estado: string confirmado.

Antes de guardar: verificar que existan los clientes y el servicio indicados.
El primer ID de cliente coincide con el ID del servicio; confirmar que también
exista en clientes, sin reemplazarlo por un cliente elegido al azar.
Revisar reservas existentes para evitar superposición para el mismo barbero.
Usar el turno incompleto dlPdm0hxw80JQxXLUc5t para el lunes y crear cinco
documentos adicionales para los otros días, sin borrar otras reservas.
