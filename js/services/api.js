/* ===== 2. CAMADA DE DADOS: trocar o corpo por fetch() ao integrar =====
   Contrato: todo método devolve uma Promise (erros viram Promise rejeitada) e entrega uma
   CÓPIA dos dados — quem chama pode alterar o resultado sem afetar o "banco" (MOCK). */
const wait=v=>new Promise(r=>setTimeout(()=>r(structuredClone(v)),120));
const MAINTENANCE_STORAGE_KEY='eh-maintenance-requests';
const MAINTENANCE_STATUSES=['Recebida','Agendada','Em rota','Concluída'];
function readMaintenanceRequests(){
 const saved=localStorage.getItem(MAINTENANCE_STORAGE_KEY);
 if(saved===null){
  localStorage.setItem(MAINTENANCE_STORAGE_KEY,JSON.stringify(MOCK.requests));
  return MOCK.requests.map(r=>({...r}));
 }
 let requests;
 try{requests=JSON.parse(saved)}catch(e){throw new Error('Os pedidos de manutenção salvos estão inválidos. Limpe os dados locais para continuar.')}
 if(!Array.isArray(requests))throw new Error('Os pedidos de manutenção salvos estão inválidos. Limpe os dados locais para continuar.');
 return requests;
}
function saveMaintenanceRequests(requests){localStorage.setItem(MAINTENANCE_STORAGE_KEY,JSON.stringify(requests))}
/* Converte 'AAAA-MM-DD' em Date local; null se o texto não for uma data real (ex.: 2026-02-31) */
function parseDay(value){
 if(typeof value!=='string')return null;
 const date=new Date(`${value}T00:00:00`);
 if(Number.isNaN(date.getTime()))return null;
 const back=`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
 return back===value?date:null;
}
const api={
 /* Totais de dispositivos calculados da lista, para nunca divergirem dela (no back-end real, já virão consistentes) */
 getOverview(){
  const on=MOCK.devices.filter(d=>d.status==='Online').length;
  return wait({...MOCK.overview,devices:{total:MOCK.devices.length,online:on,offline:MOCK.devices.length-on}});
 },
 getSources:()=>wait(MOCK.sources),getLoads:()=>wait(MOCK.loads),
 getBatteries:()=>wait(MOCK.batteries),getDevices:()=>wait(MOCK.devices),getAlerts:()=>wait(MOCK.alerts),
 getForecasts:()=>wait(MOCK.forecast),
 async getMaintenanceRequests(){return wait(readMaintenanceRequests())},
 async requestMaintenance(deviceId,{reason,note}){
  const requests=readMaintenanceRequests();
  const r={id:requests.reduce((max,item)=>Math.max(max,Number(item.id)||0),0)+1,deviceId,reason,note,status:'Recebida',createdAt:new Date().toISOString(),scheduledFor:null};
  requests.push(r);saveMaintenanceRequests(requests);return wait(r)
 },
 /* scheduledFor vazio mantém a data já agendada; voltar para "Recebida" limpa a data. */
 async updateMaintenanceRequest(id,{status,scheduledFor}){
  if(!MAINTENANCE_STATUSES.includes(status))throw new Error('Status de manutenção inválido.');
  const requests=readMaintenanceRequests(),request=requests.find(r=>String(r.id)===String(id));
  if(!request)throw new Error('Solicitação de manutenção não encontrada.');
  let day=scheduledFor||null;
  if(day&&!parseDay(day))throw new Error('Informe uma data válida para o agendamento.');
  if(status==='Recebida')day=null;
  else if(!day)day=request.scheduledFor||null;
  if(status==='Agendada'){
   const date=parseDay(day);
   if(!date)throw new Error('Informe uma data válida para o agendamento.');
   const today=new Date();today.setHours(0,0,0,0);
   if(date<today)throw new Error('A data do agendamento não pode estar no passado.');
  }
  Object.assign(request,{status,scheduledFor:day});
  saveMaintenanceRequests(requests);return wait(request)
 },
 /* Séries de previsão de um dispositivo. range: '24h' (12 h passadas + 24 h futuras, passo de 1 h)
    ou '7d' (3 dias passados + 7 futuros, passo de 1 dia). timestamps[nowIndex] = momento atual.
    measured: valores medidos (null no futuro); predicted: previsão (consumo em Wh, bateria em %). */
 getForecastSeries(id,range){
  const d=MOCK.devices.find(x=>x.id===id),f=MOCK.forecast.items.find(x=>x.id===id);
  if(!d||!f)return Promise.reject(new Error('Dispositivo sem previsão.'));
  const hourly=range==='24h',past=hourly?12:3,future=hourly?24:7,stepHours=hourly?1:24;
  const ph=+id.slice(4),n=past+1+future;
  const now=new Date();if(hourly)now.setMinutes(0,0,0);else now.setHours(0,0,0,0);
  const timestamps=[...Array(n)].map((_,i)=>new Date(now.getTime()+(i-past)*stepHours*3600000).toISOString());
  const wave=i=>Math.sin((i+ph)*(hourly?.55:1.1));
  const predicted=timestamps.map((_,i)=>+(d.consumption*(1+.12*wave(i))).toFixed(1));
  const measured=timestamps.map((_,i)=>i<=past?+(d.consumption*(1+.12*wave(i)+.06*Math.sin(i*2.3+ph))).toFixed(1):null);
  const perDay=f.daysLeft?-d.battery/f.daysLeft:(f.sustainable?.6:0);
  const level=timestamps.map((_,i)=>Math.round(Math.min(100,Math.max(0,d.battery+perDay*(i-past)*stepHours/24))));
  return wait({step:hourly?'hour':'day',timestamps,nowIndex:past,
   consumption:{measured,predicted},
   battery:{measured:level.map((v,i)=>i<=past?v:null),predicted:level.map((v,i)=>i>=past?v:null)}})},
 /* Histórico agregado. range: 'hoje' (24 pontos por hora desde 00:00) ou '7d'/'30d'/'custom' (um ponto por dia,
    terminando hoje). generatedKwh/consumedKwh em kWh por passo; storagePct em %. */
 getHistory(range){
  const n={hoje:24,'7d':7,'30d':30,custom:14}[range];
  if(!n)return Promise.reject(new Error('Período de histórico inválido.'));
  const h=range==='hoje',start=new Date();start.setHours(0,0,0,0);
  if(!h)start.setDate(start.getDate()-(n-1));
  const timestamps=[...Array(n)].map((_,i)=>{const t=new Date(start);if(h)t.setHours(i);else t.setDate(start.getDate()+i);return t.toISOString()});
  const generatedKwh=timestamps.map((_,i)=>+(h?Math.max(0,Math.sin((i-6)/12*Math.PI))*0.45:3.5+Math.sin(i*1.3)*1.2).toFixed(2));
  const consumedKwh=timestamps.map((_,i)=>+(h?0.12+Math.abs(Math.sin(i/3))*0.1:3.6+Math.cos(i*.9)*.6).toFixed(2));
  let s=60;const storagePct=generatedKwh.map((g,i)=>{s=Math.min(98,Math.max(15,s+(g-consumedKwh[i])*(h?20:4)));return Math.round(s)});
  return wait({step:h?'hour':'day',timestamps,generatedKwh,consumedKwh,storagePct})}
};
