/* ===== PORTAL DO USUÁRIO (rota raiz "/") =====
   Só funções de quem acompanha os próprios dispositivos: ver estado, previsão, energia,
   histórico e alertas; pedir manutenção; ajustar a própria conta e notificações.
   Funções de administração (cadastro/configuração de dispositivos, roteirização e agenda de
   manutenção, parâmetros do sistema) ficam no painel /admin. */

/* ---------- preferências de notificação (salvas neste navegador; nome e e-mail vêm do login) ---------- */
const PREF_KEY='eh-user-prefs';
const DEFAULT_PREFS={limit:300,critical:true,high:true,mail:false};
function loadPrefs(){try{return{...DEFAULT_PREFS,...JSON.parse(localStorage.getItem(PREF_KEY)||'{}')}}catch(e){return{...DEFAULT_PREFS}}}
function savePrefs(p){try{localStorage.setItem(PREF_KEY,JSON.stringify(p));return true}catch(e){return false}}
let prefs=loadPrefs();

/* ---------- alertas do usuário: respeitam as notificações e o limite de consumo ---------- */
let alertsBase=null;
async function userAlerts(){
 if(!alertsBase)alertsBase=await api.getAlerts();
 const o=await api.getOverview();
 const list=alertsBase.filter(a=>a.level==='critical'?prefs.critical:a.level==='high'?prefs.high:true);
 const nowW=o.consumption.nowW;
 if(prefs.high&&nowW>prefs.limit)list.unshift({id:'limit',level:'high',title:'Consumo acima do seu limite',message:`Consumo atual de ${power(nowW)}, acima do limite de ${num(prefs.limit)} W.`,createdAt:new Date().toISOString()});
 return withRead(list);
}
function bindAlerts(redraw){
 V.onclick=async e=>{const b=e.target.closest('[data-read],[data-readall]');if(!b)return;
  if(b.dataset.readall)(await userAlerts()).forEach(a=>readIds.add(String(a.id)));
  else readIds.add(b.dataset.read);
  redraw()};
}
/* dispositivo precisa de atenção: offline, bateria baixa ou esgotando em até 7 dias */
const needsAttention=(d,f)=>d.status==='Offline'||d.battery<20||(f.daysLeft!=null&&f.daysLeft<=7);

/* ---------- telas ---------- */
const pages={
async inicio(){
 alertsBase=null; /* busca alertas novos a cada carga (inclusive na atualização automática) */
 const [o,alerts,devices,fc,h]=await Promise.all([api.getOverview(group),userAlerts(),api.getDevices(),api.getForecasts(),api.getHistory('hoje',{group})]);
 const fById=byId(fc.items);
 /* o aviso de atenção vale para todos os dispositivos; os cards seguem o grupo escolhido */
 const needs=devices.filter(d=>needsAttention(d,fById[d.id]));
 const banner=needs.length
  ?`<div class="banner" style="border-color:var(--wn)">${needs.length} ${needs.length===1?'dispositivo precisa':'dispositivos precisam'} de atenção. <a href="#dispositivos">Ver meus dispositivos</a></div>`
  :'<div class="banner" style="border-color:var(--ok)">Tudo certo com os seus dispositivos.</div>';
 V.innerHTML=`${banner}<div class="grid">
  <div class="card"><h3>Energia gerada hoje</h3><div class="big">${bigKwh(o.generation.todayKwh)}</div>${changeText(o.generation.changePct,true)}</div>
  <div class="card"><h3>Consumo agora</h3><div class="big">${bigPower(o.consumption.nowW)}</div><div class="mu">Hoje: ${kwh(o.consumption.todayKwh)}</div></div>
  <div class="card"><h3>Bateria armazenada</h3><div class="big">${o.storage.pct==null?"—":num(o.storage.pct)}<small>%</small></div>${bar(o.storage.pct||0,'var(--sto)')}<div class="mu">${storageText(o.storage)}</div></div>
  <div class="card"><h3>Dispositivos online</h3><div class="big">${num(o.devices.online)} <small>de ${num(o.devices.total)}</small></div><div class="mu">${num(o.devices.offline)} offline</div></div></div>
  <div class="card" style="margin-bottom:16px"><h3>Gerada × consumida hoje (${historyEnergy(h).unit})</h3><div class="chart"><canvas id="c1"></canvas></div></div>
  <div class="card"><h3>Alertas recentes</h3><div id="recent"></div></div>`;
 V.firstElementChild.after(groupFilter());
 chart('c1','line',historyLabels(h),[lineDataset('--gen','Gerada',historyEnergy(h).gen),lineDataset('--con','Consumida',historyEnergy(h).con)]);
 const drawAlerts=async()=>{const l=await userAlerts();$('#recent').innerHTML=l.length?l.slice(0,3).map(alertHTML).join(''):'<div class="mu">Nenhum alerta no momento.</div>'};
 bindAlerts(drawAlerts);await drawAlerts()},

async dispositivos(){
 const [devices,fc,reqs]=await Promise.all([api.getDevices(),api.getForecasts(),api.getMaintenanceRequests()]);
 const fById=byId(fc.items);
 /* filtro sobrevive à atualização automática (com o pedido de manutenção aberto ela fica pausada) */
 const st=pageState({filter:'all'});
 let openForm=null,notice='';
 const filterFns={all:()=>true,att:d=>needsAttention(d,fById[d.id]),on:d=>d.status==='Online',off:d=>d.status==='Offline'};
 const reqOf=id=>[...reqs].reverse().find(r=>r.deviceId===id&&r.status!=='Concluída');
 const card=d=>{
  const f=fById[d.id],r=reqOf(d.id),att=needsAttention(d,f);
  const reason=d.status==='Offline'?'Dispositivo offline':'Bateria baixa';
  let action='';
  if(openForm===d.id)action=`<div class="mform">
    <label class="mu" for="mreason">Motivo</label>
    <select id="mreason">${['Bateria baixa','Dispositivo offline','Outro problema'].map(x=>`<option${x===reason?' selected':''}>${x}</option>`).join('')}</select>
    <label class="mu" for="mnote">Observação (opcional)</label>
    <textarea id="mnote" rows="2" maxlength="200"></textarea>
    <div class="row" style="margin:0"><button class="btn on" data-act="send" data-id="${esc(d.id)}">Enviar solicitação</button><button class="btn" data-act="cancel" data-id="${esc(d.id)}">Cancelar</button></div></div>`;
  else if(r){const visit=r.scheduledFor?` · Visita em ${esc(formatDay(r.scheduledFor))}`:'';action=`<div class="mform"><div class="mu">Manutenção solicitada · ${esc(timeAgo(r.createdAt))} ${requestTag(r.status)}${visit}</div></div>`}
  else if(att)action=`<div class="mform"><button class="btn" data-act="open" data-id="${esc(d.id)}">Solicitar manutenção</button></div>`;
  return `<div class="card">
   ${deviceCardHead(d)}
   <dl class="kv">
    ${kvRow('Autonomia prevista',daysTag(f))}
    ${kvRow('Consumo',perDay(d.consumption))}
    ${kvRow('Energia coletada',harvestText(d,'Sem coleta de energia'))}
    ${kvRow('Temperatura',d.temperature==null?'—':`${num(d.temperature)} °C`)}
   </dl>${action}</div>`};
 const draw=()=>{
  const list=devices.filter(filterFns[st.filter]);
  V.innerHTML=notice?`<div class="banner" role="status">${esc(notice)}</div>`:'';
  V.append(filters([['all','Todos'],['att','Precisam de atenção'],['on','Online'],['off','Offline']],st.filter,k=>{st.filter=k;notice='';draw()}));
  V.insertAdjacentHTML('beforeend',list.length?`<div class="grid">${list.map(card).join('')}</div>`:'<div class="card mu">Nenhum dispositivo neste filtro.</div>')};
 V.onclick=async e=>{
  const b=e.target.closest('[data-act]');if(!b)return;
  const id=b.dataset.id,act=b.dataset.act;
  if(act==='open'){openForm=id;notice='';draw()}
  else if(act==='cancel'){openForm=null;draw()}
  else if(act==='send'){
   b.textContent='Enviando…';
   try{
    await api.requestMaintenance(id,{reason:$('#mreason').value,note:$('#mnote').value.trim()});
    reqs.splice(0,reqs.length,...await api.getMaintenanceRequests());
    const name=devices.find(d=>d.id===id).name;
    openForm=null;notice=`Solicitação de manutenção enviada para ${name}. Você acompanha o status no card do dispositivo.`
   }catch(error){notice=`Não foi possível enviar a solicitação: ${error.message}`}
   draw()}};
 draw()},

previsao:()=>renderForecast(),

async energia(){
 const [s,b,l]=await Promise.all([api.getSources(group),api.getBatteries(),api.getLoads()]);
 V.innerHTML=`<h2 class="sec">De onde vem a energia</h2><div id="grp"></div>
  <div class="grid">${s.map(x=>sourceCard(x)).join('')}</div>
  ${shareCard(s)}
  <h2 class="sec">Armazenamento</h2>
  <div class="grid g2">${b.map(batteryCard).join('')}</div>
  <h2 class="sec">Maiores consumidores agora</h2>
  <div class="card">${topConsumers(l)}</div>`;
 $('#grp').replaceWith(groupFilter())},

historico:()=>renderHistory(),

async alertas(){
 alertsBase=null;
 const draw=async()=>{
  const list=await userAlerts(),unread=list.filter(a=>!a.read).length;
  V.innerHTML=`<div class="row">${unread?'<button class="btn" data-readall="1">Marcar todos como lidos</button>':''}<span class="mu">${unread} ${unread===1?'não lido':'não lidos'}</span></div>`
   +(list.length?list.map(alertHTML).join(''):'<div class="card mu">Nenhum alerta no momento.</div>')};
 bindAlerts(draw);await draw()},

async conta(){
 const me=await api.getMe();
 const p={...prefs};
 const sw=(k,label,desc)=>`<div class="set"><div><b>${label}</b><div class="mu">${desc}</div></div><button class="sw${p[k]?' on':''}" data-pref="${k}" role="switch" aria-checked="${!!p[k]}" aria-label="${label}"></button></div>`;
 V.innerHTML=`${accountCards(me)}
  <div class="card" style="max-width:640px">
  <h3>Notificações</h3>
  <div class="mu" style="font-size:13px">Preferências salvas neste navegador.</div>
  ${sw('critical','Bateria crítica','Avisar quando um dispositivo estiver com bateria muito baixa')}
  ${sw('high','Consumo elevado','Avisar quando o consumo passar do seu limite')}
  ${sw('mail','Receber alertas por e-mail',`Enviar um resumo para ${esc(me.email)}`)}
  <div class="set"><div><b>Limite de consumo</b><div class="mu">Alerta acima de <span id="limv">${p.limit}</span> W</div></div><input id="limr" type="range" min="100" max="600" step="10" value="${p.limit}" aria-label="Limite de consumo em watts"></div>
  <div class="row" style="margin:12px 0 0"><button class="btn on" id="csave">Salvar notificações</button><span class="mu" id="cok" role="status"></span></div></div>`;
 bindAccount();
 V.onclick=e=>{const b=e.target.closest('[data-pref]');if(!b)return;const k=b.dataset.pref;p[k]=!p[k];b.classList.toggle('on',p[k]);b.setAttribute('aria-checked',String(p[k]));$('#cok').textContent=''};
 $('#limr').oninput=e=>{p.limit=+e.target.value;$('#limv').textContent=p.limit;$('#cok').textContent=''};
 $('#csave').onclick=()=>{prefs=p;
  $('#cok').textContent=savePrefs(p)?'Notificações salvas.':'Aplicadas nesta sessão (não foi possível salvar no navegador).'}}
};

const NAV=[['inicio','Início'],['dispositivos','Meus dispositivos'],['previsao','Previsão de bateria'],['energia','Minha energia'],['historico','Histórico'],['alertas','Alertas'],['conta','Minha conta']];
startApp(NAV,pages,{staticPages:['conta']});
