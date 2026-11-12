/* ===== TELA DE LOGIN ===== */
(()=>{
 /* tema salvo pelos portais */
 try{document.documentElement.dataset.theme=localStorage.getItem('eh-theme')||(matchMedia('(prefers-color-scheme: light)').matches?'light':'dark')}catch(e){document.documentElement.dataset.theme='dark'}
 const next=auth.safeNext(new URLSearchParams(location.search).get('next')||'');
 /* usuário comum não volta para o painel admin */
 const target=s=>next&&!(next.includes('/admin/')&&!auth.isAdmin(s))?next:auth.home(s);
 const current=auth.get();
 if(current){location.replace(target(current));return}

 const form=document.getElementById('login'),err=document.getElementById('err'),btn=document.getElementById('go');
 document.getElementById('email').focus();
 form.onsubmit=async e=>{
  e.preventDefault();
  const email=form.email.value.trim(),password=form.password.value;
  if(!email||!password){err.textContent='Informe o e-mail e a senha.';return}
  err.textContent='';btn.disabled=true;btn.textContent='Entrando…';
  try{
   const s=await api.login(email,password);
   auth.save(s);
   location.replace(target(s));
  }catch(error){
   err.textContent=error.message;btn.disabled=false;btn.textContent='Entrar';
   form.password.select();
  }
 };
})();
