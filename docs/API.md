# Contrato da API — EcoSense

Formato que o back-end ([`backend/`](../backend/README.md), Java + Spring Boot) devolve ao front-end.
O front-end chama estes endpoints pelo [`js/services/api.js`](../js/services/api.js); o endereço do back-end
fica em [`js/config.js`](../js/config.js) (padrão `http://localhost:8080/api`).

## Arquitetura

```
Sensores IoT ──▶ ThingSpeak (canais/fields) ──▶ Back-end Java ──▶ Front-end (este repositório)
```

- O front-end **nunca** chama o ThingSpeak direto: a *Read API Key* fica só no back-end.
- O back-end lê os canais do ThingSpeak, converte `field1..field8` nos campos com nome abaixo,
  calcula agregados (histórico, totais, previsão) e expõe os endpoints REST deste documento.
- O `created_at` dos *feeds* do ThingSpeak já é ISO 8601 em UTC e pode ser repassado direto
  (`lastSeenAt`, `timestamps`…).
- Os *fields* do ThingSpeak chegam como **texto** (`"23.5"`); o back-end deve convertê-los para número.

## Regras gerais

| Regra | Exemplo |
|---|---|
| Números puros, nunca texto formatado | `"energyKwh": 3.4` (não `"3,40 kWh"`) |
| Unidade no nome do campo | `currentW`, `capacityKwh`, `efficiencyPct` |
| Potência e energia com 4 algarismos significativos (nunca arredondar para 0) | `"nowW": 0.000125`, `"todayKwh": 4.758E-6` |
| Datas em ISO 8601 com fuso | `"2026-11-10T14:05:00Z"` |
| Datas sem hora (agendamento) | `"2026-11-21"` |
| Valor ausente | `null` (não omitir o campo, não usar `""`) |
| Erros | HTTP 4xx/5xx com `{"message": "texto para o usuário"}` |
| Autenticação | `Authorization: Bearer <token>` em toda chamada, exceto o login |

Status exibidos como tag usam estes valores (texto em português, também usados como classe CSS):
`Normal`, `Atenção`, `Crítico`, `Carregando`, `Online`, `Offline`.

## Login e perfis

Dois perfis: **ADMIN** (painel `/admin` e portal do usuário) e **USER** (só o portal do usuário).
O front guarda a sessão (js/auth.js) e envia o token em toda chamada. Respostas de acesso:

| Situação | Resposta |
|---|---|
| Sem token, token inválido/expirado, senha trocada ou usuário removido | `401 {"message": "Faça login para continuar."}` → o front volta para `login.html` |
| USER chamando rota de administrador | `403 {"message": "Acesso restrito a administradores."}` |

**Só ADMIN:** `/api/users/**`, `/api/thingspeak/**`, `GET /api/devices/{id}/settings`,
`POST /api/devices`, `PUT`/`DELETE /api/devices/{id}` e `PATCH /api/maintenance-requests/{id}`.
Todo o resto de `/api/**` vale para qualquer usuário logado.

`POST /api/auth/login` (público) → `api.login(email, password)`
```json
// corpo
{ "email": "ana@exemplo.com", "password": "…" }
// resposta
{ "token": "eyJhbGciOiJIUzI1NiJ9…", "expiresAt": "2026-11-12T22:00:00Z",
  "user": { "id": 1, "name": "Ana", "email": "ana@exemplo.com", "role": "ADMIN", "createdAt": "2026-11-12T14:00:00Z" } }
```
E-mail ou senha errados: `401 {"message": "E-mail ou senha incorretos."}`. O token vale 8 h.

**Minha conta** (qualquer perfil)

| Método | Rota | Front | Corpo / resposta |
|---|---|---|---|
| GET | `/api/me` | `api.getMe()` | o `user` acima |
| PUT | `/api/me` | `api.updateMe(name)` | `{ "name": "…" }` → `user` |
| PUT | `/api/me/password` | `api.changePassword(atual, nova)` | `{ "currentPassword", "newPassword" }` (8–72 caracteres) → sessão nova (mesmo formato do login). Encerra as outras sessões. |

**Usuários** (só ADMIN)

| Método | Rota | Front | Corpo / resposta |
|---|---|---|---|
| GET | `/api/users` | `api.getUsers()` | lista de `user` |
| POST | `/api/users` | `api.createUser(u)` | `{ "name", "email", "role": "ADMIN"|"USER", "password" }` → 201 `user` (e-mail repetido: 409) |
| PUT | `/api/users/{id}` | `api.updateUser(id, u)` | `{ "name", "email", "role" }` → `user` |
| PUT | `/api/users/{id}/password` | `api.resetUserPassword(id, senha)` | `{ "password" }` → 204; encerra as sessões do usuário |
| DELETE | `/api/users/{id}` | `api.deleteUser(id)` | 204 |

O administrador não pode remover a si mesmo nem mudar o próprio perfil (assim sempre resta um ADMIN).

## Endpoints

Todos implementados em `backend/` (`ApiController`). Exigem login (ver acima).

### `GET /api/overview` → `api.getOverview()`
```json
{
  "generation":  { "todayKwh": 4.82, "changePct": 8.4 },
  "consumption": { "nowW": 312, "todayKwh": 3.91, "changePct": -2.1 },
  "storage":     { "pct": 68, "storedKwh": 6.8, "capacityKwh": 10, "status": "Normal" },
  "devices":     { "total": 10, "online": 8, "offline": 2 }
}
```
`changePct` = variação em relação a ontem no mesmo horário (negativo = queda; `null` sem dados de ontem).
`storage`: com baterias do sistema cadastradas, carga ponderada pela capacidade; sem elas, `pct` é a média
das baterias dos dispositivos e `storedKwh`/`capacityKwh` vêm `null`.

### `GET /api/sources` → `api.getSources()`
```json
[{ "name": "Solar", "energyKwh": 3.4, "efficiencyPct": 18.2, "status": "Normal", "updatedAt": "2026-11-10T14:04:00Z" }]
```
Uma fonte por tipo de coleta, somando a energia de hoje dos dispositivos daquele tipo. `efficiencyPct` é `null`
enquanto não houver medição da energia disponível no ambiente.

### `GET /api/loads` → `api.getLoads()`
```json
[{ "name": "Gateway LoRa", "type": "Comunicação", "currentW": 48, "avgW": 44, "priority": "Alta", "status": "Normal" }]
```
`priority`: `Alta` | `Média` | `Baixa`. `currentW` e `avgW` são `null` quando o canal do dispositivo não mede consumo.

### `GET /api/batteries` → `api.getBatteries()`
```json
[{ "name": "Bateria 01", "type": "Íon-lítio", "capacityKwh": 5, "pct": 82, "status": "Normal" }]
```

### `GET /api/devices` → `api.getDevices()`
```json
[{
  "id": "IOT-001", "name": "Sensor Ambiental 01", "location": "Área Remota A", "region": "Norte",
  "latitude": -15.786, "longitude": -47.883, "status": "Online",
  "battery": 87, "consumption": 12, "temperature": 24, "txInterval": 5,
  "lastSeenAt": "2026-11-10T14:04:00Z",
  "harvesting": { "type": "Solar", "collected": 15 }
}]
```
Unidades: `battery` em %, `consumption` e `harvesting.collected` em Wh/dia (`null` = o canal não mede essa leitura; `0` = mede, mas está offline), `temperature` em °C
(`null` se offline), `txInterval` em minutos. `harvesting` é `null` quando o dispositivo não coleta energia.
No ThingSpeak, cada dispositivo costuma ser um canal; `lastSeenAt` = `created_at` do último *feed*.

### `GET /api/alerts` → `api.getAlerts()`
```json
[{ "id": 1, "level": "critical", "title": "Bateria crítica", "message": "Sensor Ambiental 03 está com nível de bateria baixo.", "createdAt": "2026-11-10T13:53:00Z" }]
```
`level`: `critical` | `high` | `warning` | `info` (define cor e ícone no front).

### `GET /api/forecasts` → `api.getForecasts()`
```json
{
  "season": "Primavera", "updatedAt": "2026-11-10T14:03:00Z",
  "items": [{ "id": "IOT-003", "daysLeft": 2, "sustainable": false, "deficit": false,
              "balanceWhDay": -5.04, "confidence": 91, "sunHours": 4.8 }]
}
```
A previsão usa os últimos 7 dias. `daysLeft` = dias até a bateria esgotar (bateria caindo), ou `null`.
Com `daysLeft: null`:
- `sustainable: true`: bateria estável/subindo **e** saldo energético não negativo (ou não medido);
- `deficit: true`: bateria estável/subindo, mas o consumo supera a coleta (front mostra "Saldo negativo");
- os dois `false`: sem leitura suficiente (offline ou poucos pontos).

`balanceWhDay` = coleta − consumo médio em Wh/dia (`null` se o canal não mede as duas grandezas).
`confidence` em % (ou `null`); cai com oscilação da bateria, poucos dados e quando bateria e saldo se
contradizem. `sunHours` = horas de sol por dia no local (`null` se o dispositivo não usa solar).

### `GET /api/devices/{id}/forecast?range=24h|7d` → `api.getForecastSeries(id, range)`
```json
{
  "step": "hour",
  "timestamps": ["2026-11-10T02:00:00Z", "…"],
  "nowIndex": 12,
  "consumption": { "measured": [11.8, "…", null], "predicted": [12.1, "…"] },
  "battery":     { "measured": [9, "…", null],     "predicted": [null, "…", 4] }
}
```
`24h` = 12 h passadas + 24 h futuras com `step: "hour"`; `7d` = 3 dias passados + 7 futuros com
`step: "day"`. `timestamps[nowIndex]` é o momento atual. `measured` é `null` no futuro; `battery.predicted`
é `null` no passado. Consumo em Wh, bateria em %.

### `GET /api/history?range=hoje|7d|30d|custom` → `api.getHistory(range)`
```json
{
  "step": "hour",
  "timestamps":   ["2026-11-10T03:00:00Z", "…"],
  "generatedKwh": [0, 0.12, "…"],
  "consumedKwh":  [0.12, 0.15, "…"],
  "storagePct":   [60, 58, "…"]
}
```
`hoje` = 24 pontos por hora (`step: "hour"`, kWh por hora); os demais = um ponto por dia terminando hoje
(`step: "day"`, kWh por dia). `custom` exige `from` e `to` (`AAAA-MM-DD`, até 31 dias atrás).
Horas/dias sem leitura (ou no futuro) vêm `null`.
Os agregados podem vir da própria API do ThingSpeak (parâmetros `average`/`sum` e `timescale`) ou ser
calculados no back-end.

### Cadastro de dispositivos (admin)

`GET /api/devices/{id}/settings` → `api.getDeviceSettings(id)` — configuração editável (não traz leituras):
```json
{
  "id": "IOT-001", "name": "Harveo – coletor solar", "location": "Laboratório EMBEDO (indoor)",
  "region": "Gdańsk", "latitude": 54.424526, "longitude": 18.478837, "txInterval": 1,
  "type": "Sensor", "priority": "Alta", "harvestingType": "Solar", "sunHours": null,
  "channelId": 2988514, "historyChunkDays": 2,
  "fields": { "battery": "field1", "consumption": "field2", "temperature": "none", "harvested": "field4" },
  "conversion": { "batteryVoltageMin": 3.0, "batteryVoltageMax": 4.2,
                  "consumptionScale": -0.001, "harvestedScale": 0.001, "temperatureScale": 1.0 }
}
```
`fields.*`: `field1`…`field8` ou `none` (o canal não mede). `conversion` é opcional: com
`batteryVoltageMin/Max` a bateria é lida em volts e convertida para %; as escalas multiplicam o valor lido.

`POST /api/devices` → `api.createDevice(settings)` — mesmo corpo; responde `201` com o dispositivo
(formato de `GET /api/devices/{id}`). `409` se o ID já existir.

`PUT /api/devices/{id}` → `api.updateDevice(id, settings)` — o ID não pode mudar.

`DELETE /api/devices/{id}` → `api.deleteDevice(id)` — responde `204`. Só apaga o cadastro.

`GET /api/thingspeak/channels/{channelId}` → `api.getChannelInfo(channelId)` — ajuda a preencher o cadastro:
```json
{ "channelId": 181889, "name": "Solar Weather Station (Outside2-17)", "description": "…",
  "latitude": 35.337296, "longitude": 139.624439,
  "fields": { "field1": "RSSIOut", "field2": "BatOut", "field3": "TempOut" },
  "lastEntryAt": "2026-11-12T02:25:00Z" }
```
`404` se o canal não existir ou for privado sem Read API Key válida.

### Manutenção

`GET /api/maintenance-requests` → `api.getMaintenanceRequests()`
```json
[{ "id": 1, "deviceId": "IOT-007", "reason": "Dispositivo offline", "note": "", "status": "Recebida",
   "createdAt": "2026-10-15T12:00:00Z", "scheduledFor": null, "requestedBy": "Ana" }]
```
`status`: `Recebida` | `Agendada` | `Em rota` | `Concluída`. `scheduledFor`: `AAAA-MM-DD` ou `null`.
`requestedBy`: nome de quem pediu, no momento do pedido (`null` em pedidos anteriores ao login).
ADMIN recebe todos os pedidos; USER, só os próprios.

`POST /api/maintenance-requests` → `api.requestMaintenance(deviceId, {reason, note})`
corpo `{ "deviceId": "IOT-007", "reason": "…", "note": "…" }`; responde com a solicitação criada.

`PATCH /api/maintenance-requests/{id}` (só ADMIN) → `api.updateMaintenanceRequest(id, {status, scheduledFor})`
corpo `{ "status": "Agendada", "scheduledFor": "2026-11-21" }`; responde com a solicitação atualizada.
Regras (validadas no back-end): `scheduledFor` vazio mantém a data atual;
voltar para `Recebida` limpa a data; `Agendada` exige data válida e não passada.
