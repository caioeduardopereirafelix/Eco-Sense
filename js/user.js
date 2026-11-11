/* ===== PORTAL DO USUÁRIO (rota raiz "/") =====
   Só funções de quem acompanha os próprios dispositivos: ver estado, previsão, energia,
   histórico e alertas; pedir manutenção; ajustar a própria conta e notificações.
   Funções de administração (cadastro/configuração de dispositivos, roteirização e agenda de
   manutenção, parâmetros do sistema) ficam no painel /admin. */

/* ---------- preferências do usuário (salvas no navegador) ---------- */
const PREF_KEY='eh-user-prefs';
const DEFAULT_PREFS={name:'Usuário Demo',email:'usuario@exemplo.com',limit:300,critical:true,high:true,mail:false};
function loadPrefs(){try{return{...DEFAULT_PREFS,...JSON.parse(localStorage.getItem(PREF_KEY)||'{}')}}catch(e){return{...DEFAULT_PREFS}}}
function savePrefs(p){try{localStorage.setItem(PREF_KEY,JSON.stringify(p));return true}catch(e){return false}}
let prefs=loadPrefs();
const updateWho=()=>{$('#who').textContent=prefs.name};

/* ---------- alertas do usuário: respeitam as notificações e o limite de consumo ---------- */
const readIds=new Set();
let alertsBase=null;
async function userAlerts(){
 if(!alertsBase)alertsBase=await api.getAlerts();
 const o=await api.getOverview();
 const list=alertsBase.filter(a=>a.lv==='cr'?prefs.critical:a.lv==='hi'?prefs.high:true);
 if(prefs.high&&o.con.now>prefs.limit)list.unshift({id:'limit',lv:'hi',ic:'🟠',t:'Consumo acima do seu limite',m:`Consumo atual de ${num(o.con.now)} W, acima do limite de ${num(prefs.limit)} W.`,w:'agora'});
 return list.map(a=>({...a,read:readIds.has(String(a.id))}));
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
 const [o,alerts,devices,fc,h]=await Promise.all([api.getOverview(),userAlerts(),api.getDevices(),api.getForecasts(),api.getHistory('hoje')]);
 const fById=Object.fromEntries(fc.items.map(f=>[f.id,f]));
 const needs=devices.filter(d=>needsAttention(d,fById[d.id])),online=devices.filter(d=>d.status==='Online').length;
 const banner=needs.length
  ?`<div class="banner" style="border-color:var(--wn)">${needs.length} ${needs.length===1?'dispositivo precisa':'dispositivos precisam'} de atenção. <a href="#dispositivos">Ver meus dispositivos</a></div>`
  :'<div class="banner" style="border-color:var(--ok)">Tudo certo com os seus dispositivos.</div>';
 V.innerHTML=`${banner}<div class="grid">
  <div class="card"><h3>Energia gerada hoje</h3><div class="big">${num(o.gen.v)} <small>kWh</small></div><div class="up">▲ ${num(o.gen.d)}% vs. ontem</div></div>
  <div class="card"><h3>Consumo agora</h3><div class="big">${num(o.con.now)} <small>W</small></div><div class="mu">Hoje: ${num(o.con.period)} kWh</div></div>
  <div class="card"><h3>Bateria armazenada</h3><div class="big">${o.sto.pct}<small>%</small></div>${bar(o.sto.pct,'var(--sto)')}<div class="mu">${esc(o.sto.cap)}</div></div>
  <div class="card"><h3>Dispositivos online</h3><div class="big">${online} <small>de ${devices.length}</small></div><div class="mu">${devices.length-online} offline</div></div></div>
  <div class="card" style="margin-bottom:16px"><h3>Gerada × consumida hoje (kWh/h)</h3><div class="chart"><canvas id="c1"></canvas></div></div>
  <div class="card"><h3>Alertas recentes</h3><div id="recent"></div></div>`;
 chart('c1','line',h.L,[lineDataset('--gen','Gerada',h.gen),lineDataset('--con','Consumida',h.con)]);
 const drawAlerts=async()=>{const l=await userAlerts();$('#recent').innerHTML=l.length?l.slice(0,3).map(alertHTML).join(''):'<div class="mu">Nenhum alerta no momento.</div>'};
 bindAlerts(drawAlerts);drawAlerts()},

async dispositivos(){
 const [devices,fc,reqs]=await Promise.all([api.getDevices(),api.getForecasts(),api.getMaintenanceRequests()]);
 const fById=Object.fromEntries(fc.items.map(f=>[f.id,f]));
 let filter='all',openForm=null,notice='';
 const filterFns={all:()=>true,att:d=>needsAttention(d,fById[d.id]),on:d=>d.status==='Online',off:d=>d.status==='Offline'};
 const reqOf=id=>[...reqs].reverse().find(r=>r.deviceId===id&&r.status!=='Concluída');
 const card=d=>{
  const f=fById[d.id],r=reqOf(d.id),att=needsAttention(d,f);
  const harvest=d.harvesting?`${num(d.harvesting.collected)} Wh <span class="mu">(${esc(d.harvesting.type)})</span>`:'<span class="mu">Sem coleta de energia</span>';
  const reason=d.status==='Offline'?'Dispositivo offline':'Bateria baixa';
  let action='';
  if(openForm===d.id)action=`<div class="mform">
    <label class="mu" for="mreason">Motivo</label>
    <select id="mreason">${['Bateria baixa','Dispositivo offline','Outro problema'].map(x=>`<option${x===reason?' selected':''}>${x}</option>`).join('')}</select>
    <label class="mu" for="mnote">Observação (opcional)</label>
    <textarea id="mnote" rows="2" maxlength="200"></textarea>
    <div class="row" style="margin:0"><button class="btn on" data-act="send" data-id="${esc(d.id)}">Enviar solicitação</button><button class="btn" data-act="cancel" data-id="${esc(d.id)}">Cancelar</button></div></div>`;
  else if(r){const statusClass={'Recebida':'s-Recebida','Agendada':'s-Agendada','Em rota':'s-Em-rota','Concluída':'s-Concluída'}[r.status]||'';const visit=r.scheduledFor?` · Visita em ${esc(r.scheduledFor.split('-').reverse().join('/'))}`:'';action=`<div class="mform"><div class="mu">Manutenção solicitada · ${esc(timeAgo(r.createdAt))} <span class="tag ${statusClass}">${esc(r.status)}</span>${visit}</div></div>`}
  else if(att)action=`<div class="mform"><button class="btn" data-act="open" data-id="${esc(d.id)}">Solicitar manutenção</button></div>`;
  return `<div class="card">
   <h3>${esc(d.location)}</h3>
   <div class="big" style="font-size:20px">${esc(d.name)}</div>
   <div style="margin-top:6px">${tag(d.status)}</div>
   ${bar(d.battery)}
   <div class="mu">Bateria ${d.battery}% · Última comunicação ${esc(d.lastSeen)}</div>
   <dl class="kv">
    ${kvRow('Autonomia prevista',daysTag(f))}
    ${kvRow('Consumo',`${num(d.consumption)} Wh`)}
    ${kvRow('Energia coletada',harvest)}
    ${kvRow('Temperatura',d.temperature==null?'—':`${num(d.temperature)} °C`)}
   </dl>${action}</div>`};
 const draw=()=>{
  const list=devices.filter(filterFns[filter]);
  V.innerHTML=notice?`<div class="banner" role="status">${esc(notice)}</div>`:'';
  V.append(filters([['all','Todos'],['att','Precisam de atenção'],['on','Online'],['off','Offline']],filter,k=>{filter=k;notice='';draw()}));
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

async previsao(){
 const [devices,fc]=await Promise.all([api.getDevices(),api.getForecasts()]);
 const fById=Object.fromEntries(fc.items.map(f=>[f.id,f]));
 const rank=r=>r.f.daysLeft!=null?r.f.daysLeft:r.f.sustainable?1000:2000;
 const rows=devices.map(d=>({d,f:fById[d.id]})).sort((a,b)=>rank(a)-rank(b));
 const online=rows.filter(r=>r.f.daysLeft!=null||r.f.sustainable);
 const atRisk=rows.filter(r=>r.f.daysLeft!=null&&r.f.daysLeft<=7);
 const first=rows.find(r=>r.f.daysLeft!=null);
 let sel=(first||online[0]||rows[0]).d.id,range='24h',drawToken=0;
 const tr=({d,f})=>`<tr><td>${esc(d.name)}</td><td>${daysTag(f)}</td><td>${f.confidence==null?'—':f.confidence+'%'}</td><td>${d.battery}%</td>
  <td>${f.daysLeft!=null||f.sustainable?`<button class="btn" data-pick="${esc(d.id)}" aria-label="Ver gráficos de ${esc(d.name)}">Ver<span class="pvx"> gráficos</span></button>`:''}</td></tr>`;
 V.innerHTML=`<div class="grid">
  <div class="card"><h3>Em risco (até 7 dias)</h3><div class="big">${atRisk.length}</div><div class="mu">de ${online.length} dispositivos com leitura</div></div>
  <div class="card"><h3>Menor autonomia</h3><div class="big">${first?first.f.daysLeft:'—'} <small>${first?(first.f.daysLeft===1?'dia':'dias'):''}</small></div><div class="mu">${first?esc(first.d.name):'Nenhum dispositivo em esgotamento'}</div></div>
  <div class="card"><h3>Sustentáveis</h3><div class="big">${rows.filter(r=>r.f.sustainable).length}</div><div class="mu">coletam mais do que consomem</div></div></div>
  <div class="card" style="margin-bottom:16px"><h3>Quanto tempo cada bateria deve durar</h3><div class="tw"><table class="pvu">
   <tr><th>Dispositivo</th><th>Autonomia</th><th><span class="pvx">Confiança</span><span class="pvm">Conf.</span></th><th>Bateria</th><th></th></tr>${rows.map(tr).join('')}</table></div>
   <div class="mu" style="margin-top:10px">Estimativas atualizadas ${esc(fc.updatedAt)}.</div></div>
  <div class="card" id="pcharts" style="margin-bottom:16px"><div class="row">
   <select id="pdev" aria-label="Dispositivo">${online.map(r=>`<option value="${esc(r.d.id)}"${r.d.id===sel?' selected':''}>${esc(r.d.name)}</option>`).join('')}</select>
   <span id="prange"></span></div>
   <div class="mu" id="pinfo" style="margin-bottom:12px"></div>
   <div class="grid g2" style="margin:0"><div><h3 class="mu" style="margin-bottom:8px">Consumo previsto × real (Wh)</h3><div class="chart"><canvas id="c1"></canvas></div></div>
   <div><h3 class="mu" style="margin-bottom:8px">Nível de bateria projetado (%)</h3><div class="chart"><canvas id="c2"></canvas></div></div></div>
   <div class="mu" style="margin-top:10px">A linha vertical marca o momento atual. Antes dela, dados medidos; depois, previsão.</div></div>`;
 const drawRange=()=>{const r=$('#prange');r.innerHTML='';const f=filters([['24h','Próximas 24 h'],['7d','Próximos 7 dias']],range,k=>{range=k;drawRange();drawCharts()});f.style.margin='0';r.append(f)};
 const drawCharts=async()=>{
  const token=++drawToken;
  const s=await api.getForecastSeries(sel,range);
  if(token!==drawToken)return;
  charts.forEach(c=>c.destroy());charts=[];
  const r=rows.find(x=>x.d.id===sel);
  $('#pinfo').innerHTML=`Autonomia: ${daysTag(r.f)}${r.f.confidence==null?'':` · confiança ${r.f.confidence}%`}`;
  const line=(c,label,data,dash)=>({borderColor:css(c),label,data,fill:false,borderDash:dash||[]});
  chart('c1','line',s.L,[line('--con','Real',s.consumo.real),line('--gen','Previsto',s.consumo.pred,[6,4])],{min:0},[nowLine(s.now)]);
  chart('c2','line',s.L,[line('--con','Real',s.bateria.real),line('--cr','Projeção',s.bateria.pred,[6,4])],{min:0,max:100},[nowLine(s.now)])};
 $('#pdev').onchange=e=>{sel=e.target.value;drawCharts()};
 V.onclick=e=>{const btn=e.target.closest('[data-pick]'),id=btn&&btn.dataset.pick;if(id){sel=id;$('#pdev').value=id;drawCharts();$('#pcharts').scrollIntoView()}};
 drawRange();drawCharts()},

async energia(){
 const [s,b,l]=await Promise.all([api.getSources(),api.getBatteries(),api.getLoads()]);
 const top=[...l].sort((a,b)=>b.c-a.c).slice(0,3),mx=top.length?top[0].c:1;
 V.innerHTML=`<h2 class="sec">De onde vem a energia</h2>
  <div class="grid">${s.map(x=>`<div class="card"><h3>${esc(x.n)}</h3><div class="big">${esc(x.e)}</div><div class="mu">Eficiência ${esc(x.ef)} · ${tag(x.st)}</div></div>`).join('')}</div>
  <h2 class="sec">Armazenamento</h2>
  <div class="grid g2">${b.map(x=>`<div class="card"><h3>${esc(x.n)} · ${esc(x.tp)}</h3><div class="big">${x.pct}<small>% de ${esc(x.cap)}</small></div>
   <div class="batt">${[...Array(10)].map((_,i)=>`<i style="${i<Math.round(x.pct/10)?'background:'+(x.st==='Carregando'?'var(--con)':col(x.pct)):''}"></i>`).join('')}</div>
   ${tag(x.st)}${x.st==='Carregando'?' <span class="mu">⚡ recebendo carga</span>':''}</div>`).join('')}</div>
  <h2 class="sec">Maiores consumidores agora</h2>
  <div class="card">${top.map(x=>`<div>${esc(x.n)} <span class="mu">${num(x.c)} W</span>${bar(x.c/mx*100,'var(--con)')}</div>`).join('')||'<div class="mu">Sem dados de consumo.</div>'}</div>`},

async historico(){
 const draw=async k=>{
  charts.forEach(c=>c.destroy());charts=[];
  const h=await api.getHistory(k);
  V.innerHTML='';V.append(filters([['hoje','Hoje'],['7d','7 dias'],['30d','30 dias']],k,draw));
  V.insertAdjacentHTML('beforeend',`<div class="grid g2"><div class="card"><h3>Energia gerada (${h.u})</h3><div class="chart"><canvas id="c1"></canvas></div></div><div class="card"><h3>Energia consumida (${h.u})</h3><div class="chart"><canvas id="c2"></canvas></div></div></div><div class="card"><h3>Bateria armazenada (%)</h3><div class="chart"><canvas id="c3"></canvas></div></div>`);
  chart('c1','line',h.L,[lineDataset('--gen','Gerada',h.gen)]);chart('c2','line',h.L,[lineDataset('--con','Consumida',h.con)]);chart('c3','line',h.L,[lineDataset('--sto','Armazenada',h.sto)],{min:0,max:100})};
 draw('hoje')},

async alertas(){
 const draw=async()=>{
  const list=await userAlerts(),unread=list.filter(a=>!a.read).length;
  V.innerHTML=`<div class="row">${unread?'<button class="btn" data-readall="1">Marcar todos como lidos</button>':''}<span class="mu">${unread} ${unread===1?'não lido':'não lidos'}</span></div>`
   +(list.length?list.map(alertHTML).join(''):'<div class="card mu">Nenhum alerta no momento.</div>')};
 bindAlerts(draw);draw()},

async conta(){
 const p={...prefs};
 const sw=(k,label,desc)=>`<div class="set"><div><b>${label}</b><div class="mu">${desc}</div></div><button class="sw${p[k]?' on':''}" data-pref="${k}" role="switch" aria-checked="${!!p[k]}" aria-label="${label}"></button></div>`;
 V.innerHTML=`<div class="card" style="max-width:640px">
  <h3>Perfil</h3>
  <div class="set"><label for="uname"><b>Nome</b></label><input id="uname" type="text" value="${esc(p.name)}" maxlength="60"></div>
  <div class="set"><label for="umail"><b>E-mail</b></label><input id="umail" type="email" value="${esc(p.email)}" maxlength="80"></div>
  <h3 style="margin-top:20px">Notificações</h3>
  ${sw('critical','Bateria crítica','Avisar quando um dispositivo estiver com bateria muito baixa')}
  ${sw('high','Consumo elevado','Avisar quando o consumo passar do seu limite')}
  ${sw('mail','Receber alertas por e-mail','Enviar um resumo para o e-mail acima')}
  <div class="set"><div><b>Limite de consumo</b><div class="mu">Alerta acima de <span id="limv">${p.limit}</span> W</div></div><input id="limr" type="range" min="100" max="600" step="10" value="${p.limit}" aria-label="Limite de consumo em watts"></div>
  <div id="cerr" role="alert" style="color:var(--cr);min-height:20px;margin-top:12px;font-size:13px"></div>
  <div class="row" style="margin:8px 0 0"><button class="btn on" id="csave">Salvar alterações</button><span class="mu" id="cok" role="status"></span></div></div>`;
 const clear=()=>{$('#cerr').textContent='';$('#cok').textContent=''};
 V.onclick=e=>{const b=e.target.closest('[data-pref]');if(!b)return;const k=b.dataset.pref;p[k]=!p[k];b.classList.toggle('on',p[k]);b.setAttribute('aria-checked',String(p[k]));clear()};
 $('#limr').oninput=e=>{p.limit=+e.target.value;$('#limv').textContent=p.limit;clear()};
 $('#uname').oninput=clear;$('#umail').oninput=clear;
 $('#csave').onclick=()=>{
  const name=$('#uname').value.trim(),mail=$('#umail').value.trim();
  if(!name){$('#cerr').textContent='Informe seu nome.';return}
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)){$('#cerr').textContent='Informe um e-mail válido.';return}
  p.name=name;p.email=mail;prefs=p;
  const saved=savePrefs(p);updateWho();
  $('#cerr').textContent='';
  $('#cok').textContent=saved?'Alterações salvas.':'Alterações aplicadas nesta sessão (não foi possível salvar no navegador).'}}
};

const NAV=[['inicio','Início'],['dispositivos','Meus dispositivos'],['previsao','Previsão de bateria'],['energia','Minha energia'],['historico','Histórico'],['alertas','Alertas'],['conta','Minha conta']];
updateWho();
startApp(NAV,pages);
