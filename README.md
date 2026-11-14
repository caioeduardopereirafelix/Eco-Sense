# EcoSense

Plataforma web para **monitorar dispositivos IoT alimentados por energia do ambiente** (*energy harvesting*,
como painéis solares): quanto cada dispositivo gera e consome, quanto resta na bateria e **quanto tempo ela
deve durar**.

> Projeto acadêmico. Os dispositivos cadastrados por padrão usam **canais públicos do ThingSpeak** (sensores
> de laboratório, estações solares e residências reais de vários países) para simular uma rede de sensores
> com dados reais e ao vivo.

## O problema

Sensores espalhados em áreas remotas dependem da energia que conseguem coletar. Sem acompanhamento, só se
descobre que um deles parou quando os dados param de chegar. O EcoSense responde, em tempo real:

- quais dispositivos estão online, com quanta bateria e quanta energia geraram hoje;
- quais **se sustentam sozinhos** e quais vão descarregar, e em quantos dias;
- onde estão os dispositivos em risco e quais pedidos de manutenção estão abertos.

## Funcionalidades

| Área | O que faz |
|---|---|
| **Painel** | Energia gerada, consumida e armazenada, dispositivos online e alertas; filtro por grupo (*Dispositivos IoT* em mWh, *Sistemas de energia* em kWh) |
| **Dispositivos** | Estado, bateria, consumo, coleta, saldo energético e autonomia de cada dispositivo |
| **Previsão** | Autonomia da bateria pelos últimos 7 dias, com grau de confiança e gráficos de consumo e bateria projetados |
| **Geração e consumo** | Participação de cada dispositivo na geração do dia e maiores consumidores |
| **Histórico** | Geração, consumo e armazenamento por hora (hoje) ou por dia (7 dias, 30 dias ou período escolhido) |
| **Manutenção** | Usuário pede visita; administrador agenda e acompanha num mapa de dispositivos por risco |
| **Alertas** | Bateria crítica ou esgotando, consumo acima da média, dispositivo offline, saldo negativo |
| **Cadastro** (admin) | Dispositivos ligados a canais do ThingSpeak ("Buscar canal" lê os campos) e usuários |
| **Login e perfis** | **Administrador** (painel `/admin` completo) e **Usuário** (portal de acompanhamento) |

As telas se atualizam sozinhas a cada 60 s e funcionam no celular.

## Arquitetura

```
Sensores IoT ──▶ ThingSpeak ──▶ Back-end Java (API REST) ──▶ Front-end (navegador)
                                       │
                                       └── MySQL: dispositivos, manutenção e usuários
```

- **Back-end** ([`backend/`](backend/README.md)): Java 21, Spring Boot 3.5, Spring Security (token JWT),
  JPA/Hibernate e MySQL 8. Lê os canais do ThingSpeak, converte as leituras e calcula totais, histórico e
  previsão.
- **Front-end** (raiz): HTML, CSS e JavaScript sem framework, com Chart.js (gráficos) e Leaflet (mapa).
- **Contrato da API**: [`docs/API.md`](docs/API.md).

As leituras não ficam no banco: vêm do ThingSpeak em tempo real. A chave do ThingSpeak fica só no back-end.

## Como rodar

**Pré-requisitos:** JDK 21+, MySQL 8 rodando e internet (para o ThingSpeak e as bibliotecas dos gráficos e
do mapa). O Maven vem junto no projeto.

**1. Configure o back-end.** Copie `backend/.env.example` para `backend/.env` e preencha:

```properties
MYSQL_USER=root
MYSQL_PASSWORD=sua_senha_do_mysql
ECOSENSE_ADMIN_EMAIL=admin@ecosense.local
ECOSENSE_ADMIN_PASSWORD=escolha_uma_senha
JWT_SECRET=um_texto_aleatorio_com_32_ou_mais_caracteres
```

`ECOSENSE_ADMIN_PASSWORD` é a senha do primeiro administrador. Não escreva comentários na mesma linha de um
valor: no `.env`, tudo depois do `=` faz parte do valor, inclusive um `#`.

O banco `ecosense`, as tabelas, os 10 dispositivos de exemplo e o primeiro administrador são **criados
sozinhos** na primeira execução.

**2. Suba o back-end** (a API fica em `http://localhost:8080/api`):

```bash
cd backend
sh mvnw spring-boot:run
```

No Windows: `mvnw.cmd spring-boot:run`. No IntelliJ: rode a classe `EcoSenseApplication`.

**3. Abra o front-end** por um servidor local na raiz do projeto, por exemplo o *Live Server* do VS Code
(porta 5500) ou:

```bash
python -m http.server 5500
```

Acesse `http://localhost:5500/login.html`.

**4. Entre** com o e-mail e a senha de administrador do `.env`. Em **Usuários**, crie um usuário comum
para ver o portal do outro perfil.

Problemas comuns e detalhes (MySQL, chave do ThingSpeak, primeiro acesso, como cadastrar dispositivos) estão
no [README do back-end](backend/README.md).

## Testes

```bash
cd backend
sh mvnw test
```

Os testes usam um ThingSpeak simulado e um banco H2 em memória: não precisam de MySQL nem de internet.

## Estrutura

```
├── index.html, login.html     portal do usuário e login
├── admin/index.html           painel do administrador
├── js/                        telas (user.js, admin.js, views.js), utilidades (shared.js),
│                              login (auth.js) e chamadas à API (services/api.js)
├── css/style.css              estilos (tema claro e escuro)
├── docs/API.md                contrato entre front-end e back-end
└── backend/                   API Java + Spring Boot
    └── src/main/java/br/com/ecosense/
        ├── api/               endpoints REST
        ├── service/           regras: energia, previsão, histórico, manutenção, usuários
        ├── thingspeak/        leitura dos canais do ThingSpeak
        ├── persistence/       entidades e repositórios (MySQL)
        └── security/          login, token e perfis
```
