package br.com.ecosense.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** API completa com o cliente simulado do ThingSpeak (application.yml padrão). */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void visaoGeral() throws Exception {
        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation.todayKwh").isNumber())
                .andExpect(jsonPath("$.consumption.nowW").isNumber())
                .andExpect(jsonPath("$.storage.capacityKwh").value(10.0))
                .andExpect(jsonPath("$.devices.total").value(10))
                .andExpect(jsonPath("$.devices.online").value(8))
                .andExpect(jsonPath("$.devices.offline").value(2));
    }

    @Test
    void dispositivosComStatusEDatasIso() throws Exception {
        mvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].id").value("IOT-001"))
                .andExpect(jsonPath("$[0].status").value("Online"))
                .andExpect(jsonPath("$[0].lastSeenAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d\\d-\\d\\dT.*Z")))
                .andExpect(jsonPath("$[0].harvesting.type").value("Solar"))
                .andExpect(jsonPath("$[4].harvesting").doesNotExist())
                .andExpect(jsonPath("$[6].status").value("Offline"))
                .andExpect(jsonPath("$[6].temperature").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void previsaoIdentificaBateriaEsgotandoESustentaveis() throws Exception {
        mvc.perform(get("/api/forecasts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(notNullValue()))
                .andExpect(jsonPath("$.items[0].sustainable").value(true))      // IOT-001 carregando
                .andExpect(jsonPath("$.items[0].deficit").value(false))
                .andExpect(jsonPath("$.items[0].balanceWhDay").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.items[2].daysLeft").value(lessThanOrEqualTo(4))) // IOT-003 caindo 4%/dia
                .andExpect(jsonPath("$.items[6].daysLeft").value(org.hamcrest.Matchers.nullValue())) // offline
                .andExpect(jsonPath("$.items[6].sustainable").value(false))
                .andExpect(jsonPath("$.items[6].deficit").value(false))
                /* IOT-010: bateria subindo, mas coleta < consumo → não é sustentável */
                .andExpect(jsonPath("$.items[9].daysLeft").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[9].sustainable").value(false))
                .andExpect(jsonPath("$.items[9].deficit").value(true))
                .andExpect(jsonPath("$.items[9].balanceWhDay").value(org.hamcrest.Matchers.lessThan(0.0)))
                .andExpect(jsonPath("$.items[9].confidence").value(lessThanOrEqualTo(79)));
        mvc.perform(get("/api/alerts"))
                .andExpect(jsonPath("$[?(@.id == 'IOT-010:deficit')].level").value(org.hamcrest.Matchers.contains("warning")));
    }

    @Test
    void saldoNegativoNaoProjetaBateriaSubindo() throws Exception {
        mvc.perform(get("/api/devices/IOT-010/forecast").param("range", "7d"))
                .andExpect(jsonPath("$.battery.predicted[3]").isNumber())
                .andExpect(jsonPath("$.battery.predicted[10]").value(lessThanOrEqualTo(67.0)));
    }

    @Test
    void seriesDePrevisao() throws Exception {
        mvc.perform(get("/api/devices/IOT-003/forecast").param("range", "24h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("hour"))
                .andExpect(jsonPath("$.timestamps", hasSize(37)))
                .andExpect(jsonPath("$.nowIndex").value(12))
                .andExpect(jsonPath("$.battery.measured[36]").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.battery.predicted[0]").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.battery.predicted[12]").isNumber());
        mvc.perform(get("/api/devices/IOT-003/forecast").param("range", "7d"))
                .andExpect(jsonPath("$.step").value("day"))
                .andExpect(jsonPath("$.timestamps", hasSize(11)));
    }

    @Test
    void historico() throws Exception {
        mvc.perform(get("/api/history").param("range", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("day"))
                .andExpect(jsonPath("$.timestamps", hasSize(7)))
                /* [5] = ontem (sempre completo); hoje ([6]) pode não ter leitura logo após a meia-noite */
                .andExpect(jsonPath("$.generatedKwh[5]").isNumber())
                .andExpect(jsonPath("$.storagePct[0]").isNumber());
        mvc.perform(get("/api/history").param("range", "hoje"))
                .andExpect(jsonPath("$.step").value("hour"))
                .andExpect(jsonPath("$.timestamps", hasSize(24)));
    }

    @Test
    void fontesCargasBateriasEAlertas() throws Exception {
        mvc.perform(get("/api/sources")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Solar"))
                .andExpect(jsonPath("$[0].efficiencyPct").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/loads")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(10)));
        mvc.perform(get("/api/batteries")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[2].status").value("Crítico"));
        mvc.perform(get("/api/alerts")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].level").value("critical"));
    }

    @Test
    void errosVoltamComMensagem() throws Exception {
        mvc.perform(get("/api/history").param("range", "ano"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Período")));
        mvc.perform(get("/api/devices/NAO-EXISTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Dispositivo NAO-EXISTE não encontrado."));
    }

    @Test
    void fluxoDeManutencao() throws Exception {
        String created = mvc.perform(post("/api/maintenance-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"IOT-007\",\"reason\":\"Dispositivo offline\",\"note\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Recebida"))
                .andExpect(jsonPath("$.scheduledFor").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        String day = LocalDate.now().plusDays(3).toString();

        mvc.perform(patch("/api/maintenance-requests/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Agendada\",\"scheduledFor\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe uma data válida para o agendamento."));
        mvc.perform(patch("/api/maintenance-requests/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Agendada\",\"scheduledFor\":\"" + day + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduledFor").value(day));
        mvc.perform(patch("/api/maintenance-requests/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Em rota\",\"scheduledFor\":\"\"}"))
                .andExpect(jsonPath("$.scheduledFor").value(day));
        mvc.perform(patch("/api/maintenance-requests/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Recebida\"}"))
                .andExpect(jsonPath("$.scheduledFor").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/maintenance-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"IOT-007\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe o motivo."));
    }

    @Test
    void corsLiberadoParaOFront() throws Exception {
        mvc.perform(get("/api/devices").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
