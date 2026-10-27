# Decisiones de arquitectura

## Módulos por responsabilidad

Las pantallas se agrupan por capacidad del usuario: autenticación, publicaciones,
turnos, cuenta y notificaciones. Los módulos compartidos separan dominio, datos,
autenticación, red, base local e interfaz. `app` integra las implementaciones.
No se conserva código de ejemplos de tareas o libros que no forma parte de la app.

## Contratos de dominio

El dominio es Kotlin puro. Las interfaces de repositorio permiten utilizar
Firebase/Room en producción y simulaciones en las pruebas. Los errores viajan
como `AppResult`/`DomainError`; los ViewModels no dependen de Firebase directamente.

## Inyección y presentación

Hilt conecta constructores, implementaciones y configuración Android. Los
ViewModels exponen estado observable y las pantallas lo recogen con su ciclo de
vida. Navigation 3 conserva back stacks por destino principal.

## Datos offline

Room conserva turnos separados por cuenta y rol, y publicaciones para clientes.
Las imágenes descargadas se almacenan en archivos privados. Una falla de red no
se interpreta como una lista vacía confirmada y no elimina el contenido guardado.
Consultar sin conexión no implica confirmar nuevas reservas sin servidor.

## Duración y concurrencia

La creación de servicios admite 5 a 240 minutos y las reservas usan esa duración.
El dominio calcula horarios y franjas y las transacciones de Firestore actualizan
la ocupación del día y el turno en forma atómica. Las reglas validan los minutos. Esto evita que dos reservas con inicios distintos se superpongan.
La publicación de una agenda cancela turnos futuros que ya no entran en sus
franjas. Cambiar la duración de un servicio no redimensiona reservas existentes.

Se mantiene la compatibilidad del mecanismo antiguo hasta que cada barbero
publique su agenda nueva. Después, las reglas rechazan escrituras de apps viejas.
No se necesitan Cloud Functions ni plan Blaze. Ver la publicación de reglas en
[activación](validacion-y-reservas.md) antes de instalar la versión nueva.

## Pruebas

`core:testing` contiene herramientas compartidas en `src/main`, consumidas por
`testImplementation`. Las verificaciones se mantienen junto a su módulo. Se usan
SQLite real y HTTP local donde aportan evidencia; Firebase se valida con emulador.
Las pruebas Android verifican interfaz, idioma, Hilt y recuperación del almacenamiento.
No se considera que una prueba aritmética del template valide funcionalidad de la app.
