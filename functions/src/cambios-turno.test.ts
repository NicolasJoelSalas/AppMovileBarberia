import { test } from "node:test";
import assert from "node:assert/strict";
import { cambiosTurno, leerTurno } from "./cambios-turno";
const turno = { BarberoId: "barbero-a", Estado: "confirmado", HorarioId: "h1" };
test("alta, cancelación y borrado físico", () => {
  assert.equal(cambiosTurno(null, turno)[0].tipo, "alta");
  assert.equal(cambiosTurno(turno, { ...turno, Estado: "cancelado" })[0].tipo, "baja");
  assert.equal(cambiosTurno(turno, null)[0].uid, "barbero-a");
});

test("valida documentos remotos antes de construir rutas de Firestore", () => {
  assert.equal(leerTurno(undefined), null);
  assert.deepEqual(leerTurno(turno), turno);
  for (const datos of [42, [], { BarberoId: 42 }, { BarberoId: " " }, { BarberoId: "otro/documento" }]) {
    assert.throws(() => leerTurno(datos));
  }
});
test("modificación de horario y referencia", () => {
  assert.equal(cambiosTurno(turno, { ...turno, HorarioId: "h2" })[0].tipo, "modificacion");
  assert.equal(cambiosTurno(turno, { ...turno, ReferenciaCorteUrl: "imagen" }).length, 1);
});
test("reasignación avisa al barbero anterior y al nuevo", () => {
  assert.deepEqual(cambiosTurno(turno, { ...turno, BarberoId: "barbero-b" }).map(({ uid, tipo }) => ({ uid, tipo })),
    [{ uid: "barbero-a", tipo: "baja" }, { uid: "barbero-b", tipo: "alta" }]);
});
test("ignora cambios internos y documentos sin destinatario", () => {
  assert.deepEqual(cambiosTurno(turno, { ...turno, PrecioCobrado: 500 }), []);
  assert.deepEqual(cambiosTurno(null, { Estado: "confirmado" }), []);
  assert.deepEqual(cambiosTurno(turno, { ...turno }), []);
});
