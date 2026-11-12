/* ===== CAMADA DE DADOS: chamadas ao back-end (Java + Spring Boot; contrato em docs/API.md) =====
   O back-end lê os canais do ThingSpeak e guarda o cadastro no MySQL. A URL vem de js/config.js.
   Contrato: todo método devolve uma Promise; erros viram Promise rejeitada com a mensagem
   enviada pelo back-end ({"message": "..."}), pronta para mostrar ao usuário. */
const API_URL=(window.ECOSENSE_API_URL||'http://localhost:8080/api').replace(/\/+$/,'');
const MAINTENANCE_STATUSES=['Recebida','Agendada','Em rota','Concluída'];

async function http(path,{method='GET',body}={}){
 let res;
 try{
  res=await fetch(API_URL+path,{method,headers:body?{'Content-Type':'application/json'}:undefined,body:body?JSON.stringify(body):undefined});
 }catch(e){throw new Error(`Não foi possível conectar ao servidor (${API_URL}). Verifique se o back-end está rodando.`)}
 const data=await res.json().catch(()=>null);
 if(!res.ok)throw new Error(data&&data.message||`Erro ${res.status} ao acessar a API.`);
 return data;
}
const query=params=>{const q=new URLSearchParams(Object.entries(params).filter(([,v])=>v!=null&&v!==''));return q.toString()?`?${q}`:''};

const api={
 getOverview:()=>http('/overview'),getSources:()=>http('/sources'),getLoads:()=>http('/loads'),
 getBatteries:()=>http('/batteries'),getDevices:()=>http('/devices'),getAlerts:()=>http('/alerts'),
 getForecasts:()=>http('/forecasts'),
 getForecastSeries:(id,range)=>http(`/devices/${encodeURIComponent(id)}/forecast${query({range})}`),
 getHistory:(range,{from,to}={})=>http(`/history${query({range,from,to})}`),
 /* cadastro de dispositivos (admin) */
 getDeviceSettings:id=>http(`/devices/${encodeURIComponent(id)}/settings`),
 createDevice:s=>http('/devices',{method:'POST',body:s}),
 updateDevice:(id,s)=>http(`/devices/${encodeURIComponent(id)}`,{method:'PUT',body:s}),
 deleteDevice:id=>http(`/devices/${encodeURIComponent(id)}`,{method:'DELETE'}),
 getChannelInfo:channelId=>http(`/thingspeak/channels/${encodeURIComponent(channelId)}`),
 /* manutenção */
 getMaintenanceRequests:()=>http('/maintenance-requests'),
 requestMaintenance:(deviceId,{reason,note})=>http('/maintenance-requests',{method:'POST',body:{deviceId,reason,note}}),
 updateMaintenanceRequest:(id,{status,scheduledFor})=>http(`/maintenance-requests/${encodeURIComponent(id)}`,{method:'PATCH',body:{status,scheduledFor}})
};
