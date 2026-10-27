/** Los catch reciben unknown: ni Firestore ni FCM garantizan un objeto Error. */
export function codigoError(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  const codigo: unknown = error.code;
  return typeof codigo === "string" || typeof codigo === "number" ? String(codigo) : undefined;
}
