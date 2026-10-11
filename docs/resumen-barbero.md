# Resumen privado del barbero

En Mi resumen, disponible en la barra inferior solo para barberos, cada barbero habilitado ve exclusivamente los documentos cuyo BarberoId coincide con el UID de su sesión. Se puede consultar la semana actual, la próxima y semanas anteriores.

La pantalla y el ViewModel están en features:turnos/resumen. El dominio define los modelos, el contrato y los casos de uso para calcular el resumen y completar turnos. core:data consulta Firestore y ejecuta la transacción. app conecta dependencias y navegación. No se agrega otro módulo ni colección.

En servicios/{ServicioId}, Precio debe ser un número en pesos argentinos, no texto, mayor o igual a cero y con hasta dos decimales. Ejemplo: 12500.50.

Un turno confirmado o pendiente se puede marcar realizado después de Fin. La transacción verifica el barbero habilitado, su identidad, el estado, el horario y el precio confirmado. En turnos/{id} guarda Estado: "realizado", PrecioCobrado: el Precio del servicio y RealizadoEn: timestamp del servidor. No libera el horario ni modifica ClienteId, BarberoId o ServicioId.

Las ganancias corresponden a la semana de Inicio (lunes a domingo, Buenos Aires) y suman PrecioCobrado exclusivamente de turnos realizados. Un precio cambiado posteriormente no altera el historial. Los realizados antiguos sin PrecioCobrado se muestran con un aviso y quedan fuera del total: no se inventa un precio histórico. Cancelados y turnos con estados desconocidos no se cuentan.

Publicar el contenido actualizado de firestore.rules en Firebase Console → Firestore → Reglas es necesario para permitir completar turnos. Las modificaciones locales no publican reglas ni alteran documentos remotos automáticamente.
