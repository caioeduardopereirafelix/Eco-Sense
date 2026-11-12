/* ===== CÓDIGO COMPARTILHADO (admin e usuário) ===== */
const $=s=>document.querySelector(s),V=$('#view');
const tag=s=>`<span class="tag s-${s}">${s}</span>`;
/* escapa HTML; null/undefined viram texto vazio (não "null") */
const esc=s=>s==null?'':String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
/* ---------- formatação (a API manda números puros e datas ISO; aqui viram texto pt-BR) ---------- */
/* num(3.4) → "3,4"; num(3.4,2) → "3,40" (casas fixas) */
const num=(n,digits)=>Number(n).toLocaleString('pt-BR',digits==null?undefined:{minimumFractionDigits:digits,maximumFractionDigits:digits});
/* valores ausentes (null) aparecem como "—" */
const pct=n=>n==null?'—':`${Number(n).toLocaleString('pt-BR',{maximumFractionDigits:1})}%`;
/* casas decimais conforme o tamanho: 312 → "312"; 48,5 → "48,5"; 4,82 → "4,82" */
const smart=n=>{const a=Math.abs(n);return Number(n).toLocaleString('pt-BR',{maximumFractionDigits:a>=100?0:a>=10?1:2})};
/* Potência e energia variam de microwatts (energy harvesting) a quilowatts: a unidade acompanha o valor.
   powerParts(0.00045) → ['0,45','mW']; energyParts(4820) → ['4,82','kWh'] */
const powerParts=w=>{const a=Math.abs(w);
 if(a>=1||a===0)return[smart(w),'W'];if(a>=0.0001)return[smart(w*1e3),'mW'];return[smart(w*1e6),'µW']};
const energyParts=wh=>{const a=Math.abs(wh);
 if(a>=100)return[smart(wh/1e3),'kWh'];if(a>=1||a===0)return[smart(wh),'Wh'];if(a>=0.001)return[smart(wh*1e3),'mWh'];return[smart(wh*1e6),'µWh']};
const power=w=>w==null?'—':powerParts(w).join(' ');
const energy=wh=>wh==null?'—':energyParts(wh).join(' ');
const kwh=n=>n==null?'—':energy(n*1000);
/* para os números grandes dos cards: "4,82 <small>kWh</small>" */
const bigPower=w=>w==null?'—':(([v,u])=>`${v} <small>${u}</small>`)(powerParts(w));
const bigKwh=n=>n==null?'—':(([v,u])=>`${v} <small>${u}</small>`)(energyParts(n*1000));
/* escala comum para uma série de gráfico em Wh: escolhe a unidade pelo maior valor */
function energyScale(...seriesWh){
 const max=Math.max(0,...seriesWh.flat().filter(v=>v!=null).map(Math.abs));
 const [unit,factor]=max>=100?['kWh',1e-3]:max>=1||max===0?['Wh',1]:max>=0.001?['mWh',1e3]:['µWh',1e6];
 return{unit,scale:list=>list.map(v=>v==null?null:+(v*factor).toPrecision(4))};
}
/* rótulo do eixo X a partir de um timestamp: passo 'hour' → "14h"; 'day' → "05/11" */
const timeLabel=(iso,step)=>{const d=new Date(iso);
 return step==='hour'?`${d.getHours()}h`:`${String(d.getDate()).padStart(2,'0')}/${String(d.getMonth()+1).padStart(2,'0')}`};
/* Tag de autonomia: dias restantes (crítico ≤3, atenção ≤7), "Sustentável", "Saldo negativo"
   (bateria ainda estável, mas consome mais do que coleta) ou "Sem leitura" */
const daysTag=f=>f.daysLeft!=null
 ?`<span class="tag s-${f.daysLeft<=3?'Crítico':f.daysLeft<=7?'Atenção':'Normal'}">${f.daysLeft} ${f.daysLeft===1?'dia':'dias'}</span>`
 :f.sustainable?'<span class="tag s-Normal">Sustentável</span>'
 :f.deficit?'<span class="tag s-Atenção" title="A bateria não está caindo, mas o consumo supera a coleta">Saldo negativo</span>'
 :'<span class="tag s-Baixa">Sem leitura</span>';
/* tem previsão (dias, sustentável ou saldo negativo) */
const hasForecast=f=>f.daysLeft!=null||f.sustainable||f.deficit;
/* saldo energético coleta − consumo: "+1,2 Wh/dia" / "−730 Wh/dia" */
const balanceText=wh=>wh==null?null:`${wh>0?'+':wh<0?'−':''}${energy(Math.abs(wh))}/dia`;
/* Linha vertical tracejada marcando "agora" nos gráficos de previsão */
const nowLine=i=>({id:'nowLine',afterDatasetsDraw(c){const x=c.scales.x.getPixelForValue(i),{top,bottom}=c.chartArea,g=c.ctx;g.save();g.strokeStyle=css('--mu');g.setLineDash([3,3]);g.beginPath();g.moveTo(x,top);g.lineTo(x,bottom);g.stroke();g.setLineDash([]);g.fillStyle=css('--mu');g.font='12px IBM Plex Sans,system-ui,sans-serif';g.fillText('agora',x+5,top+13);g.restore()}});
const col=p=>p<20?'var(--cr)':p<45?'var(--wn)':'var(--ok)';
const bar=(p,c)=>`<div class="bar"><b style="width:${p}%;background:${c||col(p)}"></b></div>`;
let charts=[];
/* funções executadas ao sair da tela atual (ex.: destruir o mapa e seus listeners) */
const cleanups=[],onLeave=f=>cleanups.push(f);
/* Estado da tela atual que sobrevive à atualização automática (filtro, dispositivo escolhido, zoom
   do mapa…). Zera ao trocar de tela. Uso: const st=pageState({filter:'all'}); st.filter='on'; */
let viewState={};
const pageState=defaults=>{for(const k in defaults)if(!(k in viewState))viewState[k]=defaults[k];return viewState};
const css=n=>getComputedStyle(document.documentElement).getPropertyValue(n).trim();
function chart(id,type,L,sets,extra={},plugins=[]){const c=new Chart(document.getElementById(id),{type,plugins,data:{labels:L,datasets:sets.map(s=>({tension:.35,pointRadius:0,borderWidth:2,fill:type==='line'&&s.fill,...s}))},options:{responsive:true,maintainAspectRatio:false,interaction:{mode:'index',intersect:false},plugins:{legend:{labels:{color:css('--mu'),boxWidth:12}}},scales:{x:{ticks:{color:css('--mu'),maxTicksLimit:8},grid:{display:false}},y:{ticks:{color:css('--mu')},grid:{color:css('--bd')},...extra}}}});charts.push(c)}
/* dataset de linha preenchida. (Não se chama `L` para não confundir com o global do Leaflet.) */
const lineDataset=(c,l,d)=>({borderColor:css(c),backgroundColor:css(c)+'22',label:l,data:d,fill:true});
/* Tempo relativo a partir de uma data ISO: "agora", "há 5 minutos", "ontem", "há 3 semanas"… */
const rtf=new Intl.RelativeTimeFormat('pt-BR',{numeric:'auto'});
function timeAgo(iso){
 const t=Date.parse(iso);if(Number.isNaN(t))return'—';
 const s=Math.round((t-Date.now())/1000);
 for(const [unit,sec] of [['year',31536000],['month',2592000],['week',604800],['day',86400],['hour',3600],['minute',60]])
  if(Math.abs(s)>=sec)return rtf.format(Math.round(s/sec),unit);
 return'agora';
}
/* alerta: level 'critical' | 'high' | 'warning' | 'info' define cor e ícone */
const ALERT_ICONS={critical:'🔴',high:'🟠',warning:'🟡',info:'🟢'};
const alertHTML=a=>`<div class="alert a-${esc(a.level)}${a.read?' read':''}"><span aria-hidden="true">${ALERT_ICONS[a.level]||'⚪'}</span><div><b>${esc(a.title)}</b><div class="mu">${esc(a.message)} · ${esc(timeAgo(a.createdAt))}</div></div>${a.read?'':`<button class="btn" data-read="${esc(a.id)}">Marcar como lido</button>`}</div>`;
function filters(opts,cur,cb){const r=document.createElement('div');r.className='row';r.innerHTML=opts.map(([k,l])=>`<button class="btn${k===cur?' on':''}" data-k="${k}">${l}</button>`).join('');r.onclick=e=>{const k=e.target.dataset.k;if(k)cb(k)};return r}
const kvRow=(l,v)=>`<dt>${l}</dt><dd>${v}</dd>`;
/* ---------- atualização automática ----------
   A cada REFRESH_MS a tela atual é recarregada em silêncio (sem "Carregando…", mantendo rolagem,
   filtros e seleção). O intervalo acompanha o cache de 60 s do back-end: atualizar mais rápido só
   devolveria o mesmo dado. Fica pausada com a aba em segundo plano, logo após o usuário interagir,
   com formulário aberto ou com campo alterado e não salvo. */
const REFRESH_MS=60000,IDLE_MS=10000;
/* campo com valor diferente do que veio na tela = edição não salva (data-live = campo de navegação) */
const unsaved=()=>[...V.querySelectorAll('input,select,textarea')].some(el=>{
 if(el.closest('[data-live]'))return false;
 if(el.tagName==='SELECT')return[...el.options].some(o=>o.selected!==o.defaultSelected);
 if(el.type==='checkbox'||el.type==='radio')return el.checked!==el.defaultChecked;
 return el.value!==el.defaultValue});
const updatedText=t=>{const s=Math.round((Date.now()-t)/1000);
 return s<10?'Atualizado agora':s<60?`Atualizado há ${s - s%5} s`:`Atualizado há ${Math.floor(s/60)} min`};

/* Monta navegação, tema e roteamento por hash. NAV = [[chave,título],...]; pages = {chave: async()=>{}}
   staticPages: telas sem dados da API (configurações, conta), que não são atualizadas sozinhas */
function startApp(NAV,pages,{staticPages=[]}={}){
 $('#nav').innerHTML=NAV.map(([k,n])=>`<a href="#${k}" data-k="${k}">${n}</a>`).join('');
 $('#theme').insertAdjacentHTML('beforebegin','<span class="upd mu" id="upd" role="status" aria-live="off"></span>');
 /* Navegação concorrente: cada go() ganha um número. As chamadas à api feitas por uma tela que
    já foi abandonada nunca resolvem, então ela não chega a sobrescrever a tela atual. */
 let navToken=0;
 for(const name of Object.keys(api)){
  const original=api[name];
  api[name]=(...args)=>{const token=navToken;
   return Promise.resolve().then(()=>original(...args)).then(
    v=>token===navToken?v:new Promise(()=>{}),
    e=>token===navToken?Promise.reject(e):new Promise(()=>{}))};
 }
 const pageKey=()=>location.hash.slice(1)in pages?location.hash.slice(1):NAV[0][0];
 const runAll=fs=>fs.forEach(f=>{try{f()}catch(e){}});
 /* gráficos e limpezas da versão anterior da tela, descartados quando a nova versão aparece */
 let stale=null;
 const flushStale=()=>{if(!stale)return;stale.charts.forEach(c=>c.destroy());runAll(stale.cleanups);stale=null};
 let lastLoad=0,lastTouch=0,loading=false,failed=false;
 const showStamp=()=>{const el=$('#upd');
  if(staticPages.includes(pageKey())||!lastLoad){el.textContent='';return}
  el.classList.toggle('err',failed);
  const editing=Date.now()-lastLoad>=REFRESH_MS&&(V.querySelector('form,textarea')||unsaved());
  el.textContent=failed?'Falha ao atualizar · tentando de novo':updatedText(lastLoad)+(editing?' · pausada durante a edição':'')};
 /* silent: atualização automática (mantém a tela até os dados novos chegarem) */
 async function go(silent){silent=silent===true;
  const k=pageKey(),token=++navToken;
  const y=window.scrollY,oldClick=V.onclick,oldSubmit=V.onsubmit;
  flushStale();
  if(silent){stale={charts,cleanups:cleanups.splice(0)};charts=[]}
  else{
   viewState={};failed=false;
   charts.forEach(c=>c.destroy());charts=[];V.onclick=null;
   runAll(cleanups.splice(0));
   document.querySelectorAll('#nav a').forEach(a=>a.classList.toggle('on',a.dataset.k===k));
   $('#title').textContent=NAV.find(n=>n[0]===k)[1];$('#side').classList.remove('open');
   V.innerHTML='<div class="mu" role="status">Carregando…</div>';
  }
  loading=true;
  try{await pages[k]()}
  catch(error){
   if(token!==navToken)return;
   loading=false;console.error(error);
   if(silent){
    /* mantém a tela anterior; a próxima tentativa vem no ciclo seguinte */
    charts.forEach(c=>c.destroy());charts=stale.charts;cleanups.push(...stale.cleanups);stale=null;
    failed=true;lastLoad=Date.now();showStamp();return}
   V.innerHTML=`<div class="banner" role="alert" style="border-color:var(--cr)">Não foi possível carregar esta tela: ${esc(String(error&&error.message||error).replace(/\.$/,''))}. <button class="btn" id="retry" style="margin-left:8px">Tentar novamente</button></div>`;
   $('#retry').onclick=go;return}
  if(token!==navToken)return;
  loading=false;failed=false;lastLoad=Date.now();
  if(silent){
   flushStale();
   /* tela que não registra handler próprio não pode herdar o da versão anterior */
   if(V.onclick===oldClick)V.onclick=null;
   if(V.onsubmit===oldSubmit&&oldSubmit)V.onsubmit=null;
   window.scrollTo(0,y)}
  else window.scrollTo(0,0);
  showStamp()}
 const busy=()=>document.hidden||loading||Date.now()-lastTouch<IDLE_MS
  ||V.querySelector('form,textarea')||unsaved();
 const tick=()=>{
  if(!staticPages.includes(pageKey())&&lastLoad&&Date.now()-lastLoad>=REFRESH_MS&&!busy())go(true);
  else showStamp()};
 setInterval(tick,5000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)tick()});
 for(const ev of['pointerdown','keydown','input','wheel'])V.addEventListener(ev,()=>{lastTouch=Date.now()},{passive:true});
 window.addEventListener('hashchange',go);
 $('#menu').onclick=()=>$('#side').classList.toggle('open');
 function setTheme(t){document.documentElement.dataset.theme=t;try{localStorage.setItem('eh-theme',t)}catch(e){}}
 let t0='dark';try{t0=localStorage.getItem('eh-theme')||(matchMedia('(prefers-color-scheme: light)').matches?'light':'dark')}catch(e){}
 setTheme(t0);$('#theme').onclick=()=>{setTheme(document.documentElement.dataset.theme==='dark'?'light':'dark');go()};
 go()}
