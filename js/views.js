/* ===== TELAS E COMPONENTES COMPARTILHADOS (usuário e admin) =====
   O que é igual nos dois portais mora aqui; as diferenças entram como opções.
   Depende de shared.js (esc, num, tag, bar, col, chart, nowLine, filters, daysTag, kvRow…). */

/* ---------- utilitários ---------- */
const byId=list=>Object.fromEntries(list.map(x=>[x.id,x]));
/* 'AAAA-MM-DD' → 'DD/MM/AAAA' */
const formatDay=value=>value?value.split('-').reverse().join('/'):'';
/* tag de status da manutenção ("Em rota" vira a classe s-Em-rota) */
const requestTag=status=>`<span class="tag s-${String(status).replace(/ /g,'-')}">${esc(status)}</span>`;

/* ---------- alertas lidos nesta sessão (a API devolve cópias, então o estado fica aqui) ---------- */
const readIds=new Set();
const withRead=list=>list.map(a=>({...a,read:readIds.has(String(a.id))}));

/* ---------- dispositivos ---------- */
/* energia por dia (Wh/dia); null = o canal não mede essa leitura → "—" */
const perDay=wh=>wh==null?'—':`${energy(wh)}/dia`;
const harvestText=(d,emptyLabel)=>d.harvesting
 ?`${perDay(d.harvesting.collected)} <span class="mu">(${esc(d.harvesting.type)})</span>`
 :`<span class="mu">${emptyLabel}</span>`;
/* topo comum dos cards de dispositivo: local, nome, status e bateria */
const deviceCardHead=(d,{showId=false}={})=>`<h3>${showId?`${esc(d.id)} · `:''}${esc(d.location||'Local não informado')}</h3>
   <div class="big" style="font-size:20px">${esc(d.name)}</div>
   <div style="margin-top:6px">${tag(d.status)}</div>
   ${bar(d.battery)}
   <div class="mu">Bateria ${num(d.battery)}% · Última comunicação ${esc(timeAgo(d.lastSeenAt))}</div>`;

/* ---------- energia: fontes, baterias e consumidores ---------- */
const sourceCard=(x,{showUpdated=false}={})=>`<div class="card"><h3>${esc(x.name)}</h3><div class="big">${bigKwh(x.energyKwh)}</div><div class="mu">Eficiência ${pct(x.efficiencyPct)} · ${tag(x.status)}</div>${showUpdated?`<div class="mu" style="margin-top:6px">Atualizado ${esc(timeAgo(x.updatedAt))}</div>`:''}</div>`;
const batteryCard=x=>{
 const charging=x.status==='Carregando',filled=Math.round(x.pct/10);
 const cells=[...Array(10)].map((_,i)=>`<i style="${i<filled?`background:${charging?'var(--con)':col(x.pct)}`:''}"></i>`).join('');
 return `<div class="card"><h3>${esc(x.name)} · ${esc(x.type)}</h3><div class="big">${num(x.pct)}<small>% de ${kwh(x.capacityKwh)}</small></div>
   <div class="batt">${cells}</div>${tag(x.status)}${charging?' <span class="mu">⚡ recebendo carga</span>':''}</div>`};
/* barras com as 3 cargas de maior consumo (conteúdo do card, sem o card) */
const topConsumers=loads=>{
 /* só cargas que medem consumo (currentW null = canal sem essa leitura) */
 const top=loads.filter(x=>x.currentW!=null).sort((a,b)=>b.currentW-a.currentW).slice(0,3),max=top.length&&top[0].currentW||1;
 return top.map(x=>`<div>${esc(x.name)} <span class="mu">${power(x.currentW)}</span>${bar(x.currentW/max*100,'var(--con)')}</div>`).join('')||'<div class="mu">Sem dados de consumo.</div>'};

/* variação vs. ontem: ▲/▼ com a cor certa. higherIsGood: true para geração, false para consumo */
const changeText=(changePct,higherIsGood,suffix=' vs. ontem')=>{
 if(changePct==null)return'<span class="mu">sem comparação com ontem</span>';
 const up=changePct>=0,good=up===higherIsGood;
 return `<span class="${good?'up':'dn'}">${up?'▲':'▼'} ${pct(Math.abs(changePct))}${suffix}</span>`};

/* armazenamento: "6,8 kWh de 10 kWh"; sem baterias do sistema cadastradas, o % é a média dos dispositivos */
const storageText=s=>s.capacityKwh!=null?`${kwh(s.storedKwh)} de ${kwh(s.capacityKwh)}`:'Média das baterias dos dispositivos';

/* ---------- histórico: rótulos e unidade a partir da resposta da API ---------- */
const historyLabels=h=>h.timestamps.map(t=>timeLabel(t,h.step));
/* energia do histórico (kWh) na escala adequada: {unit:'mWh/h', gen:[…], con:[…]} */
const historyEnergy=h=>{const toWh=l=>l.map(v=>v==null?null:v*1000),g=toWh(h.generatedKwh),c=toWh(h.consumedKwh),s=energyScale(g,c);
 return{unit:`${s.unit}/${h.step==='hour'?'h':'dia'}`,gen:s.scale(g),con:s.scale(c)}};

/* ---------- tela: histórico ----------
   custom: inclui o filtro "Personalizado" (admin); storageTitle: título do gráfico de bateria */
async function renderHistory({custom=false,storageTitle='Bateria armazenada'}={}){
 const options=[['hoje','Hoje'],['7d','7 dias'],['30d','30 dias'],...(custom?[['custom','Personalizado']]:[])];
 /* período personalizado: começa nos últimos 14 dias; o back-end guarda até 31 dias */
 const isoDay=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
 const daysAgo=n=>{const d=new Date();d.setDate(d.getDate()-n);return isoDay(d)};
 const period={from:daysAgo(13),to:daysAgo(0)},minDay=daysAgo(30),maxDay=daysAgo(0);
 const draw=async k=>{
  let h=null,error='';
  try{h=await api.getHistory(k,k==='custom'?period:{})}
  catch(e){if(k!=='custom')throw e;error=e.message}
  charts.forEach(c=>c.destroy());charts=[];
  V.innerHTML='';V.append(filters(options,k,draw));
  if(k==='custom'){
   V.insertAdjacentHTML('beforeend',`<div class="row"><input type="date" id="hfrom" value="${period.from}" min="${minDay}" max="${maxDay}" aria-label="Data inicial"> até <input type="date" id="hto" value="${period.to}" min="${minDay}" max="${maxDay}" aria-label="Data final"><span role="alert" style="color:var(--cr);font-size:13px">${esc(error)}</span></div>`);
   $('#hfrom').onchange=e=>{period.from=e.target.value;draw('custom')};
   $('#hto').onchange=e=>{period.to=e.target.value;draw('custom')};
  }
  if(!h)return;
  V.insertAdjacentHTML('beforeend',`<div class="grid g2"><div class="card"><h3>Energia gerada (${historyEnergy(h).unit})</h3><div class="chart"><canvas id="c1"></canvas></div></div><div class="card"><h3>Energia consumida (${historyEnergy(h).unit})</h3><div class="chart"><canvas id="c2"></canvas></div></div></div><div class="card"><h3>${esc(storageTitle)} (%)</h3><div class="chart"><canvas id="c3"></canvas></div></div>`);
  const labels=historyLabels(h);
  const e=historyEnergy(h);
  chart('c1','line',labels,[lineDataset('--gen','Gerada',e.gen)]);
  chart('c2','line',labels,[lineDataset('--con','Consumida',e.con)]);
  chart('c3','line',labels,[lineDataset('--sto','Armazenada',h.storagePct)],{min:0,max:100})};
 await draw('hoje')}

/* ---------- tela: previsão de bateria ----------
   detailed (admin): mostra ID, local, exposição solar e contexto sazonal */
async function renderForecast({detailed=false}={}){
 const [devices,fc]=await Promise.all([api.getDevices(),api.getForecasts()]);
 const fById=byId(fc.items);
 /* ordem: menor autonomia primeiro, depois sustentáveis, depois sem leitura */
 const rank=r=>r.f.daysLeft!=null?r.f.daysLeft:r.f.sustainable?1000:2000;
 const rows=devices.map(d=>({d,f:fById[d.id]})).sort((a,b)=>rank(a)-rank(b));
 const withReading=rows.filter(r=>r.f.daysLeft!=null||r.f.sustainable);
 const atRisk=rows.filter(r=>r.f.daysLeft!=null&&r.f.daysLeft<=7);
 const first=rows.find(r=>r.f.daysLeft!=null);
 const suns=rows.map(r=>r.f.sunHours).filter(h=>h!=null);
 const avgSun=suns.length?num(+(suns.reduce((a,b)=>a+b,0)/suns.length).toFixed(1)):'—';
 let sel=(first||withReading[0]||rows[0]).d.id,range='24h',drawToken=0;
 const idSuffix=d=>detailed?` <span class="mu">${esc(d.id)}</span>`:'';
 const tr=({d,f})=>`<tr>
  <td>${esc(d.name)}${idSuffix(d)}</td>${detailed?`<td>${esc(d.location||'—')}</td>`:''}
  <td>${daysTag(f)}</td><td>${f.confidence==null?'—':f.confidence+'%'}</td>
  ${detailed?`<td>${f.sunHours==null?'—':num(f.sunHours)+' h/dia'}</td>`:''}<td>${num(d.battery)}%</td>
  <td>${f.daysLeft!=null||f.sustainable?`<button class="btn" data-pick="${esc(d.id)}" aria-label="Ver gráficos de ${esc(d.name)}">Ver<span class="pvx"> gráficos</span></button>`:''}</td></tr>`;
 const head=`<tr><th>Dispositivo</th>${detailed?'<th>Local</th>':''}<th>Autonomia</th><th><span class="pvx">Confiança</span><span class="pvm">Conf.</span></th>${detailed?'<th>Exposição solar</th>':''}<th>Bateria</th><th></th></tr>`;
 V.innerHTML=`<div class="grid">
  <div class="card"><h3>Em risco (até 7 dias)</h3><div class="big">${atRisk.length}</div><div class="mu">de ${withReading.length} dispositivos com leitura</div></div>
  <div class="card"><h3>Menor autonomia</h3><div class="big">${first?first.f.daysLeft:'—'} <small>${first?(first.f.daysLeft===1?'dia':'dias'):''}</small></div><div class="mu">${first?esc(first.d.name):'Nenhum dispositivo em esgotamento'}</div></div>
  <div class="card"><h3>Sustentáveis</h3><div class="big">${rows.filter(r=>r.f.sustainable).length}</div><div class="mu">coletam mais do que consomem</div></div>
  ${detailed?`<div class="card"><h3>Contexto da previsão</h3><div class="big" style="font-size:22px">${esc(fc.season)}</div><div class="mu">Exposição solar média ${avgSun} h/dia · atualizado ${esc(timeAgo(fc.updatedAt))}</div></div>`:''}</div>
  <div class="card" style="margin-bottom:16px"><h3>${detailed?'Autonomia estimada por dispositivo':'Quanto tempo cada bateria deve durar'}</h3>
   <div class="tw"><table class="${detailed?'pv':'pvu'}">${head}${rows.map(tr).join('')}</table></div>
   ${detailed?'':`<div class="mu" style="margin-top:10px">Estimativas atualizadas ${esc(timeAgo(fc.updatedAt))}.</div>`}</div>
  <div class="card" id="pcharts" style="margin-bottom:16px"><div class="row">
   <select id="pdev" aria-label="Dispositivo">${withReading.map(r=>`<option value="${esc(r.d.id)}"${r.d.id===sel?' selected':''}>${esc(r.d.name)}${detailed?` · ${esc(r.d.id)}`:''}</option>`).join('')}</select>
   <span id="prange"></span></div>
   <div class="mu" id="pinfo" style="margin-bottom:12px"></div>
   <div class="grid g2" style="margin:0"><div><h3 class="mu" style="margin-bottom:8px">Consumo previsto × real (<span id="pcunit">Wh</span>)</h3><div class="chart"><canvas id="c1"></canvas></div></div>
   <div><h3 class="mu" style="margin-bottom:8px">Nível de bateria projetado (%)</h3><div class="chart"><canvas id="c2"></canvas></div></div></div>
   <div class="mu" style="margin-top:10px">A linha tracejada vertical marca o momento atual. Antes dela, dados medidos; depois, previsão.</div></div>`;
 const info=({f})=>{
  const parts=[`Autonomia: ${daysTag(f)}`];
  if(f.confidence!=null)parts.push(`confiança ${f.confidence}%`);
  if(detailed)parts.push(f.sunHours==null?'sem geração solar':`exposição solar ${num(f.sunHours)} h/dia`,`estação: ${esc(fc.season)}`);
  return parts.join(' · ')};
 const drawRange=()=>{const r=$('#prange');r.innerHTML='';const f=filters([['24h','Próximas 24 h'],['7d','Próximos 7 dias']],range,k=>{range=k;drawRange();drawCharts()});f.style.margin='0';r.append(f)};
 const drawCharts=async()=>{
  const token=++drawToken;
  const s=await api.getForecastSeries(sel,range);
  if(token!==drawToken)return;
  charts.forEach(c=>c.destroy());charts=[];
  $('#pinfo').innerHTML=info(rows.find(x=>x.d.id===sel));
  const line=(c,label,data,dash)=>({borderColor:css(c),label,data,fill:false,borderDash:dash||[]});
  /* rótulos relativos ao agora: "−3h", "agora", "+2d" */
  const labels=s.timestamps.map((_,i)=>{const k=i-s.nowIndex;return k===0?'agora':`${k<0?'−':'+'}${Math.abs(k)}${s.step==='hour'?'h':'d'}`});
  const es=energyScale(s.consumption.measured,s.consumption.predicted);$('#pcunit').textContent=es.unit;
  chart('c1','line',labels,[line('--con','Real',es.scale(s.consumption.measured)),line('--gen','Previsto',es.scale(s.consumption.predicted),[6,4])],{min:0},[nowLine(s.nowIndex)]);
  chart('c2','line',labels,[line('--con','Real',s.battery.measured),line('--cr','Projeção',s.battery.predicted,[6,4])],{min:0,max:100},[nowLine(s.nowIndex)])};
 $('#pdev').onchange=e=>{sel=e.target.value;drawCharts()};
 V.onclick=e=>{const btn=e.target.closest('[data-pick]'),id=btn&&btn.dataset.pick;if(id){sel=id;$('#pdev').value=id;drawCharts();$('#pcharts').scrollIntoView()}};
 drawRange();await drawCharts()}
