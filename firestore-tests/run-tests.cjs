const {spawn} = require('node:child_process');

// Un assertFails también acepta rechazos por agotar las reglas. Eso no prueba
// la validación de negocio: la suite debe fallar si aparece ese diagnóstico.
const child = spawn(process.execPath, ['--test', '--test-concurrency=1',
  'disponibilidad.test.cjs', 'reservas.test.cjs'], {stdio:['inherit','pipe','pipe']});
let diagnosticos = '';
function reenviar(stream, chunk) {
  stream.write(chunk);
  diagnosticos = (diagnosticos + chunk.toString()).slice(-16000);
  if (/maximum of 1000 expressions|maximum.*(?:document access|access calls)|ternary operator predicate|UNKNOWN: Application error/i.test(diagnosticos)) {
    excedeLimites = true;
  }
}
let excedeLimites = false;
child.stdout.on('data', chunk => reenviar(process.stdout, chunk));
child.stderr.on('data', chunk => reenviar(process.stderr, chunk));
child.on('error', error => { console.error(error); process.exitCode = 1; });
child.on('close', code => {
  if (excedeLimites) console.error('Las reglas alcanzaron un límite de evaluación o un error interno; no se aceptan esos rechazos como pruebas válidas.');
  process.exitCode = excedeLimites ? 1 : (code ?? 1);
});
