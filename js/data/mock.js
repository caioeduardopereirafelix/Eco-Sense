/* ===== 1. DADOS MOCKADOS (substituir pelo back-end) =====
   Mesmo formato que a API real deve devolver (ver docs/API.md):
   números puros (unidade no nome do campo) e datas em ISO 8601. A formatação é feita no front. */
const minutesAgo=m=>new Date(Date.now()-m*60000).toISOString();
const MOCK={
 overview:{
  generation:{todayKwh:4.82,changePct:8.4},
  consumption:{nowW:312,todayKwh:3.91,changePct:-2.1},
  storage:{pct:68,storedKwh:6.8,capacityKwh:10,status:'Normal'}
  /* devices: calculado pela api a partir da lista de dispositivos */
 },
 sources:[
  {name:'Solar',            energyKwh:3.40,efficiencyPct:18.2,status:'Normal', updatedAt:minutesAgo(1)},
  {name:'Vibração mecânica',energyKwh:0.62,efficiencyPct:41,  status:'Normal', updatedAt:minutesAgo(2)},
  {name:'Gradiente térmico',energyKwh:0.54,efficiencyPct:6.8, status:'Atenção',updatedAt:minutesAgo(4)},
  {name:'RF ambiente',      energyKwh:0.26,efficiencyPct:2.1, status:'Normal', updatedAt:minutesAgo(3)}
 ],
 loads:[
  {name:'Sensor Ambiental 01',  type:'Sensor',     currentW:12,avgW:11,priority:'Alta', status:'Normal'},
  {name:'Gateway LoRa',         type:'Comunicação',currentW:48,avgW:44,priority:'Alta', status:'Normal'},
  {name:'Sensor Ambiental 03',  type:'Sensor',     currentW:9, avgW:10,priority:'Alta', status:'Crítico'},
  {name:'Câmera de Vigilância', type:'Vídeo',      currentW:96,avgW:70,priority:'Média',status:'Atenção'},
  {name:'Dispositivo IoT 05',   type:'Atuador',    currentW:74,avgW:40,priority:'Média',status:'Atenção'},
  {name:'Estação Meteorológica',type:'Sensor',     currentW:22,avgW:21,priority:'Média',status:'Normal'},
  {name:'Iluminação Auxiliar',  type:'Carga',      currentW:41,avgW:38,priority:'Baixa',status:'Normal'},
  {name:'Display Informativo',  type:'Carga',      currentW:10,avgW:12,priority:'Baixa',status:'Normal'}
 ],
 batteries:[
  {name:'Bateria 01',      type:'Íon-lítio',     capacityKwh:5,  pct:82,status:'Normal'},
  {name:'Bateria 02',      type:'Íon-lítio',     capacityKwh:3,  pct:34,status:'Atenção'},
  {name:'Supercapacitor A',type:'Supercapacitor',capacityKwh:0.5,pct:12,status:'Crítico'},
  {name:'Bateria 03',      type:'LiFePO4',       capacityKwh:1.5,pct:55,status:'Carregando'}
 ],
 /* battery em %, consumption e harvesting.collected em Wh/dia, temperature em °C, txInterval em minutos */
 devices:[
  {id:'IOT-001',name:'Sensor Ambiental 01',  location:'Área Remota A', region:'Norte',  latitude:-15.7860,longitude:-47.8830,status:'Online', battery:87,consumption:12,lastSeenAt:minutesAgo(1),   temperature:24,  txInterval:5, harvesting:{type:'Solar',   collected:15}},
  {id:'IOT-002',name:'Sensor Ambiental 02',  location:'Área Remota A', region:'Norte',  latitude:-15.7890,longitude:-47.8780,status:'Online', battery:74,consumption:11,lastSeenAt:minutesAgo(2),   temperature:23,  txInterval:5, harvesting:{type:'Solar',   collected:13}},
  {id:'IOT-003',name:'Sensor Ambiental 03',  location:'Área Remota B', region:'Leste',  latitude:-15.7950,longitude:-47.8720,status:'Online', battery:9, consumption:9, lastSeenAt:minutesAgo(3),   temperature:31,  txInterval:5, harvesting:{type:'Solar',   collected:4}},
  {id:'IOT-004',name:'Gateway LoRa',         location:'Torre Central', region:'Central',latitude:-15.7939,longitude:-47.8828,status:'Online', battery:92,consumption:48,lastSeenAt:minutesAgo(0.5), temperature:36,  txInterval:1, harvesting:{type:'Solar',   collected:52}},
  {id:'IOT-005',name:'Dispositivo IoT 05',   location:'Área Remota C', region:'Sul',    latitude:-15.8010,longitude:-47.8860,status:'Online', battery:61,consumption:74,lastSeenAt:minutesAgo(1),   temperature:29,  txInterval:2, harvesting:null},
  {id:'IOT-006',name:'Estação Meteorológica',location:'Topo da colina',region:'Norte',  latitude:-15.7810,longitude:-47.8910,status:'Online', battery:78,consumption:22,lastSeenAt:minutesAgo(5),   temperature:18,  txInterval:10,harvesting:{type:'Solar',   collected:25}},
  {id:'IOT-007',name:'Câmera Norte',         location:'Cerca Norte',   region:'Norte',  latitude:-15.7780,longitude:-47.8790,status:'Offline',battery:0, consumption:0, lastSeenAt:minutesAgo(180), temperature:null,txInterval:5, harvesting:{type:'Solar',   collected:0}},
  {id:'IOT-008',name:'Sensor de Solo 01',    location:'Área Remota B', region:'Leste',  latitude:-15.7980,longitude:-47.8690,status:'Online', battery:18,consumption:6, lastSeenAt:minutesAgo(4),   temperature:21,  txInterval:15,harvesting:{type:'Térmico', collected:3}},
  {id:'IOT-009',name:'Sensor de Solo 02',    location:'Área Remota D', region:'Leste',  latitude:-15.8050,longitude:-47.8750,status:'Offline',battery:0, consumption:0, lastSeenAt:minutesAgo(1440),temperature:null,txInterval:15,harvesting:{type:'Térmico', collected:0}},
  {id:'IOT-010',name:'Medidor de Vazão',     location:'Rio Leste',     region:'Leste',  latitude:-15.8070,longitude:-47.8660,status:'Online', battery:66,consumption:14,lastSeenAt:minutesAgo(2),   temperature:17,  txInterval:5, harvesting:{type:'Cinético',collected:15}}
 ],
 /* level: 'critical' | 'high' | 'warning' | 'info' */
 alerts:[
  {id:1,level:'critical',title:'Bateria crítica',message:'Sensor Ambiental 03 está com nível de bateria baixo.',createdAt:minutesAgo(12)},
  {id:2,level:'high',    title:'Consumo elevado',message:'Dispositivo IoT 05 apresentou consumo acima do esperado.',createdAt:minutesAgo(38)},
  {id:3,level:'warning', title:'Baixa geração',  message:'A geração solar está abaixo do esperado.',createdAt:minutesAgo(60)},
  {id:4,level:'info',    title:'Sistema normal', message:'Todos os dispositivos da Área Remota A estão funcionando normalmente.',createdAt:minutesAgo(120)}
 ],
 /* Solicitações de manutenção feitas pelo usuário (portal do usuário) */
 requests:[{id:1,deviceId:'IOT-007',reason:'Dispositivo offline',note:'',status:'Recebida',createdAt:'2026-10-15T12:00:00.000Z',scheduledFor:null}],
 /* Previsão de bateria por dispositivo (viria do modelo preditivo do back-end).
    daysLeft = dias até a bateria esgotar; null + sustainable = coleta >= consumo (sem esgotamento previsto);
    confidence em %; sunHours = horas de sol/dia no local (null se o dispositivo não usa solar). */
 forecast:{season:'Primavera',updatedAt:minutesAgo(2),items:[
  {id:'IOT-001',daysLeft:null,sustainable:true, confidence:93,sunHours:6.2},
  {id:'IOT-002',daysLeft:null,sustainable:true, confidence:92,sunHours:6.2},
  {id:'IOT-003',daysLeft:2,   sustainable:false,confidence:91,sunHours:4.8},
  {id:'IOT-004',daysLeft:null,sustainable:true, confidence:95,sunHours:6.5},
  {id:'IOT-005',daysLeft:5,   sustainable:false,confidence:84,sunHours:null},
  {id:'IOT-006',daysLeft:null,sustainable:true, confidence:90,sunHours:6.8},
  {id:'IOT-007',daysLeft:null,sustainable:false,confidence:null,sunHours:5.5},
  {id:'IOT-008',daysLeft:6,   sustainable:false,confidence:78,sunHours:null},
  {id:'IOT-009',daysLeft:null,sustainable:false,confidence:null,sunHours:null},
  {id:'IOT-010',daysLeft:null,sustainable:true, confidence:71,sunHours:null}
 ]}
};
