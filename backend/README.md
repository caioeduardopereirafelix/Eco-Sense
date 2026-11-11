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

## Rodar com o canal real (perfil `thingspeak`)

O perfil [`application-thingspeak.yml`](src/main/resources/application-thingspeak.yml) já aponta para o
canal **3574018** ("EcoSense - Sensor 01": field1 bateria %, field2 consumo W, field3 temperatura °C,
field4 energia coletada W) e desliga a simulação.

```powershell
# Windows (PowerShell) — copie a Read API Key da aba "API Keys" do canal
$env:THINGSPEAK_READ_API_KEY='SUA_READ_API_KEY'
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
Depois de ~1 min (cache), `GET /api/devices` mostra o Sensor 01 Online com esses valores.

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
