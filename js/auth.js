const auth=(()=>{
 const KEY='eh-session';
 const ROOT=new URL('..',document.currentScript.src);
 const url=path=>new URL(path,ROOT).href;
 const get=()=>{
  try{const s=JSON.parse(localStorage.getItem(KEY)||'null');
   if(s&&s.token&&Date.parse(s.expiresAt)>Date.now())return s}catch(e){}
  return null};
 const save=s=>{try{localStorage.setItem(KEY,JSON.stringify(s))}catch(e){}};
 const clear=()=>{try{localStorage.removeItem(KEY)}catch(e){}};
 const isAdmin=s=>!!s&&s.user.role==='ADMIN';
 const home=s=>isAdmin(s)?url('admin/'):url('index.html');
 const safeNext=next=>{
  if(!next)return null;
  try{const u=new URL(next,location.href);
   return u.origin===location.origin&&!u.pathname.endsWith('/login.html')?u.href:null}catch(e){return null}};
 const loginUrl=next=>url('login.html')+(next?`?next=${encodeURIComponent(next)}`:'');
 return{
  get,save,clear,isAdmin,home,safeNext,
  roleLabel:r=>r==='ADMIN'?'Administrador':'Usuário',
  toLogin(){clear();location.replace(loginUrl(location.href))},
  logout(){clear();location.replace(loginUrl())},
  require(role){
   const s=get();
   if(!s){this.toLogin();return null}
   if(role==='ADMIN'&&!isAdmin(s)){location.replace(home(s));return null}
   return s},
  setUser(user){const s=get();if(s){s.user=user;save(s)}}
 };
})();
