import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import { createHash } from "node:crypto";
import { cambiosTurno, leerTurno } from "./cambios-turno";
import { codigoError } from "./errores";

initializeApp();
const db = getFirestore();
export const notificarCambioTurno = onDocumentWritten({
  document: "turnos/{turnoId}", region: "us-central1", retry: true,
  timeoutSeconds: 60, maxInstances: 10
}, async event => {
  if (!event.data) return;
  const antes = leerTurno(event.data.before.exists ? event.data.before.data() : null);
  const despues = leerTurno(event.data.after.exists ? event.data.after.data() : null);
  try {
    for (const cambio of cambiosTurno(antes, despues)) {
      const barbero = db.doc(`barberos/${cambio.uid}`);
      if ((await barbero.get()).get("Estado") !== true) continue;
      const dispositivos = await barbero.collection("dispositivos").get();
      for (const dispositivo of dispositivos.docs) {
        const token: unknown = dispositivo.get("token");
        if (typeof token !== "string" || !token) continue;
        const clave = createHash("sha256").update(`${event.id}:${cambio.uid}:${dispositivo.id}:${token}`).digest("hex");
        const registro = db.collection("eventosNotificaciones").doc(clave);
        const enviar = await db.runTransaction(async tx => {
          const estado = await tx.get(registro);
          if (estado.get("enviado") === true) return false;
          const leaseHasta: unknown = estado.get("leaseHasta");
          if (leaseHasta instanceof Timestamp && leaseHasta.toMillis() > Date.now()) {
            throw new Error("Envío en curso; reintentar");
          }
          tx.set(registro, { enviado: false, leaseHasta: Timestamp.fromMillis(Date.now() + 120000) });
          return true;
        });
        if (!enviar) continue;
        try {
          await getMessaging().send({ token,
            data: { turnoId: event.params.turnoId, tipo: cambio.tipo, texto: cambio.texto,
              barberoId: cambio.uid, eventoId: event.id },
            android: { priority: "high", ttl: 24 * 60 * 60 * 1000 }
          });
          await registro.set({ enviado: true, enviadoEn: FieldValue.serverTimestamp() });
        } catch (error) {
          if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(codigoError(error) ?? "")) {
            // No borrar un token que el celular haya renovado mientras se enviaba.
            await db.runTransaction(async tx => {
              const actual = await tx.get(dispositivo.ref);
              if (actual.get("token") === token) tx.delete(dispositivo.ref);
              tx.set(registro, { enviado: true, tokenInvalido: true });
            });
          } else {
            // Un fallo de limpieza no debe ocultar el error original de envío/Firestore.
            try {
              await registro.delete();
            } catch (limpiezaError: unknown) {
              logger.error("No se pudo liberar el registro de envío", {
                eventoId: event.id, codigo: codigoError(limpiezaError)
              });
            }
            throw error;
          }
        }
      }
    }
  } catch (error: unknown) {
    // Se relanza para que Firebase reintente; no registrar tokens ni datos personales.
    logger.error("Falló la notificación del cambio de turno", {
      eventoId: event.id, turnoId: event.params.turnoId, codigo: codigoError(error)
    });
    throw error;
  }
});
