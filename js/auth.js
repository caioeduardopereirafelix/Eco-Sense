/* ===== SESSÃO DO USUÁRIO (login) =====
   O back-end devolve um token no login; ele fica guardado neste navegador e vai em toda chamada
   à API (Authorization: Bearer …). Sem sessão válida, as telas mandam para login.html.
   Perfis: ADMIN (painel /admin e portal do usuário) e USER (só o portal do usuário). */
const auth=(()=>{
 const KEY='eh-session';
 /* raiz do site, a partir do endereço deste arquivo (js/auth.js → ../) */
 const ROOT=new URL('..',document.currentScript.src);
 const url=path=>new URL(path,ROOT).href;
 const get=()=>{
  try{const s=JSON.parse(localStorage.getItem(KEY)||'null');
   if(s&&s.token&&Date.parse(s.expiresAt)>Date.now())return s}catch(e){}
  return null};
 const save=s=>{try{localStorage.setItem(KEY,JSON.stringify(s))}catch(e){}};
 const clear=()=>{try{localStorage.removeItem(KEY)}catch(e){}};
 const isAdmin=s=>!!s&&s.user.role==='ADMIN';
 /* portal inicial de cada perfil */
 const home=s=>isAdmin(s)?url('admin/'):url('index.html');
 /* só aceita voltar para páginas deste mesmo site (evita redirecionamento para fora) */
 const safeNext=next=>{
  if(!next)return null;
  try{const u=new URL(next,location.href);
   /* nunca volta para a própria tela de login (evita laço de redirecionamento) */
   return u.origin===location.origin&&!u.pathname.endsWith('/login.html')?u.href:null}catch(e){return null}};
 const loginUrl=next=>url('login.html')+(next?`?next=${encodeURIComponent(next)}`:'');
 return{
  get,save,clear,isAdmin,home,safeNext,
  roleLabel:r=>r==='ADMIN'?'Administrador':'Usuário',
  /* sessão expirada ou recusada pela API: volta para o login e depois retorna a esta página */
  toLogin(){clear();location.replace(loginUrl(location.href))},
  logout(){clear();location.replace(loginUrl())},
  /* Exige login (e, com role 'ADMIN', perfil de administrador). Devolve a sessão ou null (redirecionando). */
  require(role){
   const s=get();
   if(!s){this.toLogin();return null}
   if(role==='ADMIN'&&!isAdmin(s)){location.replace(home(s));return null}
   return s},
  /* atualiza o nome guardado na sessão (após editar o perfil) */
  setUser(user){const s=get();if(s){s.user=user;save(s)}}
 };
})();
