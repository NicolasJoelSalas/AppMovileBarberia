import { isDeepStrictEqual } from "node:util";

export interface TurnoDocumento {
  ClienteId?: unknown;
  BarberoId?: string;
  ServicioId?: unknown;
  HorarioId?: unknown;
  Inicio?: unknown;
  Fin?: unknown;
  Estado?: unknown;
  ReferenciaCorteUrl?: unknown;
  [campo: string]: unknown;
}

export interface CambioTurno {
  uid: string;
  tipo: "alta" | "baja" | "modificacion";
  texto: string;
}

/** Los datos de Firestore también se validan al ejecutar: los tipos no validan datos remotos. */
export function leerTurno(datos: unknown): TurnoDocumento | null {
  if (datos === undefined || datos === null) return null;
  if (typeof datos !== "object" || Array.isArray(datos)) throw new Error("Documento de turno inválido");
  const documento = datos as Record<string, unknown>;
  const uid = documento.BarberoId;
  if (uid !== undefined && (typeof uid !== "string" || !uid.trim() || uid.includes("/"))) {
    throw new Error("BarberoId inválido en el turno");
  }
  return documento as TurnoDocumento;
}

const campos = ["ClienteId", "BarberoId", "ServicioId", "HorarioId", "Inicio", "Fin", "Estado", "ReferenciaCorteUrl"];
function estado(turno: TurnoDocumento | null): string {
  const valor = turno?.Estado;
  return valor === false ? "cancelado" : valor === true ? "confirmado" : String(valor ?? "").toLowerCase();
}

export function cambiosTurno(antes: TurnoDocumento | null, despues: TurnoDocumento | null): CambioTurno[] {
  if (!antes && !despues) return [];
  if (antes && despues && campos.every(campo => isDeepStrictEqual(antes[campo], despues[campo]))) return [];
  const anterior = antes?.BarberoId;
  const actual = despues?.BarberoId;
  if (despues && (estado(despues) === "realizado" ||
      (actual && estado(despues) === "cancelado" && despues.CanceladoPor === actual))) return [];
  if (!antes) return actual ? [{ uid: actual, tipo: "alta", texto: "Tenés una nueva reserva en tu agenda." }] : [];
  if (!despues) return anterior ? [{ uid: anterior, tipo: "baja", texto: "Se eliminó un turno de tu agenda." }] : [];
  if (anterior !== actual) return [
    ...(anterior ? [{ uid: anterior, tipo: "baja" as const, texto: "Un turno fue reasignado a otro barbero." }] : []),
    ...(actual ? [{ uid: actual, tipo: "alta" as const, texto: "Se asignó un turno a tu agenda." }] : [])
  ];
  if (!actual) return [];
  const cancelado = estado(despues) === "cancelado" && estado(antes) !== "cancelado";
  return [{ uid: actual, tipo: cancelado ? "baja" : "modificacion",
    texto: cancelado ? "Se canceló un turno de tu agenda." : "Se modificó un turno de tu agenda." }];
}
