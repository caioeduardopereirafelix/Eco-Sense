# EcoSense — back-end

API em Java 21 + Spring Boot 3.5 que lê os canais do **ThingSpeak**, guarda o cadastro no **MySQL** e
entrega os dados ao front-end no formato de [`docs/API.md`](../docs/API.md).

```
Sensores ──▶ ThingSpeak ──▶ este back-end (/api/...) ──▶ front-end (index.html, admin/)
                                   │
                                   └── MySQL: cadastro de dispositivos e manutenção
```

## Rodar

Pré-requisitos: **JDK 21+** e **MySQL 8** rodando. O Maven vem junto (`mvnw`).

1. Copie [`.env.example`](.env.example) para `backend/.env` e preencha (o `.env` não vai para o git):

   ```properties
   THINGSPEAK_READ_API_KEY=
   MYSQL_URL=jdbc:mysql://localhost:3306/ecosense?createDatabaseIfNotExist=true&serverTimezone=UTC
   MYSQL_USER=root
   MYSQL_PASSWORD=sua_senha
   ```

   `THINGSPEAK_READ_API_KEY` só é necessária para canais privados (vazia = só canais públicos). Não escreva
   comentários na mesma linha de um valor: no `.env`, tudo depois do `=` faz parte do valor, inclusive um `#`.

2. Suba o back-end:

   ```bash
   cd backend
   sh mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```

   No IntelliJ: rode a classe `EcoSenseApplication` (sem argumentos). O *Working directory* deve ser a
   pasta do projeto ou `backend/`, para o `.env` ser encontrado.

3. A API sobe em `http://localhost:8080/api` (ex.: http://localhost:8080/api/devices). O front-end já
   aponta para esse endereço em [`js/config.js`](../js/config.js); abra-o por um servidor local (Live
   Server do VS Code na porta 5500, `npx serve -l 5173` etc.). As origens liberadas no CORS ficam em
   `ecosense.cors-origins`.

Ao iniciar, o log mostra `Read API Key configurada (N caracteres)` (ou `NÃO configurada`) e, na
primeira consulta com o banco vazio, `Banco vazio: 10 dispositivo(s) iniciais copiados da configuração`.
Se aparecer `Access denied for user`, confira `MYSQL_USER`/`MYSQL_PASSWORD`.

## Login e perfis

A API exige login (Spring Security + token JWT assinado com HMAC-SHA256). Perfis:

- **ADMIN**: painel `/admin` (cadastro de dispositivos, manutenção, **Usuários**) e portal do usuário;
- **USER**: só o portal do usuário; vê apenas os próprios pedidos de manutenção.

**Primeiro acesso.** Com a tabela `app_users` vazia, o back-end cria um administrador:

| Variável no `.env` | Padrão |
|---|---|
| `ECOSENSE_ADMIN_EMAIL` | `admin@ecosense.local` |
| `ECOSENSE_ADMIN_PASSWORD` | vazio → o back-end gera uma senha e a mostra **uma única vez** no log de inicialização |
| `JWT_SECRET` | vazio → chave aleatória: todos precisam entrar de novo quando o back-end reinicia. Use 32+ caracteres. |

Entre em `login.html` com esse e-mail, troque a senha em **Configurações → Alterar senha** e crie os
outros usuários em **Usuários**. Esqueceu a senha do único administrador? Apague a linha dele na
tabela `app_users` (com a tabela vazia, o administrador inicial é criado de novo).

Segurança: senhas guardadas só como hash BCrypt; o token leva o id, o perfil e uma versão que muda
quando a senha é trocada ou redefinida (as sessões antigas caem na hora); perfil e existência do
usuário são conferidos no banco a cada requisição. Para um sistema em produção, sirva o front e a API
por HTTPS.

## Banco de dados (MySQL)

O **cadastro dos dispositivos** (nome, local, canal do ThingSpeak, fields, conversões) e as
**solicitações de manutenção** ficam no banco (tabelas `devices` e `maintenance_requests`), assim como os **usuários** (`app_users`). As
**leituras** não: vêm do ThingSpeak em tempo real.

- O banco `ecosense` e as tabelas são **criados sozinhos** na primeira execução (Hibernate `ddl-auto: update`).
- Com a tabela `devices` vazia, os dispositivos de `ecosense.devices` no
  [`application.yml`](src/main/resources/application.yml) são **copiados para o banco uma única vez** (é o
  que preenche o banco de quem clona o projeto). Depois disso, adicione, edite e remova pelo painel
  **admin → Dispositivos IoT** ("+ Novo dispositivo" e "Buscar canal", que lê o nome dos fields no
  ThingSpeak). Para voltar ao cadastro inicial, apague as linhas da tabela `devices`.
- A Read API Key **não** é guardada no banco (fica no `.env`).
- As baterias do sistema (`ecosense.storage`) ficam no `application.yml`.

## Fontes dos dados

Os dispositivos iniciais são **canais públicos** do ThingSpeak (não precisam de chave):

| No EcoSense | Canal | Origem | Leituras usadas |
|---|---|---|---|
| Dispositivo IOT-001 | [2988514](https://thingspeak.mathworks.com/channels/2988514) "[Harveo] Indoor tests @ INA3221" | Projeto EMBEDO Harveo — coletor de energia com mini painel solar indoor, Gdańsk (PL) | bateria (V → %), potência da bateria (mW → consumo W), potência do painel (mW → coleta W) |
| Dispositivo IOT-002 | [3124603](https://thingspeak.mathworks.com/channels/3124603) "EMBEDO Node" | Mesmo laboratório | carga do UPS (%), temperatura (°C) |
| Dispositivo IOT-003 | [1279978](https://thingspeak.mathworks.com/channels/1279978) "Elektrownia solarna" | Usina solar pequena com bateria 12 V, Polônia | bateria (V → %), corrente das cargas e de carga solar (A → W, × 12,8 V), temperatura |
| Dispositivo IOT-004 | [181889](https://thingspeak.mathworks.com/channels/181889) "Solar Weather Station (Outside2-17)" | Estação meteorológica solar (ESP8266 + INA219), Yokohama (JP) | bateria (V → %), corrente da bateria (mA → W, × 3,8 V; + descarga, − carga), temperatura |
| Dispositivo IOT-005 | [107067](https://thingspeak.mathworks.com/channels/107067) "PV-Anlage EFH Merlischachen" | Casa com 14,52 kWp de painéis e bateria, Suíça | carga da bateria (%), consumo da casa (W), produção solar (W) |
| Dispositivo IOT-006 | [432856](https://thingspeak.mathworks.com/channels/432856) "Solar Array" | Banco de baterias com 2 controladores solares, Texas (EUA) | bateria (V → %), descarga (A → W, × 13,2 V), controlador 1 (A → W) |
| Dispositivo IOT-007 | [285868](https://thingspeak.mathworks.com/channels/285868) "PowerStationData" | Fonte de energia solar com bateria, Nebraska (EUA) | bateria (V → %), corrente da bateria (A → W; − descarga, + carga), temperatura do gabinete |
| Armazenamento | [1276112](https://thingspeak.mathworks.com/channels/1276112) "Solarakku" | Banco de baterias solar LiFePO4 24 V / 320 Ah (~8 kWh), Hoyerswerda (DE) | tensão da célula 1 (V → %) |

**Exemplos offline** — canais públicos que pararam de transmitir, incluídos de propósito para mostrar o
comportamento do sistema (status Offline, "última comunicação há N meses/anos", alerta, sem previsão):

| No EcoSense | Canal | Origem | Parou em |
|---|---|---|---|
| Dispositivo IOT-008 | [3124605](https://thingspeak.mathworks.com/channels/3124605) "[Harveo][DFRobot] Indoor tests" | Coletor DFRobot SPV1050 do experimento comparativo EMBEDO, Gdańsk (PL) | 07/2026 |
| Dispositivo IOT-009 | [1085833](https://thingspeak.mathworks.com/channels/1085833) "Volta" | Bateria e painel de estação meteorológica, Itália | 03/2021 (bateria em 10,8 V) |
| Dispositivo IOT-010 | [33748](https://thingspeak.mathworks.com/channels/33748) "SolarWiFiRepeater" | Repetidor Wi-Fi solar, Nova Zelândia | 07/2017 (bateria em 10,7 V) |

Os dados são de terceiros: **cite os canais como fonte** no trabalho acadêmico. Se um canal ativo parar
de transmitir, ele passa a se comportar como esses exemplos; nada quebra.

**Aproximações a citar:** onde o canal mede só corrente, a potência é corrente × tensão nominal da
bateria; a carga (%) vem da tensão por uma faixa linear típica do tipo de bateria inferido dos dados
(Li-ion 3,0–4,2 V; LiFePO4 12 V 12,0–13,6 V). Os sistemas misturam escalas de mW (Harveo, Yokohama) a
kW (casa na Suíça), então os totais da visão geral são dominados pelos sistemas grandes.

O mapa da Manutenção usa tiles do **OpenStreetMap**, que exigem que o front seja aberto por um servidor
local (Live Server etc.), não como arquivo.

O Harveo trabalha com **miliwatts** (painel ~0,7 mW de dia, consumo ~0,1 mW), escala típica de
*energy harvesting*. Por isso a API devolve potência e energia com 4 algarismos significativos (sem
arredondar para 0) e o front escolhe a unidade (µW/mW/W, µWh/mWh/Wh/kWh) conforme o valor.

**Conversões** (`conversion` no YAML): `battery-voltage-min/max` lê a bateria em volts e converte
linearmente para %; `consumption-scale`/`harvested-scale` multiplicam as potências (ex.: `0.001` para
mW → W; negativo inverte o sinal). Use `none` em `fields` para uma leitura que o canal não tem.

**Canais que enviam a cada poucos segundos:** o ThingSpeak lê no máximo 8000 entradas por pedido antes de
calcular as médias. `history-chunk-days` divide a primeira carga do histórico em pedaços (ex.: `2` para um
canal de 30 s); depois o back-end busca só as horas novas.

### Canal próprio (privado)

Canais privados (como o 3574018 criado para o projeto) precisam da Read API Key em
`THINGSPEAK_READ_API_KEY` no `.env` (aba "API Keys" do canal → "Read API Keys"). Sem ela o ThingSpeak
responde `-1`: a API continua no ar, o dispositivo aparece Offline e o log avisa.

**Testar sem a placa:** envie uma leitura pelo navegador com a *Write API Key*:

```
https://api.thingspeak.com/update?api_key=SUA_WRITE_API_KEY&field1=87&field2=0.5&field3=24&field4=0.6
```

A resposta é o número da entrada (ou `0` se enviar de novo antes de 15 s, limite do plano gratuito).
Depois de ~1 min (cache), o dispositivo cadastrado com esse canal aparece Online com esses valores.

## Testes

```bash
sh mvnw test
```

Os testes **não** usam internet nem MySQL: o perfil `test`
([`src/test/resources/application-test.yml`](src/test/resources/application-test.yml)) liga um banco H2
em memória e o `SimulatedThingSpeakClient` (só em `src/test`), que gera leituras previsíveis no formato
do ThingSpeak para 10 dispositivos de teste.

## Como os dados são calculados

| O quê | Como |
|---|---|
| Leituras | Por dispositivo: última entrada (`results=1`) + médias por hora dos últimos 31 dias (`average=60`). Cache de 60 s (`cache-ttl`) para respeitar os limites do ThingSpeak. Se o ThingSpeak falhar, usa o último resultado em cache. |
| Online/Offline | Online se a última entrada for mais recente que `offline-after` (30 min) ou 3× o `tx-interval`, o que for maior. |
| Consumo e coleta (Wh/dia) | Média de potência das últimas 24 h × 24. |
| Energia (kWh) | Cada média horária (W) × 1 h, somadas no período. |
| Previsão de autonomia | Tendência linear da bateria nos últimos 7 dias + saldo energético (coleta − consumo) no mesmo período. Queda → dias até zerar; estável/subindo com saldo ≥ 0 → sustentável; estável/subindo com saldo < 0 → "saldo negativo" (sem número de dias, pois a capacidade em Wh não é conhecida). A confiança cai com oscilação da bateria, falta de dados e quando bateria e saldo se contradizem. |
| Alertas | Offline, bateria < 20 %, esgotando em ≤ 3 dias, consumo > 1,5× a média de 7 dias, geração < 70 % da de ontem. |
| Cadastro de dispositivos | Banco (tabela `devices`), com cópia em memória recarregada a cada alteração. |
| Manutenção | Banco (tabela `maintenance_requests`), com quem pediu. |
| Usuários | Banco (tabela `app_users`), senha em hash BCrypt. |

## Estrutura

```
src/main/java/br/com/ecosense/
├── config/       EcoSenseProperties e AuthProperties (application.yml), CORS, Clock
├── security/     SecurityConfig (regras por perfil), TokenService (JWT), AuthUser
├── persistence/  Entidades JPA e repositórios: DeviceEntity, MaintenanceRequestEntity, UserEntity
├── thingspeak/   ThingSpeakClient e HttpThingSpeakClient (API REST do ThingSpeak)
├── service/      DeviceCatalog (cadastro no banco), ReadingService (cache), DeviceService,
│                 ForecastService, HistoryService, EnergyService (visão geral, fontes, cargas,
│                 baterias, alertas), MaintenanceService, UserService (login, conta, usuários)
└── api/          ApiController (/api/...), DeviceAdminController (cadastro), AuthController
                  (login e conta), UserAdminController (usuários), ApiDtos, erros

src/test/
├── java/.../thingspeak/SimulatedThingSpeakClient.java   leituras simuladas (só testes)
└── resources/application-test.yml                        H2 + dispositivos de teste
```
