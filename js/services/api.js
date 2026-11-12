/* ===== 2. CAMADA DE DADOS =====
   `api` aponta para o back-end real (httpApi, no fim do arquivo) se js/config.js definir
   ECOSENSE_API_URL; senão, para os dados mockados (mockApi). As duas têm os mesmos métodos.
   Contrato: todo método devolve uma Promise (erros viram Promise rejeitada) e entrega uma
   CÓPIA dos dados — quem chama pode alterar o resultado sem afetar o "banco" (MOCK). */
const API_URL=(window.ECOSENSE_API_URL||'').replace(/\/+$/,'')||null;
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
const mockApi={
 /* Totais de dispositivos calculados da lista, para nunca divergirem dela (no back-end real, já virão consistentes) */
 getOverview(){
  const on=MOCK.devices.filter(d=>d.status==='Online').length;
  return wait({...MOCK.overview,devices:{total:MOCK.devices.length,online:on,offline:MOCK.devices.length-on}});
 },
 getSources:()=>wait(MOCK.sources),getLoads:()=>wait(MOCK.loads),
 getBatteries:()=>wait(MOCK.batteries),getDevices:()=>wait(MOCK.devices),getAlerts:()=>wait(MOCK.alerts),
 getForecasts:()=>wait(MOCK.forecast),
 /* Cadastro de dispositivos. No mock fica só na memória da página (some ao recarregar) e o
    dispositivo novo aparece Offline, porque não há canal do ThingSpeak para ler. */
 async getDeviceSettings(id){
  const d=MOCK.devices.find(x=>x.id===id);if(!d)throw new Error(`Dispositivo ${id} não encontrado.`);
  return wait(MOCK.settings[id]||{id:d.id,name:d.name,location:d.location,region:d.region,latitude:d.latitude,longitude:d.longitude,
   txInterval:d.txInterval,type:null,priority:null,harvestingType:d.harvesting?d.harvesting.type:null,sunHours:null,channelId:null,historyChunkDays:null,
   fields:{battery:'field1',consumption:'field2',temperature:'field3',harvested:'field4'},conversion:{}});
 },
 async createDevice(s){
  if(!s.id||!/^[A-Za-z0-9_-]{1,40}$/.test(s.id))throw new Error('O ID deve ter até 40 caracteres: letras, números, - ou _.');
  if(!s.name||!s.name.trim())throw new Error('Informe o nome.');
  if(MOCK.devices.some(d=>d.id===s.id))throw new Error(`Já existe um dispositivo com o ID ${s.id}.`);
  const d={id:s.id,name:s.name.trim(),location:s.location||null,region:s.region||null,latitude:s.latitude,longitude:s.longitude,status:'Offline',
   battery:0,consumption:0,lastSeenAt:null,temperature:null,txInterval:s.txInterval||5,harvesting:s.harvestingType?{type:s.harvestingType,collected:0}:null};
  MOCK.devices.push(d);MOCK.forecast.items.push({id:s.id,daysLeft:null,sustainable:false,confidence:null,sunHours:s.sunHours??null});
  MOCK.settings[s.id]=structuredClone(s);return wait(d);
 },
 async updateDevice(id,s){
  const d=MOCK.devices.find(x=>x.id===id);if(!d)throw new Error(`Dispositivo ${id} não encontrado.`);
  if(!s.name||!s.name.trim())throw new Error('Informe o nome.');
  Object.assign(d,{name:s.name.trim(),location:s.location||null,region:s.region||null,latitude:s.latitude,longitude:s.longitude,txInterval:s.txInterval||d.txInterval});
  MOCK.settings[id]={...structuredClone(s),id};return wait(d);
 },
 async deleteDevice(id){
  const i=MOCK.devices.findIndex(x=>x.id===id);if(i<0)throw new Error(`Dispositivo ${id} não encontrado.`);
  MOCK.devices.splice(i,1);MOCK.forecast.items=MOCK.forecast.items.filter(f=>f.id!==id);delete MOCK.settings[id];return wait(null);
 },
 async getChannelInfo(){throw new Error('A consulta de canais do ThingSpeak precisa do back-end (defina ECOSENSE_API_URL em js/config.js).')},
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
 getHistory(range,{from,to}={}){
  let n={hoje:24,'7d':7,'30d':30}[range];
  const h=range==='hoje',start=new Date();start.setHours(0,0,0,0);
  if(range==='custom'){
   const a=parseDay(from),b=parseDay(to);
   if(!a||!b)return Promise.reject(new Error('Informe as datas inicial e final.'));
   if(a>b)return Promise.reject(new Error('A data inicial deve ser anterior à final.'));
   n=Math.round((b-a)/86400000)+1;start.setTime(a.getTime());
  }else if(!n)return Promise.reject(new Error('Período de histórico inválido.'));
  else if(!h)start.setDate(start.getDate()-(n-1));
  const timestamps=[...Array(n)].map((_,i)=>{const t=new Date(start);if(h)t.setHours(i);else t.setDate(start.getDate()+i);return t.toISOString()});
  const generatedKwh=timestamps.map((_,i)=>+(h?Math.max(0,Math.sin((i-6)/12*Math.PI))*0.45:3.5+Math.sin(i*1.3)*1.2).toFixed(2));
  const consumedKwh=timestamps.map((_,i)=>+(h?0.12+Math.abs(Math.sin(i/3))*0.1:3.6+Math.cos(i*.9)*.6).toFixed(2));
  let s=60;const storagePct=generatedKwh.map((g,i)=>{s=Math.min(98,Math.max(15,s+(g-consumedKwh[i])*(h?20:4)));return Math.round(s)});
  return wait({step:h?'hour':'day',timestamps,generatedKwh,consumedKwh,storagePct})}
};

/* ===== API real (back-end Java, ver docs/API.md) =====
   Usada quando window.ECOSENSE_API_URL estiver definida em js/config.js. */
async function http(path,{method='GET',body}={}){
 let res;
 try{
  res=await fetch(API_URL+path,{method,headers:body?{'Content-Type':'application/json'}:undefined,body:body?JSON.stringify(body):undefined});
 }catch(e){throw new Error('Não foi possível conectar ao servidor. Verifique se o back-end está rodando.')}
 const data=await res.json().catch(()=>null);
 if(!res.ok)throw new Error(data&&data.message||`Erro ${res.status} ao acessar a API.`);
 return data;
}
const query=params=>{const q=new URLSearchParams(Object.entries(params).filter(([,v])=>v!=null&&v!==''));return q.toString()?`?${q}`:''};
const httpApi={
 getOverview:()=>http('/overview'),getSources:()=>http('/sources'),getLoads:()=>http('/loads'),
 getBatteries:()=>http('/batteries'),getDevices:()=>http('/devices'),getAlerts:()=>http('/alerts'),
 getForecasts:()=>http('/forecasts'),
 getForecastSeries:(id,range)=>http(`/devices/${encodeURIComponent(id)}/forecast${query({range})}`),
 getHistory:(range,{from,to}={})=>http(`/history${query({range,from,to})}`),
 getMaintenanceRequests:()=>http('/maintenance-requests'),
 getDeviceSettings:id=>http(`/devices/${encodeURIComponent(id)}/settings`),
 createDevice:s=>http('/devices',{method:'POST',body:s}),
 updateDevice:(id,s)=>http(`/devices/${encodeURIComponent(id)}`,{method:'PUT',body:s}),
 deleteDevice:id=>http(`/devices/${encodeURIComponent(id)}`,{method:'DELETE'}),
 getChannelInfo:channelId=>http(`/thingspeak/channels/${encodeURIComponent(channelId)}`),
 requestMaintenance:(deviceId,{reason,note})=>http('/maintenance-requests',{method:'POST',body:{deviceId,reason,note}}),
 updateMaintenanceRequest:(id,{status,scheduledFor})=>http(`/maintenance-requests/${encodeURIComponent(id)}`,{method:'PATCH',body:{status,scheduledFor}})
};
const api=API_URL?httpApi:mockApi;
