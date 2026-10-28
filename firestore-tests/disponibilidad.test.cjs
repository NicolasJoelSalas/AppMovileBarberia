const {test,before,after,beforeEach} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails} = require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,updateDoc,runTransaction,writeBatch,Timestamp,serverTimestamp} = require('firebase/firestore');
let env, inicioDia, diaId;
const zona = 'UTC';
const agenda = {Lunes:{Estado:true,Franjas:[{Inicio:'09:00',Fin:'13:00'},{Inicio:'16:00',Fin:'20:00'}]}};
const store = uid => env.authenticatedContext(uid,{email:`${uid}@example.com`}).firestore();
const reemplazar = (s,a,b,c) => s.slice(0,a)+c.repeat(b-a)+s.slice(b);
const hora = (h,m=0) => inicioDia+(h*60+m)*60000;
const mascara = () => reemplazar(reemplazar('0'.repeat(1440),540,780,'1'),960,1200,'1');
const refDia = (db,id=diaId) => doc(db,`disponibilidad/${id}`);
const turno = (uid,id,minutos=60,h=9,m=0,barbero='barbero',dia=diaId) => ({
  ClienteId:uid,BarberoId:barbero,ServicioId:barbero==='barbero'?'corte':'otro-corte',
  HorarioId:`${barbero}_${hora(h,m)}`,DiaId:dia,AgendaId:'semanal',ReservaVersion:3,DuracionMinutos:minutos,
  Inicio:Timestamp.fromMillis(hora(h,m)),Fin:Timestamp.fromMillis(hora(h,m)+minutos*60000),
  Estado:'confirmado',CreadoEn:serverTimestamp()
});
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-barberia',firestore:{rules:fs.readFileSync('../firestore.rules','utf8')}})});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
  await env.clearFirestore();
  inicioDia=Math.floor((Date.now()+86400000)/86400000)*86400000;
  diaId=`barbero_${inicioDia}`;
  await env.withSecurityRulesDisabled(async ctx=>{
    const db=ctx.firestore();
    for(const b of ['barbero','otro']){
      await setDoc(doc(db,`barberos/${b}`),{Estado:true,Nombre:b,ZonaHoraria:zona,ReservasVersion:3});
      await setDoc(doc(db,`barberos/${b}/agenda/semanal`),agenda);
      await setDoc(doc(db,`servicios/${b==='barbero'?'corte':'otro-corte'}`),{BarberoId:b,Duracion:60,Estado:true,NombreCorte:'Corte',Precio:100});
      await setDoc(doc(db,`disponibilidad/${b}_${inicioDia}`),{BarberoId:b,Inicio:Timestamp.fromMillis(inicioDia),
        Fin:Timestamp.fromMillis(inicioDia+86400000),Atencion:mascara(),Ocupacion:'0'.repeat(1440),
        Agenda:agenda,AgendaId:'semanal',ZonaHoraria:zona});
    }
    for(const uid of ['ana','juan'])await setDoc(doc(db,`clientes/${uid}`),{Nombre:uid});
  });
});
async function reservar(uid,id,minutos=60,h=9,m=0){
  const db=store(uid),t=turno(uid,id,minutos,h,m);
  return runTransaction(db,async tx=>{
    const d=await tx.get(refDia(db));
    const a=h*60+m,b=a+minutos;
    assert.match(d.data().Ocupacion.slice(a,b),/^0+$/);
    tx.set(doc(db,`turnos/${id}`),t);
    tx.update(refDia(db),{Ocupacion:reemplazar(d.data().Ocupacion,a,b,'1'),UltimoTurnoId:id});
  });
}
async function cancelar(uid,id){
  const db=store(uid);
  return runTransaction(db,async tx=>{
    const ref=doc(db,`turnos/${id}`),t=(await tx.get(ref)).data();
    if(t.Estado==='cancelado')return;
    const dia=t.DiaId||diaId,d=await tx.get(refDia(db,dia));
    const a=(t.Inicio.toMillis()-d.data().Inicio.toMillis())/60000,b=(t.Fin.toMillis()-d.data().Inicio.toMillis())/60000;
    tx.update(ref,{Estado:'cancelado',CanceladoEn:serverTimestamp(),CanceladoPor:uid,DiaId:dia,DiaAnteriorId:dia});
    tx.update(refDia(db,dia),{Ocupacion:reemplazar(d.data().Ocupacion,a,b,'0'),UltimoTurnoId:id});
  });
}
async function editar(uid,id,minutos,h,m=0,barbero='barbero'){
  const db=store(uid),r=doc(db,`turnos/${id}`),destino=`${barbero}_${inicioDia}`;
  return runTransaction(db,async tx=>{
    const antes=(await tx.get(r)).data(),origen=antes.DiaId||diaId;
    const d1=(await tx.get(refDia(db,origen))).data();
    const d2=origen===destino?d1:(await tx.get(refDia(db,destino))).data();
    const a=(antes.Inicio.toMillis()-d1.Inicio.toMillis())/60000,b=(antes.Fin.toMillis()-d1.Inicio.toMillis())/60000;
    const liberado=reemplazar(d1.Ocupacion,a,b,'0');
    const base=origen===destino?liberado:d2.Ocupacion,c=h*60+m;
    assert.match(base.slice(c,c+minutos),/^0+$/);
    tx.set(r,{...turno(uid,id,minutos,h,m,barbero,destino),CreadoEn:antes.CreadoEn,DiaAnteriorId:origen});
    tx.update(refDia(db,destino),{Ocupacion:reemplazar(base,c,c+minutos,'1'),UltimoTurnoId:id});
    if(origen!==destino)tx.update(refDia(db,origen),{Ocupacion:liberado,UltimoTurnoId:id});
  });
}

test('Spark: reserva 60, 75 y 90 minutos y permite terminar exactamente al cierre',async()=>{
  const service=doc(store('barbero'),'servicios/corte');
  for(const [id,minutos,h,m] of [['a',60,9,0],['b',75,10,0],['c',90,11,30]]){
    await assertSucceeds(updateDoc(service,{Duracion:minutos}));
    await assertSucceeds(reservar('ana',id,minutos,h,m));
    assert.equal((await getDoc(doc(store('ana'),`turnos/${id}`))).data().Fin.toMillis(),hora(h,m)+minutos*60000);
  }
});
test('Spark: dos clientes concurrentes con inicios distintos no se superponen',async()=>{
  const resultados=await Promise.allSettled([reservar('ana','a'),reservar('juan','b',60,9,30)]);
  assert.equal(resultados.filter(r=>r.status==='fulfilled').length,1);
});
test('Spark: rechaza una reserva sin ocupar todos sus minutos en la misma transacción',async()=>{
  await assertFails(setDoc(doc(store('ana'),'turnos/falso'),turno('ana','falso')));
  const db=store('ana'),batch=writeBatch(db);
  batch.set(doc(db,'turnos/corto'),turno('ana','corto'));
  batch.update(refDia(db),{Ocupacion:reemplazar('0'.repeat(1440),540,545,'1'),UltimoTurnoId:'corto'});
  await assertFails(batch.commit());
});
test('Spark: un cliente no puede ocupar minutos adicionales ni modificar la atención',async()=>{
  const db=store('ana');
  for(const extra of [false,true]){
    const b=writeBatch(db);b.set(doc(db,'turnos/falso'),turno('ana','falso'));
    b.update(refDia(db),extra?{Atencion:'1'.repeat(1440),Ocupacion:reemplazar('0'.repeat(1440),540,600,'1'),UltimoTurnoId:'falso'}:
      {Ocupacion:reemplazar('0'.repeat(1440),540,601,'1'),UltimoTurnoId:'falso'});
    await assertFails(b.commit());
  }
});
test('Spark: no permite atravesar descanso o cierre ni falsificar la duración',async()=>{
  await assertFails(reservar('ana','descanso',60,12,30));
  await assertFails(reservar('ana','cierre',60,19,30));
  await assertFails(reservar('ana','duracion',75));
});
test('Spark: cancelación libera exactamente todo el intervalo y es idempotente',async()=>{
  await assertSucceeds(reservar('ana','a'));
  await assertFails(cancelar('juan','a'));
  await assertSucceeds(cancelar('ana','a'));await cancelar('ana','a');
  assert.equal((await getDoc(refDia(store('ana')))).data().Ocupacion,'0'.repeat(1440));
  await assertSucceeds(reservar('juan','b'));
});
test('Spark: una cancelación vieja no permite liberar una reserva posterior',async()=>{
  await reservar('ana','a');await cancelar('ana','a');await reservar('juan','b');
  for(const uid of ['ana','barbero'])await assertFails(updateDoc(refDia(store(uid)),{Ocupacion:'0'.repeat(1440),UltimoTurnoId:'a'}));
});
test('Spark: el barbero cancela los propios y uno deshabilitado no puede hacerlo',async()=>{
  await reservar('ana','a');await assertFails(cancelar('otro','a'));
  await env.withSecurityRulesDisabled(ctx=>updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:false}));
  await assertFails(cancelar('barbero','a'));
  await env.withSecurityRulesDisabled(ctx=>updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:true}));
  await assertSucceeds(cancelar('barbero','a'));
});
test('Spark: editar servicio conserva identidad y creación y ajusta el intervalo',async()=>{
  await reservar('ana','a');const creado=(await getDoc(doc(store('ana'),'turnos/a'))).data().CreadoEn;
  await updateDoc(doc(store('barbero'),'servicios/corte'),{Duracion:75});
  await assertSucceeds(editar('ana','a',75,9));
  const t=(await getDoc(doc(store('ana'),'turnos/a'))).data();
  assert.equal(t.CreadoEn.toMillis(),creado.toMillis());assert.equal(t.Fin.toMillis(),hora(10,15));
});
test('Spark: una modificación fallida no libera el turno anterior',async()=>{
  await reservar('ana','a');await reservar('juan','b',60,10);
  const antes=(await getDoc(refDia(store('ana')))).data().Ocupacion;
  await assert.rejects(editar('ana','a',60,9,30));
  assert.equal((await getDoc(refDia(store('ana')))).data().Ocupacion,antes);
  const db=store('ana'),batch=writeBatch(db);
  batch.update(doc(db,'turnos/a'),{Inicio:Timestamp.fromMillis(hora(10)),Fin:Timestamp.fromMillis(hora(11)),HorarioId:`barbero_${hora(10)}`});
  await assertFails(batch.commit());
});
test('Spark: cambio de barbero libera el origen y ocupa el destino atómicamente',async()=>{
  await reservar('ana','a');await assertSucceeds(editar('ana','a',60,9,0,'otro'));
  assert.equal((await getDoc(refDia(store('ana')))).data().Ocupacion,'0'.repeat(1440));
  assert.equal((await getDoc(refDia(store('ana'),`otro_${inicioDia}`))).data().Ocupacion.slice(540,600),'1'.repeat(60));
  await assertSucceeds(reservar('juan','b'));
});
test('Spark: una edición que intenta apropiarse de un turno ajeno es rechazada',async()=>{
  await reservar('ana','a');await assertFails(editar('juan','a',60,10));
});
test('Spark: el barbero puede publicar sin borrar ocupaciones existentes',async()=>{
  await reservar('ana','a');
  const d=(await getDoc(refDia(store('barbero')))).data();
  await assertFails(setDoc(refDia(store('barbero')),{...d,Ocupacion:'0'.repeat(1440)}));
  const {UltimoTurnoId,...publicado}=d;
  await assertSucceeds(setDoc(refDia(store('barbero')),publicado));
});
test('Spark: una agenda cambiada debe republicarse antes de nuevas reservas',async()=>{
  await updateDoc(doc(store('barbero'),'barberos/barbero/agenda/semanal'),{...agenda,Lunes:{Estado:false,Franjas:[]}});
  await assertFails(reservar('ana','a'));
});
test('Spark: servicios inactivos, ajenos y barberos deshabilitados no reservan',async()=>{
  await updateDoc(doc(store('barbero'),'servicios/corte'),{Estado:false});await assertFails(reservar('ana','a'));
  await updateDoc(doc(store('barbero'),'servicios/corte'),{Estado:true});
  const db=store('ana');const b=writeBatch(db),t=turno('ana','ajeno');t.ServicioId='otro-corte';
  b.set(doc(db,'turnos/ajeno'),t);b.update(refDia(db),{Ocupacion:reemplazar('0'.repeat(1440),540,600,'1'),UltimoTurnoId:'ajeno'});
  await assertFails(b.commit());await assertFails(reservar('barbero','propio'));
  await env.withSecurityRulesDisabled(ctx=>updateDoc(doc(ctx.firestore(),'barberos/barbero'),{Estado:false}));
  await assertFails(reservar('ana','deshabilitado'));
});

test('Spark: la migración permite cancelar y modificar turnos anteriores sin DiaId',async()=>{
  await env.withSecurityRulesDisabled(async ctx=>{
    const db=ctx.firestore(),legado=turno('ana','viejo');
    delete legado.DiaId;delete legado.ReservaVersion;delete legado.DuracionMinutos;delete legado.AgendaId;
    await setDoc(doc(db,'turnos/viejo'),legado);
    await updateDoc(refDia(db),{Ocupacion:reemplazar('0'.repeat(1440),540,600,'1')});
  });
  await assertSucceeds(editar('ana','viejo',60,10));
  await assertSucceeds(cancelar('barbero','viejo'));
  assert.equal((await getDoc(refDia(store('ana')))).data().Ocupacion,'0'.repeat(1440));
});
test('Spark: cancelar no puede borrar minutos de otra reserva',async()=>{
  await reservar('ana','a');await reservar('juan','b',60,10);
  const db=store('ana'),batch=writeBatch(db);
  batch.update(doc(db,'turnos/a'),{Estado:'cancelado',CanceladoEn:serverTimestamp(),CanceladoPor:'ana',DiaAnteriorId:diaId});
  batch.update(refDia(db),{Ocupacion:'0'.repeat(1440),UltimoTurnoId:'a'});
  await assertFails(batch.commit());
});
test('Spark: un cambio de barbero no puede omitir la liberación del día anterior',async()=>{
  await reservar('ana','a');
  const db=store('ana'),destino=`otro_${inicioDia}`;
  const antes=(await getDoc(doc(db,'turnos/a'))).data();
  const batch=writeBatch(db);
  batch.set(doc(db,'turnos/a'),{...turno('ana','a',60,9,0,'otro',destino),CreadoEn:antes.CreadoEn,DiaAnteriorId:diaId});
  batch.update(refDia(db,destino),{Ocupacion:reemplazar('0'.repeat(1440),540,600,'1'),UltimoTurnoId:'a'});
  await assertFails(batch.commit());
});

test('Spark: servicios de 5, 41 y 240 minutos reservan y liberan exactamente su intervalo',async()=>{
  for(const minutos of [5,41,240]){
    await assertSucceeds(updateDoc(doc(store('barbero'),'servicios/corte'),{Duracion:minutos}));
    await assertSucceeds(reservar('ana',`limite-${minutos}`,minutos));
    assert.equal((await getDoc(refDia(store('ana')))).data().Ocupacion.slice(540,540+minutos),'1'.repeat(minutos));
    await assertSucceeds(cancelar('ana',`limite-${minutos}`));
    assert.equal((await getDoc(refDia(store('ana')))).data().Ocupacion,'0'.repeat(1440));
  }
});

test('Spark: publicación pendiente bloquea reservas y ediciones pero permite cancelar',async()=>{
  await reservar('ana','existente');
  const db=store('barbero'),{UltimoTurnoId,...dia}=(await getDoc(refDia(db))).data();
  await assertSucceeds(setDoc(refDia(db),{...dia,PublicacionPendiente:true}));
  await assertFails(reservar('juan','nuevo',60,10));
  await assertFails(editar('ana','existente',60,10));
  await assertFails(updateDoc(refDia(store('ana')),{PublicacionPendiente:false}));
  await assertFails(updateDoc(refDia(store('otro')),{PublicacionPendiente:false}));
  await assertSucceeds(cancelar('ana','existente'));
  const {UltimoTurnoId:ultimo,...cancelado}=(await getDoc(refDia(db))).data();
  await assertSucceeds(setDoc(refDia(db),{...cancelado,PublicacionPendiente:false}));
  await assertSucceeds(reservar('juan','recuperado'));
});

test('Spark: habilitar seis días en una transacción conserva ocupaciones y no excede accesos',async()=>{
  await reservar('ana','existente');
  const db=store('barbero'),{UltimoTurnoId,...base}=(await getDoc(refDia(db))).data();
  const pendientes=[];
  for(let i=0;i<6;i++){
    const inicio=inicioDia+i*86400000;
    const ref=doc(db,`disponibilidad/barbero_${inicio}`);
    const dia={...base,Inicio:Timestamp.fromMillis(inicio),Fin:Timestamp.fromMillis(inicio+86400000),
      Ocupacion:i===0?base.Ocupacion:'0'.repeat(1440),PublicacionPendiente:true};
    await assertSucceeds(setDoc(ref,dia));
    pendientes.push([ref,dia]);
  }
  await assertSucceeds(runTransaction(db,async tx=>{
    const actuales=await Promise.all(pendientes.map(([ref])=>tx.get(ref)));
    actuales.forEach(s=>tx.set(s.ref,{...s.data(),PublicacionPendiente:false}));
  }));
  assert.equal((await getDoc(refDia(db))).data().Ocupacion.slice(540,600),'1'.repeat(60));
  for(const [ref] of pendientes)assert.equal((await getDoc(ref)).data().PublicacionPendiente,false);
});
