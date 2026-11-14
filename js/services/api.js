const API_URL=(window.ECOSENSE_API_URL||'http://localhost:8080/api').replace(/\/+$/,'');
const MAINTENANCE_STATUSES=['Recebida','Agendada','Em rota','Concluída'];

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
  auth.toLogin();return new Promise(()=>{});
 }
 if(!res.ok)throw new Error(data&&data.message||`Erro ${res.status} ao acessar a API.`);
 return data;
}
const query=params=>{const q=new URLSearchParams(Object.entries(params).filter(([,v])=>v!=null&&v!==''));return q.toString()?`?${q}`:''};

const api={
 login:(email,password)=>http('/auth/login',{method:'POST',body:{email,password},public:true}),
 getMe:()=>http('/me'),
 updateMe:name=>http('/me',{method:'PUT',body:{name}}),
 changePassword:(currentPassword,newPassword)=>http('/me/password',{method:'PUT',body:{currentPassword,newPassword}}),
 getOverview:group=>http(`/overview${query({group})}`),getSources:group=>http(`/sources${query({group})}`),getLoads:()=>http('/loads'),
 getBatteries:()=>http('/batteries'),getDevices:()=>http('/devices'),getAlerts:()=>http('/alerts'),
 getForecasts:()=>http('/forecasts'),
 getForecastSeries:(id,range)=>http(`/devices/${encodeURIComponent(id)}/forecast${query({range})}`),
 getHistory:(range,{from,to,group}={})=>http(`/history${query({range,from,to,group})}`),
 getDeviceSettings:id=>http(`/devices/${encodeURIComponent(id)}/settings`),
 createDevice:s=>http('/devices',{method:'POST',body:s}),
 updateDevice:(id,s)=>http(`/devices/${encodeURIComponent(id)}`,{method:'PUT',body:s}),
 deleteDevice:id=>http(`/devices/${encodeURIComponent(id)}`,{method:'DELETE'}),
 getChannelInfo:channelId=>http(`/thingspeak/channels/${encodeURIComponent(channelId)}`),
 getUsers:()=>http('/users'),
 createUser:u=>http('/users',{method:'POST',body:u}),
 updateUser:(id,u)=>http(`/users/${encodeURIComponent(id)}`,{method:'PUT',body:u}),
 resetUserPassword:(id,password)=>http(`/users/${encodeURIComponent(id)}/password`,{method:'PUT',body:{password}}),
 deleteUser:id=>http(`/users/${encodeURIComponent(id)}`,{method:'DELETE'}),
 getMaintenanceRequests:()=>http('/maintenance-requests'),
 requestMaintenance:(deviceId,{reason,note})=>http('/maintenance-requests',{method:'POST',body:{deviceId,reason,note}}),
 updateMaintenanceRequest:(id,{status,scheduledFor})=>http(`/maintenance-requests/${encodeURIComponent(id)}`,{method:'PATCH',body:{status,scheduledFor}})
};
