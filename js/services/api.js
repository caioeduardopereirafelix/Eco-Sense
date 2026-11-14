/* ===== CAMADA DE DADOS: chamadas ao back-end (Java + Spring Boot; contrato em docs/API.md) =====
   O back-end lê os canais do ThingSpeak e guarda cadastro e usuários no MySQL. A URL vem de js/config.js.
   Contrato: todo método devolve uma Promise; erros viram Promise rejeitada com a mensagem
   enviada pelo back-end ({"message": "..."}), pronta para mostrar ao usuário.
   Toda chamada leva o token do login (js/auth.js). Sessão expirada (401) → volta para o login. */
const API_URL=(window.ECOSENSE_API_URL||'http://localhost:8080/api').replace(/\/+$/,'');
const MAINTENANCE_STATUSES=['Recebida','Agendada','Em rota','Concluída'];

/* public: chamada sem token e sem redirecionar no 401 (usada pelo login) */
async function http(path,{method='GET',body,public:isPublic=false}={}){
 const headers={};
 if(body)headers['Content-Type']='application/json';
 const session=isPublic?null:auth.get();
 if(session)headers.Authorization=`Bearer ${session.token}`;
 let res;
 try{
  res=await fetch(API_URL+path,{method,headers,body:body?JSON.stringify(body):undefined});
 }catch(e){throw new Error(`Não foi possível conectar ao servidor (${API_URL}). Verifique se o back-end está rodando.`)}
 const data=res.status===204?null:await res.json().catch(()=>null);
 if(res.status===401&&!isPublic){
  /* sessão expirada, senha trocada ou usuário removido: volta ao login (a tela fica esperando) */
  auth.toLogin();return new Promise(()=>{});
 }
 if(!res.ok)throw new Error(data&&data.message||`Erro ${res.status} ao acessar a API.`);
 return data;
}
const query=params=>{const q=new URLSearchParams(Object.entries(params).filter(([,v])=>v!=null&&v!==''));return q.toString()?`?${q}`:''};

const api={
 /* login e conta */
 login:(email,password)=>http('/auth/login',{method:'POST',body:{email,password},public:true}),
 getMe:()=>http('/me'),
 updateMe:name=>http('/me',{method:'PUT',body:{name}}),
 changePassword:(currentPassword,newPassword)=>http('/me/password',{method:'PUT',body:{currentPassword,newPassword}}),
 /* energia e dispositivos */
 /* group: todos | iot | sistemas (padrão: todos) */
 getOverview:group=>http(`/overview${query({group})}`),getSources:group=>http(`/sources${query({group})}`),getLoads:()=>http('/loads'),
 getBatteries:()=>http('/batteries'),getDevices:()=>http('/devices'),getAlerts:()=>http('/alerts'),
 getForecasts:()=>http('/forecasts'),
 getForecastSeries:(id,range)=>http(`/devices/${encodeURIComponent(id)}/forecast${query({range})}`),
 getHistory:(range,{from,to,group}={})=>http(`/history${query({range,from,to,group})}`),
 /* cadastro de dispositivos (admin) */
 getDeviceSettings:id=>http(`/devices/${encodeURIComponent(id)}/settings`),
 createDevice:s=>http('/devices',{method:'POST',body:s}),
 updateDevice:(id,s)=>http(`/devices/${encodeURIComponent(id)}`,{method:'PUT',body:s}),
 deleteDevice:id=>http(`/devices/${encodeURIComponent(id)}`,{method:'DELETE'}),
 getChannelInfo:channelId=>http(`/thingspeak/channels/${encodeURIComponent(channelId)}`),
 /* usuários (admin) */
 getUsers:()=>http('/users'),
 createUser:u=>http('/users',{method:'POST',body:u}),
 updateUser:(id,u)=>http(`/users/${encodeURIComponent(id)}`,{method:'PUT',body:u}),
 resetUserPassword:(id,password)=>http(`/users/${encodeURIComponent(id)}/password`,{method:'PUT',body:{password}}),
 deleteUser:id=>http(`/users/${encodeURIComponent(id)}`,{method:'DELETE'}),
 /* manutenção (usuário comum vê só os próprios pedidos; status só o admin muda) */
 getMaintenanceRequests:()=>http('/maintenance-requests'),
 requestMaintenance:(deviceId,{reason,note})=>http('/maintenance-requests',{method:'POST',body:{deviceId,reason,note}}),
 updateMaintenanceRequest:(id,{status,scheduledFor})=>http(`/maintenance-requests/${encodeURIComponent(id)}`,{method:'PATCH',body:{status,scheduledFor}})
};
