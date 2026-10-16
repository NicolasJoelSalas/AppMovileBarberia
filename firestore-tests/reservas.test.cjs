const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, query, where, updateDoc,
  writeBatch, runTransaction, Timestamp, serverTimestamp, deleteDoc, orderBy, limit } = require('firebase/firestore');

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
      setDoc(doc(store,'servicios/corte'),{NombreCorte:'Corte',Duracion:40,Activo:true,Precio:12500}),
      ...['ana','juan'].map(uid => setDoc(doc(store,`clientes/${uid}`),{Nombre:'',Correo:`${uid}@example.com`,CreadoEn:start}))
    ]);
  });
});
async function publish() {
  await assertSucceeds(setDoc(doc(db('barbero'),`horarios/${horarioId}`),slot()));
}

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

test('turnos: solo el barbero due�o completa desde la hora de inicio', async () => {
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

test('publicaciones: clientes leen y solo el dueño elimina, sin modificaciones', async () => {
  await assertSucceeds(setDoc(doc(db('barbero'),'publicaciones/trabajo'),publicacion()));
  await assertSucceeds(getDocs(query(collection(db('ana'),'publicaciones'),orderBy('CreadoEn','desc'),limit(50))));
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(),'publicaciones')));
  await assertFails(deleteDoc(doc(db('ana'),'publicaciones/trabajo')));
  await assertFails(deleteDoc(doc(db('otro'),'publicaciones/trabajo')));
  await assertFails(updateDoc(doc(db('barbero'),'publicaciones/trabajo'),{Descripcion:'Cambio'}));
  await assertSucceeds(deleteDoc(doc(db('barbero'),'publicaciones/trabajo')));
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
async function reserve(uid, id) {
  const store=db(uid), horario=doc(store,`horarios/${horarioId}`), turno=doc(store,`turnos/${id}`);
  return runTransaction(store, async tx => {
    const existing=await tx.get(turno);
    if (existing.exists()) return id;
    const available=await tx.get(horario);
    const client=await tx.get(doc(store,`clientes/${uid}`));
    if (available.data().Estado !== 'libre') throw new Error('ocupado');
    tx.update(horario,{Estado:'ocupado',TurnoId:id});
    tx.set(turno,booking(uid));
    if (!client.data().Nombre) tx.update(doc(store,`clientes/${uid}`),{Nombre:uid});
    return id;
  });
}

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
