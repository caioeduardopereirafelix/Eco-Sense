# Deploy na Railway (MySQL)

Um serviço só: o back-end Java entrega a API em `/api` e também o front-end. O banco é um MySQL da própria
Railway. Leva uns 10 minutos.

## 1. Criar o projeto

1. Entre em [railway.com](https://railway.com) com a sua conta do GitHub.
2. **New Project** → **Deploy from GitHub repo** → escolha **Eco-Sense**.
   Se o repositório não aparecer, clique em **Configure GitHub App** e libere o acesso a ele.
3. No serviço criado, abra **Settings** → **Source** e confira a **Branch** (`main`, ou a branch com este
   arquivo). A Railway encontra o `Dockerfile` na raiz e constrói a imagem sozinha.

## 2. Adicionar o MySQL

No mesmo projeto: **+ Create** (ou **+ New**) → **Database** → **MySQL**. Deixe o nome do serviço como
**MySQL**: as variáveis abaixo usam esse nome.

## 3. Variáveis do back-end

No serviço do back-end (o do GitHub) → **Variables** → **Raw Editor**, cole e troque o que está entre `< >`:

```
PORT=8080
MYSQL_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
MYSQL_USER=${{MySQL.MYSQLUSER}}
MYSQL_PASSWORD=${{MySQL.MYSQLPASSWORD}}
ECOSENSE_ADMIN_EMAIL=admin@ecosense.local
ECOSENSE_ADMIN_PASSWORD=<senha do administrador, 8 caracteres ou mais>
JWT_SECRET=<um texto aleatório com 32 caracteres ou mais>
```

`THINGSPEAK_READ_API_KEY` só é necessária para canais privados.

Salve: a Railway faz o deploy de novo com as variáveis.

## 4. Gerar o endereço público

No serviço do back-end → **Settings** → **Networking** → **Generate Domain**. Se pedir a porta, use **8080**.

## 5. Conferir

1. Em **Deployments** → **View Logs**, espere aparecer `Leituras carregadas em N s`.
2. Abra `https://<seu-endereço>.up.railway.app/login.html` e entre com o e-mail e a senha de
   administrador das variáveis.

Na primeira vez, o sistema cria sozinho as tabelas, os 10 dispositivos de exemplo e o primeiro administrador.

## Problemas comuns

| Sintoma | O que fazer |
|---|---|
| Log com `Access denied` ou `Communications link failure` | Confira se o serviço do banco se chama **MySQL** e se as variáveis do passo 3 foram salvas no serviço do back-end |
| Página abre, mas o login diz que não falou com o servidor | Abra o site pelo endereço da Railway (não pelo `localhost`) |
| Senha do administrador não entra | `ECOSENSE_ADMIN_PASSWORD` só vale na criação do primeiro administrador. Se o banco já tinha usuários, use a senha antiga |
