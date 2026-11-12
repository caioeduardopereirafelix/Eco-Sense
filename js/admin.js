/* ===== PAINEL ADMIN (futura rota /admin) ===== */
/* ===== 4. TELAS ===== */
const pages={
async dashboard(){const[o,a,h]=await Promise.all([api.getOverview(),api.getAlerts(),api.getHistory('hoje')]);
 V.innerHTML=`<div class="grid">
 <div class="card"><h3>Energia gerada</h3><div class="big">${bigKwh(o.generation.todayKwh)}</div>${changeText(o.generation.changePct,true)}</div>
 <div class="card"><h3>Energia consumida</h3><div class="big">${bigPower(o.consumption.nowW)} <small>agora</small></div><div class="mu">Período: ${kwh(o.consumption.todayKwh)} · ${changeText(o.consumption.changePct,false,'')}</div></div>
 <div class="card"><h3>Energia armazenada</h3><div class="big">${o.storage.pct==null?"—":num(o.storage.pct)}<small>%</small></div>${bar(o.storage.pct||0,'var(--sto)')}<div class="mu">${storageText(o.storage)} · ${tag(o.storage.status)}</div></div>
 <div class="card"><h3>Dispositivos</h3><div class="big">${num(o.devices.total)}</div><div class="mu"><span class="s-Online">● ${num(o.devices.online)} online</span> · <span class="s-Offline">● ${num(o.devices.offline)} offline</span></div></div></div>
 <div class="grid g2"><div class="card"><h3>Gerada × consumida hoje (${historyEnergy(h).unit})</h3><div class="chart"><canvas id="c1"></canvas></div></div>
 <div class="card"><h3>Nível de armazenamento hoje (%)</h3><div class="chart"><canvas id="c2"></canvas></div></div></div>
 <div class="card"><h3>Alertas recentes</h3><div id="recent"></div></div>`;
 const drawAlerts=()=>{$('#recent').innerHTML=a.length?withRead(a).slice(0,3).map(alertHTML).join(''):'<div class="mu">Nenhum alerta no momento.</div>'};
 V.onclick=e=>{const b=e.target.closest('[data-read]');if(b){readIds.add(b.dataset.read);drawAlerts()}};
 drawAlerts();
 chart('c1','line',historyLabels(h),[lineDataset('--gen','Gerada',historyEnergy(h).gen),lineDataset('--con','Consumida',historyEnergy(h).con)]);
 chart('c2','line',historyLabels(h),[lineDataset('--sto','Armazenada',h.storagePct)],{min:0,max:100})},
async geracao(){const s=await api.getSources(),h=await api.getHistory('7d');
 V.innerHTML=`<div class="grid">${s.map(x=>sourceCard(x,{showUpdated:true})).join('')}</div>
 <div class="card"><h3>Geração ao longo da semana (${historyEnergy(h).unit})</h3><div class="chart"><canvas id="c1"></canvas></div></div>`;
 chart('c1','line',historyLabels(h),[lineDataset('--gen','Total gerado',historyEnergy(h).gen)])},
async consumo(){const l=await api.getLoads();
 V.innerHTML=`<div class="card" style="margin-bottom:16px"><h3>Maiores consumidores agora</h3>${topConsumers(l)}</div>
 ${['Alta','Média','Baixa'].map(p=>`<div class="card" style="margin-bottom:16px"><h3>Prioridade ${p.toLowerCase()}</h3><div class="tw"><table><tr><th>Dispositivo</th><th>Tipo</th><th>Atual</th><th>Média</th><th>Status</th></tr>${l.filter(x=>x.priority===p).map(x=>`<tr><td>${esc(x.name)}</td><td>${esc(x.type)}</td><td>${power(x.currentW)}</td><td>${power(x.avgW)}</td><td>${tag(x.status)}</td></tr>`).join('')}</table></div></div>`).join('')}`},
async armazenamento(){const b=await api.getBatteries();
 V.innerHTML=`<div class="grid g2">${b.map(batteryCard).join('')}</div>`},
async manutencao(){
 let requests,devices,fc;
 try{[requests,devices,fc]=await Promise.all([api.getMaintenanceRequests(),api.getDevices(),api.getForecasts()])}
 catch(error){V.innerHTML=`<div class="banner" role="alert">Não foi possível carregar as solicitações de manutenção: ${esc(error.message)}</div>`;return}
 const deviceById=byId(devices),forecastById=byId(fc.items),statuses=MAINTENANCE_STATUSES;
 const offsetDate=days=>{const d=new Date();d.setDate(d.getDate()+days);return`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`};
 const today=offsetDate(0),lastUpcomingDay=offsetDate(21);
 const upcoming=r=>r.status==='Agendada'&&r.scheduledFor>=today&&r.scheduledFor<=lastUpcomingDay;
 const riskOf=d=>{
  const f=forecastById[d.id];
  if(d.status==='Offline'||d.battery<20||(f&&f.daysLeft!=null&&f.daysLeft<=3))return{label:'Crítico',color:css('--cr'),className:'high'};
  if(d.battery<45||(f&&f.daysLeft!=null&&f.daysLeft<=7))return{label:'Atenção',color:css('--wn'),className:'medium'};
  return{label:'Baixo',color:css('--ok'),className:'low'}
 };
 /* filtro e enquadramento do mapa sobrevivem à atualização automática */
 const st=pageState({scope:'abertas',mapView:null});
 let notice='',map=null;
 const regionOf=r=>deviceById[r.deviceId]?.region||'Sem região definida';
 const requestCard=r=>{
  const device=deviceById[r.deviceId],risk=device&&riskOf(device);
  const requestStatus=statuses.includes(r.status)?r.status:'Recebida';
  return `<article class="card request-card">
   <div class="row" style="justify-content:space-between;margin:0">
    <div><h3>${esc(device?device.name:r.deviceId)} · ${esc(r.deviceId)}</h3><div>${requestTag(requestStatus)} ${risk?`<span class="tag s-${risk.label}">Risco ${risk.label.toLowerCase()}</span>`:''}</div></div>
    <span class="mu">${device?esc(device.location):'Local não cadastrado'} · Pedido ${esc(timeAgo(r.createdAt))}</span>
   </div>
   <dl class="kv">
    <dt>Motivo</dt><dd>${esc(r.reason||'Não informado')}</dd>
    <dt>Observação</dt><dd>${esc(r.note||'—')}</dd>
    <dt>Visita</dt><dd>${r.scheduledFor?formatDay(r.scheduledFor):'Não agendada'}</dd>
    ${risk?`<dt>Risco do dispositivo</dt><dd>${risk.label}</dd>`:''}
   </dl>
   <div class="request-actions">
    <label>Status<select data-status="${esc(String(r.id))}">${statuses.map(s=>`<option${s===requestStatus?' selected':''}>${s}</option>`).join('')}</select></label>
    <label>Data da visita<input type="date" min="${today}" data-date="${esc(String(r.id))}" value="${esc(r.scheduledFor||'')}"></label>
    <button class="btn on" data-save="${esc(String(r.id))}">Salvar atualização</button>
   </div>
  </article>`
 };
 const draw=()=>{
  if(map){map.remove();map=null}
  const scope=st.scope;
  const matching=requests.filter(r=>{
   if(scope==='abertas')return r.status!=='Concluída';
   if(scope==='proximas')return upcoming(r);
   if(scope==='concluidas')return r.status==='Concluída';
   return true
  });
  const groups=Object.groupBy
   ?Object.groupBy(matching,regionOf)
   :matching.reduce((all,r)=>{(all[regionOf(r)]||(all[regionOf(r)]=[])).push(r);return all},{});
  const upcomingCount=requests.filter(upcoming).length;
  V.innerHTML=`${notice?`<div class="banner" role="status">${esc(notice)}</div>`:''}
   <div class="grid">
    <div class="card"><h3>Pedidos em aberto</h3><div class="big">${requests.filter(r=>r.status!=='Concluída').length}</div><div class="mu">Aguardando atendimento ou visita</div></div>
    <div class="card"><h3>Visitas nas próximas 3 semanas</h3><div class="big">${upcomingCount}</div><div class="mu">${formatDay(today)} a ${formatDay(lastUpcomingDay)}</div></div>
    <div class="card"><h3>Dispositivos em risco</h3><div class="big">${devices.filter(d=>riskOf(d).className!=='low').length}</div><div class="mu">Priorize os pontos críticos no mapa</div></div>
   </div>
   <div class="card" style="margin-bottom:16px">
    <h2 class="sec">Mapa de dispositivos por risco</h2>
    <div id="maintenance-map" class="maintenance-map" role="img" aria-label="Mapa dos dispositivos coloridos conforme o risco"></div>
    <div class="map-legend"><span class="map-key"><i style="background:var(--cr)"></i>Crítico</span><span class="map-key"><i style="background:var(--wn)"></i>Atenção</span><span class="map-key"><i style="background:var(--ok)"></i>Baixo</span></div>
    <p id="map-note" class="mu" style="margin-top:8px">Cada ponto fica na latitude e longitude cadastradas do dispositivo.</p>
   </div>
   <h2 class="sec">Solicitações por região</h2>
   <div id="maintenance-filters"></div>
   ${Object.keys(groups).length?Object.keys(groups).sort((a,b)=>a.localeCompare(b,'pt-BR')).map(region=>`<section><h3 class="sec">${esc(region)} <span class="mu">(${groups[region].length})</span></h3>${groups[region].map(requestCard).join('')}</section>`).join(''):`<div class="card mu">Nenhuma solicitação neste filtro.</div>`}`;
  const filterHost=$('#maintenance-filters');
  filterHost.append(filters([['abertas','Em aberto'],['proximas','Próximas 3 semanas'],['concluidas','Concluídas'],['todas','Todas']],scope,k=>{st.scope=k;draw()}));
  const mapNote=$('#map-note');
  const points=devices.filter(d=>Number.isFinite(d.latitude)&&Number.isFinite(d.longitude));
  if(typeof window.L==='undefined'||typeof window.L.map!=='function'){mapNote.textContent='O mapa não foi carregado. Verifique a conexão para acessar o serviço de mapas.'}
  else{
   const dark=document.documentElement.dataset.theme==='dark';
   map=window.L.map('maintenance-map',{minZoom:1,maxZoom:18,zoomControl:true,worldCopyJump:false,maxBoundsViscosity:1});
   /* Tiles do OpenStreetMap (a CARTO passou a exigir chave de API). A OSM exige Referer, então abra o
      front por um servidor local (Live Server etc.), não como arquivo. No tema escuro, um filtro CSS
      (.map-dark em style.css) escurece os tiles. */
   $('#maintenance-map').classList.toggle('map-dark',dark);
   const tileLayer=window.L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',errorTileUrl:'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='}).addTo(map);
   let tileErrors=0;
   tileLayer.on('tileerror',()=>{if(++tileErrors===3)mapNote.textContent='Não foi possível carregar o mapa base. Verifique a conexão com o serviço de mapas; os pontos continuam visíveis.'});
   /* mantém o tamanho do mapa em dia: sem isso, ao arrastar aparecem faixas em branco */
   const sizeMap=map;let ro=null;
   if(window.ResizeObserver){ro=new ResizeObserver(()=>{if(sizeMap===map)sizeMap.invalidateSize({animate:false})});ro.observe($('#maintenance-map'))}
   setTimeout(()=>{if(sizeMap===map)sizeMap.invalidateSize({animate:false})},0);
   onLeave(()=>{if(ro)ro.disconnect();if(map===sizeMap){map.remove();map=null}});
   const markers=points.map(d=>{
    const risk=riskOf(d);
    const marker=window.L.circleMarker([d.latitude,d.longitude],{radius:9,color:risk.color,fillColor:risk.color,fillOpacity:.85,weight:2}).addTo(map);
    marker.bindPopup(`<b>${esc(d.name)}</b><br>${esc(d.location||'Local não informado')} · ${esc(d.region||'Sem região')}<br>Risco: ${risk.label} · Bateria: ${num(d.battery)}%`);
    return marker
   });
   if(markers.length){const b=window.L.featureGroup(markers).getBounds();map.fitBounds(b.pad(.2));map.setMaxBounds(b.pad(4))}
   else map.setView([-15.79,-47.88],11);
   /* mantém o zoom/posição que o usuário escolheu quando a tela é redesenhada */
   if(st.mapView)map.setView(st.mapView.center,st.mapView.zoom,{animate:false});
   map.on('moveend',()=>{if(sizeMap===map)st.mapView={center:map.getCenter(),zoom:map.getZoom()}});
   if(points.length<devices.length)mapNote.textContent=`${devices.length-points.length} dispositivo(s) sem coordenadas não aparecem no mapa.`
  }
  V.onclick=async e=>{
   const button=e.target.closest('[data-save]');if(!button)return;
   const id=button.dataset.save,card=button.closest('.request-card'),statusField=card.querySelector('[data-status]'),dateField=card.querySelector('[data-date]');
   button.disabled=true;
   try{
    await api.updateMaintenanceRequest(id,{status:statusField.value,scheduledFor:dateField.value||null});
    requests.splice(0,requests.length,...await api.getMaintenanceRequests());
    notice='Solicitação atualizada.';draw()
   }catch(error){button.disabled=false;notice=`Não foi possível atualizar a solicitação: ${error.message}`;draw()}
  }
 };
 draw()
},
async dispositivos(notice=''){
 const [devices,fc]=await Promise.all([api.getDevices(),api.getForecasts()]);
 const fById=byId(fc.items);
 /* o filtro sobrevive à atualização automática (com o formulário aberto ela fica pausada) */
 const st=pageState({filter:'all'});
 let current=st.filter,form=null; /* form: null | {mode:'new'|'edit', s: configuração, channel: dados do canal} */
 /* saldo = energia coletada - consumo (Wh). Sem harvesting, o saldo é só o consumo negativo. */
 /* null quando falta consumo ou coleta medidos (não dá para calcular o saldo) */
 const balance=d=>d.consumption==null||(d.harvesting&&d.harvesting.collected==null)?null:(d.harvesting?d.harvesting.collected:0)-d.consumption;
 const isOn=d=>d.status==='Online';
 const filtersFn={
  all:()=>true,
  on:isOn,
  off:d=>!isOn(d),
  low:d=>isOn(d)&&d.battery<20,
  neg:d=>isOn(d)&&balance(d)!=null&&balance(d)<0,
  risk:d=>{const f=fById[d.id];return f.daysLeft!=null&&f.daysLeft<=7}
 };
 const card=d=>{
  const b=balance(d),on=isOn(d),f=fById[d.id];
  const saldo=on&&b!=null?`<span class="${b<0?'dn':'up'}">${b<0?'▼':'▲'} ${b<0?'−':'+'}${energy(Math.abs(b))}/dia</span>`:'—';
  return `<div class="card">
   ${deviceCardHead(d,{showId:true})}
   <dl class="kv">
    ${kvRow('Consumo',perDay(d.consumption))}
    ${kvRow('Energia coletada',harvestText(d,'Sem harvesting'))}
    ${kvRow('Saldo energético',saldo)}
    ${kvRow('Autonomia prevista',daysTag(f)+(f.confidence==null?'':` <span class="mu">(${f.confidence}%)</span>`))}
    ${kvRow('Temperatura',d.temperature==null?'—':`${num(d.temperature)} °C`)}
    ${kvRow('Transmissão',`a cada ${num(d.txInterval)} min`)}
   </dl>
   <div class="card-actions"><button class="btn" data-edit="${esc(d.id)}">Editar</button><button class="btn danger" data-del="${esc(d.id)}">Remover</button></div>
  </div>`;
 };
 const draw=k=>{
  current=st.filter=k;
  const list=devices.filter(filtersFn[k]);
  V.innerHTML=notice?`<div class="banner" role="status">${esc(notice)}</div>`:'';
  const top=filters([['all','Todos'],['on','Online'],['off','Offline'],['low','Bateria baixa'],['neg','Saldo negativo'],['risk','Em risco (≤ 7 dias)']],k,draw);
  top.insertAdjacentHTML('beforeend','<button class="btn on" data-new="1" style="margin-left:auto">+ Novo dispositivo</button>');
  V.append(top);
  if(form)V.insertAdjacentHTML('beforeend',deviceForm(form));
  V.insertAdjacentHTML('beforeend',list.length
   ?`<div class="grid">${list.map(card).join('')}</div>`
   :'<div class="card mu">Nenhum dispositivo neste filtro.</div>');
  if(form)$('#devform').scrollIntoView({block:'start'});
 };

 /* ---------- cadastro: formulário de novo dispositivo / edição ---------- */
 const FIELD_KEYS=[['battery','Bateria'],['consumption','Consumo'],['temperature','Temperatura'],['harvested','Energia coletada']];
 const fieldOptions=(sel,channel)=>['none',...[1,2,3,4,5,6,7,8].map(i=>'field'+i)].map(f=>{
  const label=f==='none'?'— não mede':(channel&&channel.fields[f]?`${f} · ${channel.fields[f]}`:f);
  return`<option value="${f}"${f===sel?' selected':''}>${esc(label)}</option>`}).join('');
 const input=(name,label,value,attrs='')=>`<label>${label}<input name="${name}" value="${value==null?'':esc(value)}" ${attrs}></label>`;
 const deviceForm=({mode,s,channel,error})=>{
  const f=s.fields||{},c=s.conversion||{};
  return`<form class="card" id="devform" style="margin-bottom:16px" novalidate>
   <h3>${mode==='new'?'Novo dispositivo':`Editar ${esc(s.id)}`}</h3>
   <div class="form-sec">Identificação</div>
   <div class="form-grid">
    ${input('id','ID *',s.id,mode==='edit'?'disabled':'maxlength="40" placeholder="ex.: IOT-011"')}
    ${input('name','Nome *',s.name,'maxlength="80"')}
    ${input('location','Local',s.location,'maxlength="120"')}
    ${input('region','Região',s.region,'maxlength="60"')}
    ${input('latitude','Latitude',s.latitude,'inputmode="decimal"')}
    ${input('longitude','Longitude',s.longitude,'inputmode="decimal"')}
    ${input('type','Tipo',s.type,'list="dl-type" maxlength="40"')}
    <label>Prioridade<select name="priority">${['Alta','Média','Baixa'].map(p=>`<option${p===(s.priority||'Média')?' selected':''}>${p}</option>`).join('')}</select></label>
    ${input('harvestingType','Tipo de coleta',s.harvestingType,'list="dl-harv" maxlength="40" placeholder="vazio = não coleta"')}
    ${input('txInterval','Envia a cada (min)',s.txInterval,'inputmode="numeric"')}
    ${input('sunHours','Horas de sol/dia',s.sunHours,'inputmode="decimal"')}
   </div>
   <datalist id="dl-type"><option>Sensor<option>Comunicação<option>Atuador<option>Vídeo<option>Sistema solar<option>Residência</datalist>
   <datalist id="dl-harv"><option>Solar<option>Térmico<option>Cinético<option>Vibração<option>RF</datalist>
   <div class="form-sec">Canal do ThingSpeak</div>
   <div class="form-grid">
    <label>Channel ID<span style="display:flex;gap:6px"><input name="channelId" value="${s.channelId??''}" inputmode="numeric"><button type="button" class="btn" data-fetch="1" style="white-space:nowrap">Buscar canal</button></span></label>
    ${input('historyChunkDays','Dias por pedido do histórico',s.historyChunkDays,'inputmode="numeric" placeholder="31 (canais rápidos: 2)"')}
   </div>
   <div class="mu" id="chinfo" style="margin-top:8px">${channel?`Canal <b>${esc(channel.name||'')}</b>${channel.lastEntryAt?` · última entrada ${esc(timeAgo(channel.lastEntryAt))}`:' · sem entradas'}${channel.description?` · ${esc(channel.description.slice(0,140))}`:''}`:'Informe o Channel ID e clique em Buscar canal para ver o nome de cada field.'}</div>
   <div class="form-grid" style="margin-top:8px">
    ${FIELD_KEYS.map(([k,l])=>`<label>${l}<select name="f_${k}">${fieldOptions(f[k]||'none',channel)}</select></label>`).join('')}
   </div>
   <div class="form-sec">Conversões (opcional)</div>
   <div class="mu" style="margin-bottom:8px">Bateria em volts: informe as tensões de vazia e cheia para converter em %. Escalas multiplicam o valor lido (ex.: 0,001 para mW → W; negativo inverte o sinal).</div>
   <div class="form-grid">
    ${input('batteryVoltageMin','Bateria vazia (V)',c.batteryVoltageMin,'inputmode="decimal"')}
    ${input('batteryVoltageMax','Bateria cheia (V)',c.batteryVoltageMax,'inputmode="decimal"')}
    ${input('consumptionScale','Escala do consumo',c.consumptionScale,'inputmode="decimal" placeholder="1"')}
    ${input('harvestedScale','Escala da coleta',c.harvestedScale,'inputmode="decimal" placeholder="1"')}
    ${input('temperatureScale','Escala da temperatura',c.temperatureScale,'inputmode="decimal" placeholder="1"')}
   </div>
   <div role="alert" style="color:var(--cr);min-height:20px;margin-top:12px;font-size:13px">${esc(error||'')}</div>
   <div class="row" style="margin:8px 0 0"><button class="btn on" type="submit">Salvar</button><button class="btn" type="button" data-cancel="1">Cancelar</button></div>
  </form>`};
 /* lê o formulário; números aceitam vírgula; vazio = null */
 const readForm=()=>{
  const el=$('#devform'),v=n=>el.elements[n].value.trim();
  const n=x=>{const t=v(x).replace(',','.');if(t==='')return null;const r=Number(t);if(!Number.isFinite(r))throw new Error(`Valor inválido em "${el.elements[x].closest('label').firstChild.textContent.trim()}".`);return r};
  return{id:form.mode==='edit'?form.s.id:v('id'),name:v('name'),location:v('location')||null,region:v('region')||null,
   latitude:n('latitude'),longitude:n('longitude'),type:v('type')||null,priority:v('priority'),harvestingType:v('harvestingType')||null,
   txInterval:n('txInterval'),sunHours:n('sunHours'),channelId:n('channelId'),historyChunkDays:n('historyChunkDays'),
   fields:Object.fromEntries(FIELD_KEYS.map(([k])=>[k,v('f_'+k)])),
   conversion:{batteryVoltageMin:n('batteryVoltageMin'),batteryVoltageMax:n('batteryVoltageMax'),
    consumptionScale:n('consumptionScale'),harvestedScale:n('harvestedScale'),temperatureScale:n('temperatureScale')}};
 };

 V.onclick=async e=>{
  const t=e.target.closest('[data-new],[data-edit],[data-del],[data-cancel],[data-fetch]');if(!t)return;
  if(t.dataset.new){notice='';form={mode:'new',s:{priority:'Média',txInterval:5,fields:{battery:'field1',consumption:'field2',temperature:'field3',harvested:'field4'},conversion:{}}};draw(current)}
  else if(t.dataset.cancel){form=null;draw(current)}
  else if(t.dataset.edit){
   notice='';t.disabled=true;
   try{form={mode:'edit',s:await api.getDeviceSettings(t.dataset.edit)};draw(current)}
   catch(err){notice=`Não foi possível abrir o cadastro: ${err.message}`;draw(current)}
  }
  else if(t.dataset.del){
   const d=devices.find(x=>x.id===t.dataset.del);
   if(!confirm(`Remover "${d.name}" (${d.id})?\n\nSó o cadastro é apagado; os dados continuam no ThingSpeak.`))return;
   try{await api.deleteDevice(d.id);await pages.dispositivos(`Dispositivo ${d.name} removido.`)}
   catch(err){notice=`Não foi possível remover: ${err.message}`;draw(current)}
  }
  else if(t.dataset.fetch){
   try{form.s=readForm()}catch(err){form.error=err.message;draw(current);return}
   if(!form.s.channelId){form.error='Informe o Channel ID.';draw(current);return}
   t.textContent='Buscando…';t.disabled=true;
   try{
    const ch=await api.getChannelInfo(form.s.channelId);
    form.channel=ch;form.error='';
    /* preenche o que estiver vazio com os dados do canal */
    if(!form.s.name)form.s.name=ch.name;
    if(form.s.latitude==null&&ch.latitude!=null){form.s.latitude=ch.latitude;form.s.longitude=ch.longitude}
   }catch(err){form.error=err.message}
   draw(current);
  }
 };
 const submit=V.onsubmit=async e=>{
  /* getAttribute: o form tem um campo "id", e form.id devolveria esse campo, não o atributo */
  if(e.target.getAttribute('id')!=='devform')return;
  e.preventDefault();
  const btn=e.target.querySelector('[type=submit]');
  try{form.s=readForm()}catch(err){form.error=err.message;draw(current);return}
  btn.disabled=true;btn.textContent='Salvando…';
  try{
   if(form.mode==='new')await api.createDevice(form.s);else await api.updateDevice(form.s.id,form.s);
   await pages.dispositivos(`Dispositivo ${form.s.name} salvo.`);
  }catch(err){form.error=err.message;draw(current)}
 };
 /* só remove o próprio handler: na atualização automática, a versão nova já registrou o dela */
 onLeave(()=>{if(V.onsubmit===submit)V.onsubmit=null});
 draw(current)},
previsao:()=>renderForecast({detailed:true}),
historico:()=>renderHistory({custom:true,storageTitle:'Energia armazenada'}),
async alertas(){const a=await api.getAlerts();
 const draw=()=>{V.innerHTML=a.length?withRead(a).map(alertHTML).join(''):'<div class="card mu">Nenhum alerta no momento.</div>'};
 V.onclick=e=>{const b=e.target.closest('[data-read]');if(b){readIds.add(b.dataset.read);draw()}};
 draw()},
async configuracoes(){V.innerHTML=`<div class="card" style="max-width:640px">
 <div class="set"><div><b>Perfil de consumo</b><div class="mu">Define como as cargas são priorizadas</div></div><select><option>Equilibrado</option><option>Econômico</option><option>Desempenho</option></select></div>
 <div class="set"><div><b>Limite de consumo</b><div class="mu">Alerta acima de <span id="lim">300</span> W</div></div><input type="range" min="100" max="600" value="300" oninput="lim.textContent=this.value"></div>
 ${[['Modo economia automático',1],['Alertas de bateria crítica',1],['Alertas de consumo elevado',1],['Alertas por e-mail',0]].map(([n,o])=>`<div class="set"><b>${n}</b><button class="sw${o?' on':''}" aria-label="${n}" onclick="this.classList.toggle('on')"></button></div>`).join('')}
 <div class="set"><div><b>Sistema</b><div class="mu">Atualização dos dados</div></div><select><option>A cada 1 min</option><option>A cada 5 min</option></select></div></div>`}
};
const NAV=[['dashboard','Dashboard'],['geracao','Geração de Energia'],['consumo','Consumo'],['armazenamento','Armazenamento'],['dispositivos','Dispositivos IoT'],['manutencao','Manutenção'],['previsao','Previsão'],['historico','Histórico'],['alertas','Alertas'],['configuracoes','Configurações']];
startApp(NAV,pages,{staticPages:['configuracoes']});
