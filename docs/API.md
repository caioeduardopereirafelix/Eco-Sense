# Contrato da API — EcoSense

Formato que o back-end (Java) deve devolver ao front-end. O front já funciona com estes dados
mockados em [`js/data/mock.js`](../js/data/mock.js); para integrar, basta trocar o corpo de cada
método de [`js/services/api.js`](../js/services/api.js) por um `fetch()` para o endpoint indicado.

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
| Datas em ISO 8601 com fuso | `"2026-11-10T14:05:00Z"` |
| Datas sem hora (agendamento) | `"2026-11-21"` |
| Valor ausente | `null` (não omitir o campo, não usar `""`) |
| Erros | HTTP 4xx/5xx com `{"message": "texto para o usuário"}` |

Status exibidos como tag usam estes valores (texto em português, também usados como classe CSS):
`Normal`, `Atenção`, `Crítico`, `Carregando`, `Online`, `Offline`.

## Endpoints

Os caminhos são sugestão; o que importa é o formato da resposta.

### `GET /api/overview` → `api.getOverview()`
```json
{
  "generation":  { "todayKwh": 4.82, "changePct": 8.4 },
  "consumption": { "nowW": 312, "todayKwh": 3.91, "changePct": -2.1 },
  "storage":     { "pct": 68, "storedKwh": 6.8, "capacityKwh": 10, "status": "Normal" },
  "devices":     { "total": 10, "online": 8, "offline": 2 }
}
```
`changePct` = variação em relação a ontem (negativo = queda).

### `GET /api/sources` → `api.getSources()`
```json
[{ "name": "Solar", "energyKwh": 3.4, "efficiencyPct": 18.2, "status": "Normal", "updatedAt": "2026-11-10T14:04:00Z" }]
```

### `GET /api/loads` → `api.getLoads()`
```json
[{ "name": "Gateway LoRa", "type": "Comunicação", "currentW": 48, "avgW": 44, "priority": "Alta", "status": "Normal" }]
```
`priority`: `Alta` | `Média` | `Baixa`.

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
Unidades: `battery` em %, `consumption` e `harvesting.collected` em Wh/dia, `temperature` em °C
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
  "items": [{ "id": "IOT-003", "daysLeft": 2, "sustainable": false, "confidence": 91, "sunHours": 4.8 }]
}
```
`daysLeft` = dias até a bateria esgotar, ou `null`. Com `daysLeft: null`: `sustainable: true` significa
que a coleta cobre o consumo; `false` significa sem leitura suficiente. `confidence` em % (ou `null`);
`sunHours` = horas de sol por dia no local (`null` se o dispositivo não usa solar).

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
(`step: "day"`, kWh por dia). Para `custom`, aceitar também `from` e `to` (`AAAA-MM-DD`).
Os agregados podem vir da própria API do ThingSpeak (parâmetros `average`/`sum` e `timescale`) ou ser
calculados no back-end.

### Manutenção

`GET /api/maintenance-requests` → `api.getMaintenanceRequests()`
```json
[{ "id": 1, "deviceId": "IOT-007", "reason": "Dispositivo offline", "note": "", "status": "Recebida",
   "createdAt": "2026-10-15T12:00:00Z", "scheduledFor": null }]
```
`status`: `Recebida` | `Agendada` | `Em rota` | `Concluída`. `scheduledFor`: `AAAA-MM-DD` ou `null`.

`POST /api/maintenance-requests` → `api.requestMaintenance(deviceId, {reason, note})`
corpo `{ "deviceId": "IOT-007", "reason": "…", "note": "…" }`; responde com a solicitação criada.

`PATCH /api/maintenance-requests/{id}` → `api.updateMaintenanceRequest(id, {status, scheduledFor})`
corpo `{ "status": "Agendada", "scheduledFor": "2026-11-21" }`; responde com a solicitação atualizada.
Regras (hoje validadas no mock, devem ir para o back-end): `scheduledFor` vazio mantém a data atual;
voltar para `Recebida` limpa a data; `Agendada` exige data válida e não passada.
