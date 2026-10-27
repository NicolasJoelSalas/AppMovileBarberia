import { test } from "node:test";
import assert from "node:assert/strict";
import { codigoError } from "./errores";

test("extrae códigos FCM y Firestore sin asumir el tipo del error", () => {
  assert.equal(codigoError({ code: "messaging/registration-token-not-registered" }), "messaging/registration-token-not-registered");
  assert.equal(codigoError({ code: 14 }), "14");
  for (const error of [null, undefined, "sin conexión", new Error("falló"), { code: {} }]) {
    assert.equal(codigoError(error), undefined);
  }
});
