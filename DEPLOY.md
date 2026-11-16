# Deploy

Duas opções, as duas com MySQL. Nas duas, o back-end Java entrega a API em `/api` e também o front-end, num
endereço só.

- [Oracle Cloud](#oracle-cloud-always-free): gratuito sem prazo, numa máquina virtual (uns 40 minutos).
- [Railway](#railway): mais rápido de configurar (uns 10 minutos), gratuito só no período de teste.

## Oracle Cloud (Always Free)

O `docker-compose.yml` sobe o MySQL e o sistema na mesma máquina virtual.

### 1. Criar a máquina virtual

1. No painel: **Compute** → **Instances** → **Create instance**.
2. **Image**: Canonical **Ubuntu** (22.04 ou 24.04).
3. **Shape**: **Ampere** → `VM.Standard.A1.Flex` com **2 OCPUs e 12 GB** de memória (dentro do Always Free).
   Se aparecer *out of capacity*, tente outro *Availability domain* ou, por último, `VM.Standard.E2.1.Micro`
   (1 GB: faça também o passo de memória extra, abaixo).
4. **SSH keys**: **Generate a key pair for me** → **Save private key** (guarde o arquivo `.key`).
5. **Create** e anote o **Public IP address** da instância.

### 2. Liberar a porta 80 na Oracle

Na página da instância: clique na **Subnet** → **Security Lists** → **Default Security List** →
**Add Ingress Rules**: *Source CIDR* `0.0.0.0/0`, *IP Protocol* `TCP`, *Destination Port Range* `80` → **Add**.

### 3. Entrar na máquina

No terminal (no Windows, o PowerShell já tem `ssh`):

```bash
ssh -i caminho/da/chave.key ubuntu@IP_DA_INSTANCIA
```

### 4. Instalar e subir (dentro da máquina)

```bash
curl -fsSL https://get.docker.com | sudo sh
```

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT && sudo netfilter-persistent save
```

```bash
git clone -b remoteprogramming https://github.com/caioeduardopereirafelix/Eco-Sense.git && cd Eco-Sense
```

Se o repositório for privado, o `git` pede o usuário e um *token* do GitHub (GitHub → Settings → Developer
settings → Personal access tokens) no lugar da senha.

Crie o `.env` com as senhas (troque os valores; ele não vai para o Git):

```bash
cat > .env <<'EOF'
DB_PASSWORD=troque-pela-senha-do-banco
ADMIN_PASSWORD=troque-pela-senha-do-admin
JWT_SECRET=troque-por-um-texto-aleatorio-com-32-ou-mais-caracteres
EOF
```

Suba tudo (a primeira vez compila o projeto e leva alguns minutos):

```bash
sudo docker compose up -d --build
```

Acompanhe até aparecer `Leituras carregadas em N s` (Ctrl+C sai do log, o sistema continua rodando):

```bash
sudo docker compose logs -f app
```

Abra `http://IP_DA_INSTANCIA/login.html` e entre com `admin@ecosense.local` e o `ADMIN_PASSWORD`.

**Só na `E2.1.Micro` (1 GB):** antes do `docker compose up`, crie memória extra:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

### Depois

- Atualizar para a versão mais nova do GitHub: `git pull` e `sudo docker compose up -d --build`.
- O sistema e o MySQL religam sozinhos se a máquina reiniciar. Os dados ficam num volume do Docker.
- O endereço é `http://` (sem cadeado). Para `https://`, é preciso um domínio e um certificado.

## Railway

Um serviço para o sistema e um MySQL da própria Railway.

### 1. Criar o projeto

1. Entre em [railway.com](https://railway.com) com a sua conta do GitHub.
2. **New Project** → **Deploy from GitHub repo** → escolha **Eco-Sense**.
   Se o repositório não aparecer, clique em **Configure GitHub App** e libere o acesso a ele.
3. No serviço criado, abra **Settings** → **Source** e confira a **Branch** (`main`, ou a branch com este
   arquivo). A Railway encontra o `Dockerfile` na raiz e constrói a imagem sozinha.

### 2. Adicionar o MySQL

No mesmo projeto: **+ Create** (ou **+ New**) → **Database** → **MySQL**. Deixe o nome do serviço como
**MySQL**: as variáveis abaixo usam esse nome.

### 3. Variáveis do back-end

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

### 4. Gerar o endereço público

No serviço do back-end → **Settings** → **Networking** → **Generate Domain**. Se pedir a porta, use **8080**.

### 5. Conferir

1. Em **Deployments** → **View Logs**, espere aparecer `Leituras carregadas em N s`.
2. Abra `https://<seu-endereço>.up.railway.app/login.html` e entre com o e-mail e a senha de
   administrador das variáveis.

Na primeira vez, o sistema cria sozinho as tabelas, os 10 dispositivos de exemplo e o primeiro administrador.

### Problemas comuns na Railway

| Sintoma | O que fazer |
|---|---|
| Log com `Access denied` ou `Communications link failure` | Confira se o serviço do banco se chama **MySQL** e se as variáveis do passo 3 foram salvas no serviço do back-end |
| Página abre, mas o login diz que não falou com o servidor | Abra o site pelo endereço da Railway (não pelo `localhost`) |
| Senha do administrador não entra | `ECOSENSE_ADMIN_PASSWORD` só vale na criação do primeiro administrador. Se o banco já tinha usuários, use a senha antiga |
