# EcoSense — back-end

API em Java 21 + Spring Boot 3.5 que lê os canais do **ThingSpeak** e entrega os dados ao
front-end no formato de [`docs/API.md`](../docs/API.md).

```
Sensores ──▶ ThingSpeak ──▶ este back-end (/api/...) ──▶ front-end (index.html, admin/)
```

## Rodar

Pré-requisito: JDK 21 ou mais novo. O Maven vem junto (`mvnw`).

```bash
cd backend
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

A API sobe em `http://localhost:8080/api` (ex.: http://localhost:8080/api/devices).
Por padrão roda em **modo simulado**: gera leituras parecidas com as do ThingSpeak para os 10
dispositivos do `application.yml`, sem precisar de canais reais.

Para o front-end usar o back-end, edite [`js/config.js`](../js/config.js):

```js
window.ECOSENSE_API_URL='http://localhost:8080/api';
```

e abra o front por um servidor local (Live Server do VS Code na porta 5500, `npx serve -l 5173` etc.).
As origens liberadas no CORS ficam em `ecosense.cors-origins`.

Testes: `./mvnw test`

## Rodar com dados reais (perfil `thingspeak`)

O perfil [`application-thingspeak.yml`](src/main/resources/application-thingspeak.yml) desliga a simulação e
lê **canais públicos** do ThingSpeak com dados reais e ativos (não precisam de chave):

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

Os dados são de terceiros: **cite os canais como fonte** no trabalho acadêmico. Se um canal parar de
transmitir, o dispositivo aparece Offline e gera alerta; nada quebra.

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

Para usar um canal privado, como o 3574018 criado para o projeto (exemplo comentado no perfil), é preciso
a Read API Key. **Onde colocar a Read API Key** (aba "API Keys" do canal → "Read API Keys") — escolha uma:

- **Arquivo `backend/.env`** (recomendado): copie [`.env.example`](.env.example) para `.env` e preencha
  `THINGSPEAK_READ_API_KEY=...`. O back-end lê esse arquivo ao iniciar (ou um `.env` na pasta de onde
  ele foi iniciado) e o `.gitignore` impede que ele vá para o GitHub.
- **Variável de ambiente** no IntelliJ (Run Configuration → Environment variables) ou no terminal.
  Variáveis de ambiente têm prioridade sobre o `.env`.

Ao iniciar, o log confirma: `Read API Key configurada (N caracteres)` ou `NÃO configurada`.

```powershell
# Windows (PowerShell), com a chave no backend/.env
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=thingspeak"
```

```bash
# Linux/macOS
export THINGSPEAK_READ_API_KEY=SUA_READ_API_KEY
./mvnw spring-boot:run -Dspring-boot.run.profiles=thingspeak
```

Sem a chave (canal privado), o ThingSpeak responde `-1`: a API continua no ar, o dispositivo aparece
Offline e o log avisa para definir `THINGSPEAK_READ_API_KEY`.

**Testar sem a placa:** envie uma leitura pelo navegador com a *Write API Key* (aba "API Keys"):

```
https://api.thingspeak.com/update?api_key=SUA_WRITE_API_KEY&field1=87&field2=0.5&field3=24&field4=0.6
```

A resposta é o número da entrada (ou `0` se enviar de novo antes de 15 s, limite do plano gratuito).
Depois de ~1 min (cache), `GET /api/devices` mostra o Sensor 01 Online com esses valores
(com o dispositivo IOT-003 descomentado no perfil).

## Conectar outros canais ao ThingSpeak

1. Em [`src/main/resources/application.yml`](src/main/resources/application.yml) (ou no perfil `thingspeak`), para cada dispositivo
   em `ecosense.devices`, coloque o `channel-id` real e diga em qual field está cada leitura:

   ```yaml
   - id: IOT-001
     name: Sensor Ambiental 01
     channel-id: 1234567            # ID do canal no ThingSpeak
     fields: { battery: field1, consumption: field2, temperature: field3, harvested: field4 }
   ```

   Unidades esperadas nos fields: **battery em %**, **consumption em W**, **temperature em °C**,
   **harvested em W** (potência coletada). Se o seu canal usar outra unidade (mW, por exemplo),
   converta no firmware ou avise para adicionarmos um fator de conversão.

2. Defina as variáveis de ambiente e desligue a simulação:

   ```bash
   export THINGSPEAK_SIMULATE=false
   export THINGSPEAK_READ_API_KEY=SUA_READ_API_KEY   # omita para canais públicos
   ```

   No Windows (PowerShell): `$env:THINGSPEAK_SIMULATE='false'; $env:THINGSPEAK_READ_API_KEY='...'`.
   Canais com chaves diferentes: use `read-api-key: ${THINGSPEAK_KEY_IOT001:}` no dispositivo.
   **Nunca** coloque a chave no `application.yml` nem no front-end.

3. Opcional: em `ecosense.storage`, cadastre as baterias do sistema (canal + field com o % de carga).
   Sem elas, o "armazenamento" da visão geral é a média das baterias dos dispositivos.

## Como os dados são calculados

| O quê | Como |
|---|---|
| Leituras | Por dispositivo: última entrada (`results=1`) + médias por hora dos últimos 31 dias (`average=60`). Cache de 60 s (`cache-ttl`) para respeitar os limites do ThingSpeak. Se o ThingSpeak falhar, usa o último resultado em cache. |
| Online/Offline | Online se a última entrada for mais recente que `offline-after` (30 min) ou 3× o `tx-interval`, o que for maior. |
| Consumo e coleta (Wh/dia) | Média de potência das últimas 24 h × 24. |
| Energia (kWh) | Cada média horária (W) × 1 h, somadas no período. |
| Previsão de autonomia | Tendência linear da bateria nas últimas 72 h. Queda → dias até zerar; estável/subindo → sustentável. A confiança cai com ruído e falta de dados. |
| Alertas | Offline, bateria < 20 %, esgotando em ≤ 3 dias, consumo > 1,5× a média de 7 dias, geração < 70 % da de ontem. |
| Manutenção | Em memória (some ao reiniciar). Próximo passo: banco de dados (Spring Data JPA). |

## Estrutura

```
src/main/java/br/com/ecosense/
├── config/       EcoSenseProperties (application.yml), CORS, Clock
├── thingspeak/   ThingSpeakClient: HttpThingSpeakClient (real) e SimulatedThingSpeakClient
├── service/      ReadingService (cache), DeviceService, ForecastService, HistoryService,
│                 EnergyService (visão geral, fontes, cargas, baterias, alertas), MaintenanceService
└── api/          ApiController (/api/...), ApiDtos (formatos de resposta), tratamento de erros
```
