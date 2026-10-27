const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, query, where, updateDoc,
  writeBatch, runTransaction, Timestamp, GeoPoint, serverTimestamp, deleteDoc, deleteField, orderBy, limit } = require('firebase/firestore');

let env, start, end, horarioId;
const zone = 'America/Argentina/Buenos_Aires';
const agenda = Object.fromEntries(['Lunes','Martes','Miercoles','Jueves','Viernes','Sabado']
  .map(d => [d, {Estado: true, Franjas: [{Inicio:'09:00', Fin:'13:00'}]}]));
const db = uid => env.authenticatedContext(uid, {email: `${uid}@example.com`}).firestore();
const slot = () => ({BarberoId:'barbero',Inicio:start,Fin:end,Estado:'libre',Agenda:agenda,AgendaId:'semanal',ZonaHoraria:zone});
const booking = (uid, horario = horarioId) => ({ClienteId:uid,BarberoId:'barbero',ServicioId:'corte',
  HorarioId:horario,Inicio:start,Fin:end,Estado:'confirmado',CreadoEn:serverTimestamp()});

before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-barberia',
    firestore: {rules:fs.readFileSync('../firestore.rules','utf8')}});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  start = Timestamp.fromMillis(Math.floor((Date.now() + 86400000) / 1000) * 1000);
  end = Timestamp.fromMillis(start.toMillis()+2400000);
  horarioId = `barbero_${start.toMillis()}`;
  await env.withSecurityRulesDisabled(async ctx => {
    const store=ctx.firestore();
    await Promise.all([
      setDoc(doc(store,'barberos/barbero'),{Nombre:'Barbero',Estado:true,ZonaHoraria:zone}),
      setDoc(doc(store,'barberos/otro'),{Nombre:'Otro',Estado:true,ZonaHoraria:zone}),
      setDoc(doc(store,'barberos/barbero/agenda/semanal'),agenda),
      setDoc(doc(store,'servicios/corte'),{BarberoId:'barbero',NombreCorte:'Corte',Duracion:40,Activo:true,Precio:12500}),
      ...['ana','juan'].map(uid => setDoc(doc(store,`clientes/${uid}`),{Nombre:'',Correo:`${uid}@example.com`,CreadoEn:start}))
    ]);
  });
});
async function publish() {
  await assertSucceeds(setDoc(doc(db('barbero'),`horarios/${horarioId}`),slot()));
}

test('FCM: solo el propietario administra sus dispositivos', async () => {
  const path = 'barberos/barbero/dispositivos/celular';
  const datos = () => ({token:'token-prueba', actualizadoEn:serverTimestamp()});
  await assertSucceeds(setDoc(doc(db('barbero'), path), datos()));
  await assertSucceeds(getDoc(doc(db('barbero'), path)));
  for (const uid of ['otro', 'ana']) {
    await assertFails(getDoc(doc(db(uid), path)));
    await assertFails(setDoc(doc(db(uid), path), datos()));
    await assertFails(deleteDoc(doc(db(uid), path)));
  }
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), path)));
  await assertFails(setDoc(doc(db('ana'), 'barberos/ana/dispositivos/celular'), datos()));
  await assertSucceeds(deleteDoc(doc(db('barbero'), path)));
});

test('FCM: valida tokens y permite desvincular un barbero deshabilitado', async () => {
  const ref = doc(db('barbero'), 'barberos/barbero/dispositivos/celular');
  await assertFails(setDoc(ref, {token:'', actualizadoEn:serverTimestamp()}));
  await assertFails(setDoc(ref, {token:'x'.repeat(4097), actualizadoEn:serverTimestamp()}));
  await assertFails(setDoc(ref, {token:'token', actualizadoEn:start}));
  await assertFails(setDoc(ref, {token:'token', actualizadoEn:serverTimestamp(), uid:'otro'}));
  await assertSucceeds(setDoc(ref, {token:'token', actualizadoEn:serverTimestamp()}));
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), 'barberos/barbero'), {Estado:false}));
  await assertFails(setDoc(ref, {token:'nuevo', actualizadoEn:serverTimestamp()}));
  await assertSucceeds(deleteDoc(ref));
});

test('ubicacion: el propietario guarda y el cliente consulta el punto de su barberia', async () => {
  const ref = doc(db('barbero'), 'barberos/barbero');
  await assertSucceeds(updateDoc(ref, {Ubicacion:new GeoPoint(-34.8, -58.3), UbicacionActualizadaEn:serverTimestamp()}));
  const data = (await assertSucceeds(getDoc(doc(db('ana'), 'barberos/barbero')))).data();
  assert.equal(data.Ubicacion.latitude, -34.8);
  assert.equal(data.Ubicacion.longitude, -58.3);
  assert.equal(data.Nombre, 'Barbero');
  await assertSucceeds(updateDoc(ref, {Ubicacion:new GeoPoint(-34.81, -58.31), UbicacionActualizadaEn:serverTimestamp()}));
});

test('ubicacion: rechaza escrituras ajenas, clientes, anonimos y barberos deshabilitados', async () => {
  const cambio = () => ({Ubicacion:new GeoPoint(-34.8, -58.3), UbicacionActualizadaEn:serverTimestamp()});
  for (const uid of ['otro', 'ana']) {
    await assertFails(updateDoc(doc(db(uid), 'barberos/barbero'), cambio()));
  }
  await assertFails(updateDoc(doc(env.unauthenticatedContext().firestore(), 'barberos/barbero'), cambio()));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'barberos/barbero'), {Estado:false}));
  await assertFails(updateDoc(doc(db('barbero'), 'barberos/barbero'), cambio()));
});

test('ubicacion: valida tipo, fecha del servidor y campos permitidos', async () => {
  const ref = doc(db('barbero'), 'barberos/barbero');
  await assertFails(updateDoc(ref, {Ubicacion:{latitud:-34.8,longitud:-58.3}, UbicacionActualizadaEn:serverTimestamp()}));
  await assertFails(updateDoc(ref, {Ubicacion:new GeoPoint(-34.8,-58.3)}));
  await assertFails(updateDoc(ref, {Ubicacion:new GeoPoint(-34.8,-58.3), UbicacionActualizadaEn:start}));
  await assertFails(updateDoc(ref, {Ubicacion:new GeoPoint(-34.8,-58.3), UbicacionActualizadaEn:serverTimestamp(), Estado:false}));
});

test('mi cuenta: permite editar un nombre existente y conserva el resto del perfil', async () => {
  const ref = doc(db('ana'), 'clientes/ana');
  await assertSucceeds(updateDoc(ref, {Nombre:'Ana'}));
  await assertSucceeds(updateDoc(ref, {Nombre:'Ana Pérez'}));
  const perfil = (await getDoc(ref)).data();
  assert.equal(perfil.Nombre, 'Ana Pérez');
  assert.equal(perfil.Correo, 'ana@example.com');
});

test('mi cuenta: rechaza cambios ajenos, de correo, de rol y nombres invalidos', async () => {
  const ref = doc(db('ana'), 'clientes/ana');
  await assertFails(updateDoc(doc(db('juan'), 'clientes/ana'), {Nombre:'Otro'}));
  await assertFails(updateDoc(ref, {Correo:'otro@example.com'}));
  await assertFails(updateDoc(ref, {Nombre:'Ana', Estado:true}));
  await assertFails(updateDoc(ref, {Nombre:''}));
  await assertFails(updateDoc(ref, {Nombre:'a'.repeat(121)}));
  await assertFails(updateDoc(doc(env.unauthenticatedContext().firestore(), 'clientes/ana'), {Nombre:'Ana'}));
});

test('mi cuenta: barbero edita solamente su perfil y no puede cambiar su habilitacion', async () => {
  await assertSucceeds(updateDoc(doc(db('barbero'), 'barberos/barbero'), {Nombre:'Nuevo nombre'}));
  await assertFails(updateDoc(doc(db('otro'), 'barberos/barbero'), {Nombre:'Otro'}));
  await assertFails(updateDoc(doc(db('barbero'), 'barberos/barbero'), {Estado:false}));
});

test('turnos: solo el barbero dueño completa desde la hora de inicio', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), 'turnos/iniciado'), {...booking('ana'),
      Inicio:Timestamp.fromMillis(Date.now() - 60000), Fin:Timestamp.fromMillis(Date.now() + 2340000)});
    await setDoc(doc(ctx.firestore(), 'turnos/futuro'), booking('ana'));
    await setDoc(doc(ctx.firestore(), 'turnos/pasado'), {...booking('ana'),
      Inicio:Timestamp.fromMillis(Date.now() - 7200000), Fin:Timestamp.fromMillis(Date.now() - 4800000)});
    await setDoc(doc(ctx.firestore(), 'turnos/cancelado'), {...booking('ana'),
      Inicio:Timestamp.fromMillis(Date.now() - 60000), Estado:'cancelado'});
  });
  const completar = () => ({Estado:'realizado', PrecioCobrado:12500, RealizadoEn:serverTimestamp()});
  for (const uid of ['ana', 'otro']) {
    await assertFails(updateDoc(doc(db(uid), 'turnos/iniciado'), completar()));
  }
  const store = db('barbero');
  await assertFails(updateDoc(doc(store, 'turnos/futuro'), completar()));
  await assertFails(updateDoc(doc(store, 'turnos/cancelado'), completar()));
  await assertFails(updateDoc(doc(store, 'turnos/iniciado'), {...completar(), ClienteId:'juan'}));
  await assertFails(updateDoc(doc(store, 'turnos/iniciado'), {...completar(), RealizadoEn:start}));
  await assertFails(updateDoc(doc(store, 'turnos/iniciado'), {...completar(), PrecioCobrado:1}));
  await assertSucceeds(updateDoc(doc(store, 'turnos/iniciado'), completar()));
  assert.equal((await getDoc(doc(store, 'turnos/iniciado'))).data().PrecioCobrado, 12500);
  assert.equal((await getDoc(doc(store, 'turnos/iniciado'))).data().Estado, 'realizado');
  await assertFails(updateDoc(doc(store, 'turnos/iniciado'), {Estado:'confirmado'}));
  await assertSucceeds(updateDoc(doc(store, 'turnos/pasado'), completar()));
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(doc(ctx.firestore(), 'barberos/barbero'), {Estado:false});
    await setDoc(doc(ctx.firestore(), 'turnos/deshabilitado'), {...booking('ana'),
      Inicio:Timestamp.fromMillis(Date.now() - 60000)});
  });
  await assertFails(updateDoc(doc(store, 'turnos/deshabilitado'), completar()));
});

const publicacion = () => ({BarberoId:'barbero',BarberoNombre:'Barbero',
  ImagenUrl:'https://res.cloudinary.com/barberia/image/upload/v1/trabajo.jpg',
  ImagenPublicId:'trabajo',Descripcion:'Corte degradado',CreadoEn:serverTimestamp()});

test('publicaciones: solo el barbero habilitado publica con identidad propia', async () => {
  await assertSucceeds(setDoc(doc(db('barbero'),'publicaciones/trabajo'),publicacion()));
  await assertFails(setDoc(doc(db('ana'),'publicaciones/cliente'),publicacion()));
  await assertFails(setDoc(doc(db('otro'),'publicaciones/ajena'),publicacion()));
  await assertFails(setDoc(doc(db('barbero'),'publicaciones/nombre-falso'),{...publicacion(),BarberoNombre:'Otro'}));
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:false});
  });
  await assertFails(setDoc(doc(db('barbero'),'publicaciones/deshabilitado'),publicacion()));
});

test('publicaciones: clientes leen y solo el dueño elimina o edita la descripcion', async () => {
  await assertSucceeds(setDoc(doc(db('barbero'),'publicaciones/trabajo'),publicacion()));
  await assertSucceeds(getDocs(query(collection(db('ana'),'publicaciones'),orderBy('CreadoEn','desc'),limit(50))));
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(),'publicaciones')));
  await assertFails(deleteDoc(doc(db('ana'),'publicaciones/trabajo')));
  await assertFails(deleteDoc(doc(db('otro'),'publicaciones/trabajo')));
  await assertSucceeds(updateDoc(doc(db('barbero'),'publicaciones/trabajo'),{Descripcion:'Cambio'}));
  await assertSucceeds(updateDoc(doc(db('barbero'),'publicaciones/trabajo'),{Descripcion:''}));
  for (const uid of ['ana', 'otro']) {
    await assertFails(updateDoc(doc(db(uid),'publicaciones/trabajo'),{Descripcion:'Ajena'}));
  }
  await assertFails(updateDoc(doc(env.unauthenticatedContext().firestore(),'publicaciones/trabajo'),{Descripcion:'Sin sesion'}));
  for (const cambio of [{Descripcion:'a'.repeat(301)}, {Descripcion:123},
    {Descripcion:'Cambio',BarberoId:'otro'}, {ImagenUrl:'https://otra'}, {CreadoEn:serverTimestamp()}]) {
    await assertFails(updateDoc(doc(db('barbero'),'publicaciones/trabajo'),cambio));
  }
  await assertSucceeds(deleteDoc(doc(db('barbero'),'publicaciones/trabajo')));
});

test('publicaciones: barbero deshabilitado no edita su descripcion', async () => {
  await assertSucceeds(setDoc(doc(db('barbero'),'publicaciones/trabajo'),publicacion()));
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:false});
  });
  await assertFails(updateDoc(doc(db('barbero'),'publicaciones/trabajo'),{Descripcion:'Cambio'}));
});

test('publicaciones: rechaza formatos campos fechas y enlaces incorrectos', async () => {
  const invalidas = [
    {...publicacion(),ImagenUrl:'http://res.cloudinary.com/barberia/image/upload/foto.jpg'},
    {...publicacion(),ImagenUrl:'https://otra-pagina.test/foto.jpg'},
    {...publicacion(),ImagenUrl:'https://res.cloudinary.com/barberia/image/upload/foto.exe'},
    {...publicacion(),Descripcion:'a'.repeat(301)}, {...publicacion(),CreadoEn:start},
    {...publicacion(),ImagenPublicId:''}, {...publicacion(),Correo:'privado@example.com'}];
  for (let i=0;i<invalidas.length;i++)
    await assertFails(setDoc(doc(db('barbero'),`publicaciones/invalida-${i}`),invalidas[i]));
});
test('servicios: barbero crea, modifica, desactiva y reactiva sus servicios', async () => {
  const ref = doc(db('barbero'), 'servicios/propio');
  const servicio = {BarberoId:'barbero', NombreCorte:'Corte personalizado', Precio:12500.50,
    Duracion:60, Estado:true, CreadoEn:serverTimestamp()};
  await assertSucceeds(setDoc(ref, servicio));
  await assertSucceeds(updateDoc(ref, {NombreCorte:'Corte y barba', Precio:0.29, Duracion:90}));
  await assertSucceeds(updateDoc(ref, {Estado:false}));
  assert.equal((await getDoc(ref)).data().Estado, false);
  await assertSucceeds(updateDoc(ref, {Estado:true}));
  assert.equal((await getDoc(ref)).data().NombreCorte, 'Corte y barba');
  await assertFails(deleteDoc(ref));
});

test('servicios: rechaza escrituras ajenas, clientes y barberos deshabilitados', async () => {
  const servicio = {BarberoId:'barbero',NombreCorte:'Corte',Precio:100,Duracion:40,Estado:true,CreadoEn:serverTimestamp()};
  await assertSucceeds(setDoc(doc(db('barbero'), 'servicios/propio'), servicio));
  for (const uid of ['ana','otro']) {
    await assertFails(setDoc(doc(db(uid), 'servicios/nuevo'), servicio));
    await assertFails(updateDoc(doc(db(uid), 'servicios/propio'), {Precio:1}));
  }
  await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), 'servicios/anonimo'), servicio));
  await assertFails(updateDoc(doc(db('barbero'), 'servicios/propio'), {BarberoId:'otro'}));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'barberos/barbero'), {Estado:false}));
  await assertFails(updateDoc(doc(db('barbero'), 'servicios/propio'), {Precio:1}));
});

test('servicios: valida nombre precio duracion y campos permitidos', async () => {
  const ref = doc(db('barbero'), 'servicios/propio');
  await assertSucceeds(setDoc(ref, {BarberoId:'barbero',NombreCorte:'Corte',Precio:100,Duracion:40,Estado:true,CreadoEn:serverTimestamp()}));
  for (const cambio of [{NombreCorte:''},{NombreCorte:'   '},{NombreCorte:'x'.repeat(121)},
    {Precio:-1},{Precio:1000000001},{Precio:1.234},{Precio:'100'},
    {Duracion:4},{Duracion:241},{Duracion:40.5},{Duracion:'40'},{Estado:'true'},{Extra:1},{CreadoEn:start}]) {
    await assertFails(updateDoc(ref, cambio));
  }
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(), 'servicios/compartido'), {NombreCorte:'Compartido',Precio:100,Duracion:40,Estado:true}));
  await assertFails(updateDoc(doc(db('barbero'), 'servicios/compartido'), {Precio:1}));
});

test('servicios: baja conserva turnos y permite completar con precio decimal', async () => {
  await assertSucceeds(setDoc(doc(db('barbero'), 'servicios/propio'), {
    BarberoId:'barbero',NombreCorte:'Corte',Precio:0.29,Duracion:40,Estado:true,CreadoEn:serverTimestamp()
  }));
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(), 'turnos/anterior'), {
    ...booking('ana'), ServicioId:'propio', Inicio:Timestamp.fromMillis(Date.now()-60000),
    Fin:Timestamp.fromMillis(Date.now()+2340000)
  }));
  await assertSucceeds(updateDoc(doc(db('barbero'), 'servicios/propio'), {Estado:false}));
  const ref = doc(db('barbero'), 'turnos/anterior');
  await assertSucceeds(updateDoc(ref, {Estado:'realizado',PrecioCobrado:0.29,RealizadoEn:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db('barbero'), 'servicios/propio'), {NombreCorte:'Nuevo nombre',Precio:200}));
  assert.equal((await getDoc(ref)).data().PrecioCobrado, 0.29);
});

test('servicios: reserva solo los servicios activos del barbero elegido', async () => {
  await publish();
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'servicios/corte'), {BarberoId:deleteField()}));
  await assertFails(reserve('ana','sin-propietario'));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'servicios/corte'), {BarberoId:'otro'}));
  await assertFails(reserve('ana','ajeno'));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'servicios/corte'), {BarberoId:'barbero',Estado:false}));
  await assertFails(reserve('ana','inactivo'));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'servicios/corte'), {Estado:true,Duracion:60}));
  await assertFails(reserve('ana','otra-duracion'));
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(), 'servicios/corte'), {Duracion:40}));
  await assertSucceeds(reserve('ana','propio'));
});

async function reserve(uid, id, extra = {}) {
  const store=db(uid), horario=doc(store,`horarios/${horarioId}`), turno=doc(store,`turnos/${id}`);
  return runTransaction(store, async tx => {
    const existing=await tx.get(turno);
    if (existing.exists()) return id;
    const available=await tx.get(horario);
    const client=await tx.get(doc(store,`clientes/${uid}`));
    if (available.data().Estado !== 'libre') throw new Error('ocupado');
    tx.update(horario,{Estado:'ocupado',TurnoId:id});
    tx.set(turno,{...booking(uid), ...extra});
    if (!client.data().Nombre) tx.update(doc(store,`clientes/${uid}`),{Nombre:uid});
    return id;
  });
}

test('referencia: acepta nulo y permite adjuntar, reemplazar y quitar una imagen propia', async () => {
  await publish();
  await assertSucceeds(reserve('ana', 'referencia', {ReferenciaCorteUrl:null}));
  const ref = doc(db('ana'), 'turnos/referencia');
  const url = 'https://res.cloudinary.com/barberia/image/upload/referencia.jpg';
  await assertSucceeds(updateDoc(ref, {ReferenciaCorteUrl:url}));
  assert.equal((await getDoc(doc(db('barbero'), 'turnos/referencia'))).data().ReferenciaCorteUrl, url);
  await assertFails(updateDoc(doc(db('juan'), 'turnos/referencia'), {ReferenciaCorteUrl:null}));
  await assertFails(updateDoc(doc(db('barbero'), 'turnos/referencia'), {ReferenciaCorteUrl:null}));
  await assertSucceeds(updateDoc(ref, {ReferenciaCorteUrl:url + '2'}));
  await assertSucceeds(updateDoc(ref, {ReferenciaCorteUrl:null}));
});

test('referencia: rechaza tipos y direcciones invalidas al reservar', async () => {
  await publish();
  for (const value of [123, '', 'content://foto', 'https://example.com/foto.jpg', 'https://res.cloudinary.com/x/image/upload/' + 'a'.repeat(2048)]) {
    await assertFails(reserve('ana', 'invalida', {ReferenciaCorteUrl:value}));
  }
});

test('solo el barbero dueño publica; el cliente no fabrica horarios', async () => {
  await assertFails(setDoc(doc(db('ana'),`horarios/${horarioId}`),slot()));
  await assertFails(setDoc(doc(db('otro'),`horarios/${horarioId}`),slot()));
  await publish();
  await assertSucceeds(getDocs(query(collection(db('ana'),'horarios'),where('BarberoId','==','barbero'))));
});
test('ID canónico y duración impiden duplicados y horarios inválidos', async () => {
  await assertFails(setDoc(doc(db('barbero'),'horarios/inventado'),slot()));
  await assertFails(setDoc(doc(db('barbero'),`horarios/${horarioId}`),{...slot(),Fin:start}));
  await publish();
});
test('reserva atómica y listados limitados al cliente o barbero autenticado', async () => {
  await publish();
  assert.equal(await assertSucceeds(reserve('ana','reserva-ana')),'reserva-ana');
  await assertSucceeds(reserve('ana','reserva-ana'));
  assert.equal((await getDoc(doc(db('ana'),'clientes/ana'))).data().Nombre,'ana');
  await assertSucceeds(getDocs(query(collection(db('barbero'),'turnos'),where('BarberoId','==','barbero'))));
  await assertFails(getDocs(collection(db('ana'),'turnos')));
  const propios = await assertSucceeds(getDocs(query(collection(db('ana'),'turnos'),where('ClienteId','==','ana'))));
  assert.equal(propios.size,1);
  assert.equal(propios.docs[0].id,'reserva-ana');
  const deJuan = await assertSucceeds(getDocs(query(collection(db('juan'),'turnos'),where('ClienteId','==','juan'))));
  assert.equal(deJuan.size,0);
  await assertFails(getDocs(query(collection(db('juan'),'turnos'),where('ClienteId','==','ana'))));
  await assertFails(getDocs(query(collection(db('ana'),'turnos'),where('BarberoId','==','barbero'))));
  await assertFails(getDocs(query(collection(db('otro'),'turnos'),where('BarberoId','==','barbero'))));
  await assertFails(getDocs(query(collection(env.unauthenticatedContext().firestore(),'turnos'),where('ClienteId','==','ana'))));
  await assertFails(getDoc(doc(db('juan'),'turnos/reserva-ana')));
  await assertFails(getDoc(doc(db('otro'),'turnos/reserva-ana')));
});
test('dos clientes simultáneos producen exactamente una reserva', async () => {
  await publish();
  const results=await Promise.allSettled([reserve('ana','a'),reserve('juan','b')]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  const turns=await getDocs(query(collection(db('barbero'),'turnos'),where('BarberoId','==','barbero')));
  assert.equal(turns.size,1);
});
test('no se permite turno sin ocupar horario ni ocupar sin turno', async () => {
  await publish();
  await assertFails(setDoc(doc(db('ana'),'turnos/suelto'),booking('ana')));
  await assertFails(updateDoc(doc(db('ana'),`horarios/${horarioId}`),{Estado:'ocupado',TurnoId:'inexistente'}));
});
test('no se puede reservar a nombre de otra cuenta o alterar las horas', async () => {
  await publish();
  for (const forged of [{...booking('juan')},{...booking('ana'),Fin:Timestamp.fromMillis(end.toMillis()+1)}]) {
    const store=db('ana'), batch=writeBatch(store);
    batch.update(doc(store,`horarios/${horarioId}`),{Estado:'ocupado',TurnoId:'falso'});
    batch.set(doc(store,'turnos/falso'),forged);
    await assertFails(batch.commit());
  }
});
test('cambio de agenda, servicio deshabilitado o barbero deshabilitado impiden reservar', async () => {
  await publish();
  await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'servicios/corte'),{Activo:false}));
  await assertFails(reserve('ana','inactivo'));
  await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'servicios/corte'),{Activo:true}));
  await assertSucceeds(updateDoc(doc(db('barbero'),'barberos/barbero/agenda/semanal'),{'Lunes.Estado':false}));
  await assertFails(reserve('ana','vieja-agenda'));
  await env.withSecurityRulesDisabled(async ctx=>setDoc(doc(ctx.firestore(),'barberos/barbero/agenda/semanal'),agenda));
  await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:false}));
  await assertFails(reserve('ana','barbero-inactivo'));
});
test('no se puede liberar ni sobrescribir una reserva confirmada', async () => {
  await publish(); await reserve('ana','a');
  await assertFails(setDoc(doc(db('barbero'),`horarios/${horarioId}`),slot()));
  await assertFails(updateDoc(doc(db('ana'),'turnos/a'),{Estado:'cancelado'}));
  await assert.rejects(reserve('juan','b'), /ocupado/);
});
test('republicar conserva identidad, permite retirar libres y rechaza horarios pasados', async () => {
  await publish();
  await publish();
  const store=db('barbero');
  await assertSucceeds(updateDoc(doc(store,`horarios/${horarioId}`),{Estado:'bloqueado'}));
  await assert.rejects(reserve('ana','retirado'), /ocupado/);
  const client = db('ana'), forged = writeBatch(client);
  forged.update(doc(client,`horarios/${horarioId}`),{Estado:'ocupado',TurnoId:'retirado'});
  forged.set(doc(client,'turnos/retirado'),booking('ana'));
  await assertFails(forged.commit());
  await publish();
  await assertSucceeds(reserve('ana','nuevo'));
  const past=Timestamp.fromMillis(Date.now()-3600000);
  await assertFails(setDoc(doc(store,`horarios/barbero_${past.toMillis()}`),
    {...slot(),Inicio:past,Fin:Timestamp.fromMillis(past.toMillis()+2400000)}));
});
test('sin sesión y barbero no pueden reservar como cliente', async () => {
  await publish();
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(),'horarios')));
  const store=db('barbero'), batch=writeBatch(store);
  batch.update(doc(store,`horarios/${horarioId}`),{Estado:'ocupado',TurnoId:'b'});
  batch.set(doc(store,'turnos/b'),booking('barbero'));
  await assertFails(batch.commit());
});
test('solo el dueño habilitado modifica su agenda, sin borrar turnos confirmados', async () => {
  await publish(); await reserve('ana','confirmado');
  const changed={...agenda,Lunes:{Estado:false,Franjas:[]}};
  await assertFails(setDoc(doc(db('ana'),'barberos/barbero/agenda/semanal'),changed));
  await assertFails(setDoc(doc(db('otro'),'barberos/barbero/agenda/semanal'),changed));
  await assertSucceeds(setDoc(doc(db('barbero'),'barberos/barbero/agenda/semanal'),changed));
  assert.equal((await getDoc(doc(db('barbero'),'turnos/confirmado'))).data().Estado,'confirmado');
  await env.withSecurityRulesDisabled(async ctx=>updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:false}));
  await assertFails(setDoc(doc(db('barbero'),'barberos/barbero/agenda/semanal'),agenda));
});
test('agendas por fecha son independientes y una modificación no invalida la otra semana', async () => {
  const store=db('barbero'), currentId='2026-10-12', nextId='2026-10-19';
  await assertSucceeds(setDoc(doc(store,`barberos/barbero/agenda/${currentId}`),agenda));
  const nextAgenda={...agenda,Lunes:{Estado:true,Franjas:[{Inicio:'16:00',Fin:'20:00'}]}};
  await assertSucceeds(setDoc(doc(store,`barberos/barbero/agenda/${nextId}`),nextAgenda));
  await assertFails(setDoc(doc(db('ana'),`barberos/barbero/agenda/${nextId}`),agenda));
  await assertSucceeds(setDoc(doc(store,`horarios/${horarioId}`),{...slot(),AgendaId:nextId,Agenda:nextAgenda}));
  await assertSucceeds(updateDoc(doc(store,`barberos/barbero/agenda/${currentId}`),{'Lunes.Estado':false}));
  await assertSucceeds(reserve('ana','proxima'));
  assert.equal((await getDoc(doc(db('ana'),'turnos/proxima'))).data().Estado,'confirmado');
});
test('horarios antiguos sin referencia semanal requieren republicación', async () => {
  await env.withSecurityRulesDisabled(async ctx=>{
    const old={...slot()}; delete old.AgendaId;
    await setDoc(doc(ctx.firestore(),`horarios/${horarioId}`),old);
  });
  await assertFails(reserve('ana','legacy'));
});

async function cancelBooking(uid = 'ana', released = 'libre') {
  const store = db(uid), batch = writeBatch(store);
  batch.update(doc(store, 'turnos/cambio'), {Estado:'cancelado', CanceladoEn:serverTimestamp(),
    ...(['barbero', 'otro'].includes(uid) ? {CanceladoPor:uid} : {})});
  batch.update(doc(store, `horarios/${horarioId}`), {Estado:released, TurnoId:deleteField()});
  return batch.commit();
}
async function setupChange() { await publish(); await reserve('ana', 'cambio'); }

test('quitar franja: barbero cancela y bloquea el horario de forma atomica', async () => {
  await setupChange();
  await assertFails(cancelBooking('otro', 'bloqueado'));
  await assertFails(updateDoc(doc(db('barbero'), 'turnos/cambio'), {Estado:'cancelado', CanceladoEn:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('barbero'), `horarios/${horarioId}`), {Estado:'bloqueado', TurnoId:deleteField()}));
  await assertFails(cancelBooking('barbero', 'libre'));
  await assertSucceeds(cancelBooking('barbero', 'bloqueado'));
  assert.equal((await getDoc(doc(db('ana'), 'turnos/cambio'))).data().Estado, 'cancelado');
  const horario = (await getDoc(doc(db('barbero'), `horarios/${horarioId}`))).data();
  assert.equal(horario.Estado, 'bloqueado');
  assert.equal(horario.TurnoId, undefined);
});

test('quitar franja: no cancela turnos que ya comenzaron', async () => {
  await setupChange();
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), 'turnos/cambio'), {
    Inicio:Timestamp.fromMillis(Date.now() - 60000)
  }));
  await assertFails(cancelBooking('barbero', 'bloqueado'));
});

test('cancelacion: cliente no puede atribuir la cancelacion al barbero', async () => {
  await setupChange();
  const store = db('ana'), batch = writeBatch(store);
  batch.update(doc(store, 'turnos/cambio'), {Estado:'cancelado', CanceladoEn:serverTimestamp(), CanceladoPor:'barbero'});
  batch.update(doc(store, `horarios/${horarioId}`), {Estado:'libre', TurnoId:deleteField()});
  await assertFails(batch.commit());
});
test('cancelar: requiere dueño y liberar el horario en la misma operación', async () => {
  await setupChange();
  await assertFails(cancelBooking('juan'));
  await assertFails(updateDoc(doc(db('ana'), 'turnos/cambio'), {Estado:'cancelado',CanceladoEn:serverTimestamp()}));
  await assertSucceeds(cancelBooking());
  assert.equal((await getDoc(doc(db('ana'), `horarios/${horarioId}`))).data().Estado, 'libre');
  await assertFails(updateDoc(doc(db('ana'), 'turnos/cambio'), {Estado:'confirmado'}));
});
test('cancelar: agenda cambiada bloquea el horario anterior', async () => {
  await setupChange();
  await updateDoc(doc(db('barbero'), 'barberos/barbero/agenda/semanal'), {Lunes:{Estado:false,Franjas:[]}});
  await assertFails(cancelBooking());
  await assertSucceeds(cancelBooking('ana', 'bloqueado'));
});
async function moveBooking(uid, newId, nextStart, release = true) {
  const store = db(uid), batch = writeBatch(store);
  batch.update(doc(store, 'turnos/cambio'), {HorarioId:newId, Inicio:nextStart,
    Fin:Timestamp.fromMillis(nextStart.toMillis()+2400000)});
  batch.update(doc(store, `horarios/${newId}`), {Estado:'ocupado',TurnoId:'cambio'});
  if (release) batch.update(doc(store, `horarios/${horarioId}`), {Estado:'libre',TurnoId:deleteField()});
  return batch.commit();
}
test('modificar: cambia horario atómicamente y rechaza cambios ajenos o sin liberar', async () => {
  await setupChange();
  const nextStart = Timestamp.fromMillis(start.toMillis()+2400000), newId = `barbero_${nextStart.toMillis()}`;
  await setDoc(doc(db('barbero'), `horarios/${newId}`), {...slot(),Inicio:nextStart,Fin:Timestamp.fromMillis(nextStart.toMillis()+2400000)});
  await assertFails(moveBooking('juan', newId, nextStart));
  await assertFails(moveBooking('ana', newId, nextStart, false));
  await assertSucceeds(moveBooking('ana', newId, nextStart));
  assert.equal((await getDoc(doc(db('ana'), 'turnos/cambio'))).data().HorarioId, newId);
});
test('modificar: un horario ocupado conserva la reserva original', async () => {
  await setupChange();
  const nextStart = Timestamp.fromMillis(start.toMillis()+2400000), newId = `barbero_${nextStart.toMillis()}`;
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), `horarios/${newId}`), {...slot(),Inicio:nextStart,
      Fin:Timestamp.fromMillis(nextStart.toMillis()+2400000),Estado:'ocupado',TurnoId:'otro'});
  });
  await assertFails(moveBooking('ana', newId, nextStart));
  assert.equal((await getDoc(doc(db('ana'), 'turnos/cambio'))).data().HorarioId, horarioId);
});
test('cancelar: rechaza turnos que ya comenzaron', async () => {
  await setupChange();
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(doc(ctx.firestore(), 'turnos/cambio'), {Inicio:Timestamp.fromMillis(Date.now()-60000)});
  });
  await assertFails(cancelBooking());
});

test('modificar: no permite ocupar horarios adicionales con el mismo turno', async () => {
  await setupChange();
  const nextStart = Timestamp.fromMillis(start.toMillis()+2400000), newId = `barbero_${nextStart.toMillis()}`;
  const extraStart = Timestamp.fromMillis(start.toMillis()+4800000), extraId = `barbero_${extraStart.toMillis()}`;
  for (const [id, inicio] of [[newId,nextStart],[extraId,extraStart]]) {
    await setDoc(doc(db('barbero'), `horarios/${id}`), {...slot(),Inicio:inicio,Fin:Timestamp.fromMillis(inicio.toMillis()+2400000)});
  }
  const store = db('ana'), batch = writeBatch(store);
  batch.update(doc(store, 'turnos/cambio'), {HorarioId:newId,Inicio:nextStart,Fin:extraStart});
  batch.update(doc(store, `horarios/${horarioId}`), {Estado:'libre',TurnoId:deleteField()});
  batch.update(doc(store, `horarios/${newId}`), {Estado:'ocupado',TurnoId:'cambio'});
  batch.update(doc(store, `horarios/${extraId}`), {Estado:'ocupado',TurnoId:'cambio'});
  await assertFails(batch.commit());
});
