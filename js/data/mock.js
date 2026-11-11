/* ===== 1. DADOS MOCKADOS (substituir pelo back-end) ===== */
const MOCK={
 overview:{gen:{v:4.82,u:'kWh',d:+8.4},con:{now:312,period:3.91,d:-2.1},sto:{pct:68,cap:'6,8 kWh de 10 kWh',st:'Normal'}},
 sources:[{n:'Solar',e:'3,40 kWh',ef:'18,2%',st:'Normal',up:'há 1 min'},{n:'Vibração mecânica',e:'0,62 kWh',ef:'41%',st:'Normal',up:'há 2 min'},{n:'Gradiente térmico',e:'0,54 kWh',ef:'6,8%',st:'Atenção',up:'há 4 min'},{n:'RF ambiente',e:'0,26 kWh',ef:'2,1%',st:'Normal',up:'há 3 min'}],
 loads:[{n:'Sensor Ambiental 01',t:'Sensor',c:12,m:11,p:'Alta',s:'Normal'},{n:'Gateway LoRa',t:'Comunicação',c:48,m:44,p:'Alta',s:'Normal'},{n:'Sensor Ambiental 03',t:'Sensor',c:9,m:10,p:'Alta',s:'Crítico'},{n:'Câmera de Vigilância',t:'Vídeo',c:96,m:70,p:'Média',s:'Atenção'},{n:'Dispositivo IoT 05',t:'Atuador',c:74,m:40,p:'Média',s:'Atenção'},{n:'Estação Meteorológica',t:'Sensor',c:22,m:21,p:'Média',s:'Normal'},{n:'Iluminação Auxiliar',t:'Carga',c:41,m:38,p:'Baixa',s:'Normal'},{n:'Display Informativo',t:'Carga',c:10,m:12,p:'Baixa',s:'Normal'}],
 batteries:[{n:'Bateria 01',tp:'Íon-lítio',cap:'5 kWh',pct:82,st:'Normal'},{n:'Bateria 02',tp:'Íon-lítio',cap:'3 kWh',pct:34,st:'Atenção'},{n:'Supercapacitor A',tp:'Supercapacitor',cap:'0,5 kWh',pct:12,st:'Crítico'},{n:'Bateria 03',tp:'LiFePO4',cap:'1,5 kWh',pct:55,st:'Carregando'}],
 devices:[
  {id:'IOT-001',name:'Sensor Ambiental 01',location:'Área Remota A',region:'Norte',latitude:-15.7860,longitude:-47.8830,status:'Online', battery:87,consumption:12,lastSeen:'há 1 min', temperature:24,txInterval:5, harvesting:{type:'Solar',    collected:15}},
  {id:'IOT-002',name:'Sensor Ambiental 02',location:'Área Remota A',region:'Norte',latitude:-15.7890,longitude:-47.8780,status:'Online', battery:74,consumption:11,lastSeen:'há 2 min', temperature:23,txInterval:5, harvesting:{type:'Solar',    collected:13}},
  {id:'IOT-003',name:'Sensor Ambiental 03',location:'Área Remota B',region:'Leste',latitude:-15.7950,longitude:-47.8720,status:'Online', battery:9, consumption:9,lastSeen:'há 3 min', temperature:31,txInterval:5, harvesting:{type:'Solar',    collected:4}},
  {id:'IOT-004',name:'Gateway LoRa',       location:'Torre Central',region:'Central',latitude:-15.7939,longitude:-47.8828,status:'Online', battery:92,consumption:48,lastSeen:'há 30 s',  temperature:36,txInterval:1, harvesting:{type:'Solar',    collected:52}},
  {id:'IOT-005',name:'Dispositivo IoT 05', location:'Área Remota C',region:'Sul',latitude:-15.8010,longitude:-47.8860,status:'Online', battery:61,consumption:74,lastSeen:'há 1 min', temperature:29,txInterval:2, harvesting:null},
  {id:'IOT-006',name:'Estação Meteorológica',location:'Topo da colina',region:'Norte',latitude:-15.7810,longitude:-47.8910,status:'Online',battery:78,consumption:22,lastSeen:'há 5 min',temperature:18,txInterval:10,harvesting:{type:'Solar',    collected:25}},
  {id:'IOT-007',name:'Câmera Norte',       location:'Cerca Norte',region:'Norte',latitude:-15.7780,longitude:-47.8790,status:'Offline',battery:0, consumption:0,lastSeen:'há 3 h',   temperature:null,txInterval:5, harvesting:{type:'Solar',    collected:0}},
  {id:'IOT-008',name:'Sensor de Solo 01',  location:'Área Remota B',region:'Leste',latitude:-15.7980,longitude:-47.8690,status:'Online', battery:18,consumption:6,lastSeen:'há 4 min', temperature:21,txInterval:15, harvesting:{type:'Térmico',  collected:3}},
  {id:'IOT-009',name:'Sensor de Solo 02',  location:'Área Remota D',region:'Leste',latitude:-15.8050,longitude:-47.8750,status:'Offline',battery:0, consumption:0,lastSeen:'há 1 dia', temperature:null,txInterval:15, harvesting:{type:'Térmico',  collected:0}},
  {id:'IOT-010',name:'Medidor de Vazão',   location:'Rio Leste',region:'Leste',latitude:-15.8070,longitude:-47.8660,status:'Online', battery:66,consumption:14,lastSeen:'há 2 min', temperature:17,txInterval:5, harvesting:{type:'Cinético', collected:15}}
 ],
 alerts:[{id:1,lv:'cr',ic:'🔴',t:'Bateria crítica',m:'Sensor Ambiental 03 está com nível de bateria baixo.',w:'há 12 min'},{id:2,lv:'hi',ic:'🟠',t:'Consumo elevado',m:'Dispositivo IoT 05 apresentou consumo acima do esperado.',w:'há 38 min'},{id:3,lv:'wn',ic:'🟡',t:'Baixa geração',m:'A geração solar está abaixo do esperado.',w:'há 1 h'},{id:4,lv:'ok',ic:'🟢',t:'Sistema normal',m:'Todos os dispositivos da Área Remota A estão funcionando normalmente.',w:'há 2 h'}],
 /* Solicitações de manutenção feitas pelo usuário (portal do usuário) */
 requests:[{id:1,deviceId:'IOT-007',reason:'Dispositivo offline',note:'',status:'Recebida',createdAt:'2026-10-15T12:00:00.000Z',scheduledFor:null}],
 /* Previsão de bateria por dispositivo (viria do modelo preditivo do back-end).
    daysLeft = dias até a bateria esgotar; null + sustainable = coleta >= consumo (sem esgotamento previsto);
    confidence em %; sunHours = horas de sol/dia no local (null se o dispositivo não usa solar). */
 forecast:{season:'Primavera',updatedAt:'há 2 min',items:[
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
