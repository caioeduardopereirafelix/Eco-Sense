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
 V.innerHTML=`<div class="row"><button class="btn${eco?' on':''}" id="eco">${eco?'Modo Economia ativo':'Ativar Modo Economia'}</button></div>${eco?'<div class="banner">Modo Economia ativo: cargas de baixa prioridade reduzidas (simulação visual).</div>':''}
 <div class="card" style="margin-bottom:16px"><h3>Maiores consumidores agora</h3>${topConsumers(l)}</div>
 ${['Alta','Média','Baixa'].map(p=>`<div class="card" style="margin-bottom:16px"><h3>Prioridade ${p.toLowerCase()}</h3><div class="tw"><table><tr><th>Dispositivo</th><th>Tipo</th><th>Atual</th><th>Média</th><th>Status</th></tr>${l.filter(x=>x.priority===p).map(x=>{const c=eco&&p==='Baixa'&&x.currentW!=null?x.currentW*.5:x.currentW;return`<tr><td>${esc(x.name)}</td><td>${esc(x.type)}</td><td>${power(c)}</td><td>${power(x.avgW)}</td><td>${tag(x.status)}</td></tr>`}).join('')}</table></div></div>`).join('')}`;
 $('#eco').onclick=()=>{eco=!eco;pages.consumo()}},
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
 let scope='abertas',notice='',map=null;
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
    <p id="map-note" class="mu" style="margin-top:8px">Coordenadas demonstrativas no entorno de Brasília; atualize com latitude e longitude reais.</p>
   </div>
   <h2 class="sec">Solicitações por região</h2>
   <div id="maintenance-filters"></div>
   ${Object.keys(groups).length?Object.keys(groups).sort((a,b)=>a.localeCompare(b,'pt-BR')).map(region=>`<section><h3 class="sec">${esc(region)} <span class="mu">(${groups[region].length})</span></h3>${groups[region].map(requestCard).join('')}</section>`).join(''):`<div class="card mu">Nenhuma solicitação neste filtro.</div>`}`;
  const filterHost=$('#maintenance-filters');
  filterHost.append(filters([['abertas','Em aberto'],['proximas','Próximas 3 semanas'],['concluidas','Concluídas'],['todas','Todas']],scope,k=>{scope=k;draw()}));
  const mapNote=$('#map-note');
  const points=devices.filter(d=>Number.isFinite(d.latitude)&&Number.isFinite(d.longitude));
  if(typeof window.L==='undefined'||typeof window.L.map!=='function'){mapNote.textContent='O mapa não foi carregado. Verifique a conexão para acessar o serviço de mapas.'}
  else{
   const dark=document.documentElement.dataset.theme==='dark';
   map=window.L.map('maintenance-map',{minZoom:3,maxZoom:18,zoomControl:true,worldCopyJump:false,maxBoundsViscosity:1});
   /* CARTO em vez do servidor da OSM: a OSM bloqueia tiles quando a página é aberta como arquivo (sem Referer) */
   const tileLayer=window.L.tileLayer(`https://{s}.basemaps.cartocdn.com/${dark?'dark_all':'light_all'}/{z}/{x}/{y}{r}.png`,{subdomains:'abcd',maxZoom:18,attribution:'&copy; OpenStreetMap &copy; CARTO',errorTileUrl:'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='}).addTo(map);
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
    marker.bindPopup(`<b>${esc(d.name)}</b><br>${esc(d.location)} · ${esc(d.region||'Sem região')}<br>Risco: ${risk.label} · Bateria: ${num(d.battery)}%`);
    return marker
   });
   if(markers.length){const b=window.L.featureGroup(markers).getBounds();map.fitBounds(b.pad(.2));map.setMaxBounds(b.pad(4))}
   else map.setView([-15.79,-47.88],11);
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
async dispositivos(){
 const [devices,fc]=await Promise.all([api.getDevices(),api.getForecasts()]);
 const fById=byId(fc.items);
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
  </div>`;
 };
 const draw=k=>{
  const list=devices.filter(filtersFn[k]);
  V.innerHTML='';
  V.append(filters([['all','Todos'],['on','Online'],['off','Offline'],['low','Bateria baixa'],['neg','Saldo negativo'],['risk','Em risco (≤ 7 dias)']],k,draw));
  V.insertAdjacentHTML('beforeend',list.length
   ?`<div class="grid">${list.map(card).join('')}</div>`
   :'<div class="card mu">Nenhum dispositivo neste filtro.</div>');
 };
 draw('all')},
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
let eco=false;
const NAV=[['dashboard','Dashboard'],['geracao','Geração de Energia'],['consumo','Consumo'],['armazenamento','Armazenamento'],['dispositivos','Dispositivos IoT'],['manutencao','Manutenção'],['previsao','Previsão'],['historico','Histórico'],['alertas','Alertas'],['configuracoes','Configurações']];
startApp(NAV,pages);
