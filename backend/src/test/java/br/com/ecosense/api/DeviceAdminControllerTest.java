package br.com.ecosense.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class DeviceAdminControllerTest {

    @Autowired
    WebApplicationContext ctx;
    MockMvc mvc;

    @BeforeEach
    void login() throws Exception {
        mvc = TestAuth.asAdmin(ctx);
    }

    private static final String NEW_DEVICE = """
            {"id":"TEST-01","name":"Sensor de teste","location":"Bancada","region":"Laboratório",
             "latitude":-15.8,"longitude":-47.9,"txInterval":2,"type":"Sensor","priority":"Alta",
             "harvestingType":"Solar","channelId":1000001,
             "fields":{"battery":"field1","consumption":"field2","temperature":"none","harvested":"field4"},
             "conversion":{"batteryVoltageMin":3.0,"batteryVoltageMax":4.2}}""";

    @Test
    void criarEditarERemover() throws Exception {
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content(NEW_DEVICE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("TEST-01"))
                .andExpect(jsonPath("$.status").value("Online"));
        mvc.perform(get("/api/devices")).andExpect(jsonPath("$", hasSize(11)));
        mvc.perform(get("/api/devices/TEST-01/settings"))
                .andExpect(jsonPath("$.fields.temperature").value("none"))
                .andExpect(jsonPath("$.conversion.batteryVoltageMax").value(4.2));

        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON).content(NEW_DEVICE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um dispositivo com o ID TEST-01."));

        mvc.perform(put("/api/devices/TEST-01").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_DEVICE.replace("Sensor de teste", "Sensor renomeado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sensor renomeado"));

        mvc.perform(delete("/api/devices/TEST-01")).andExpect(status().isNoContent());
        mvc.perform(get("/api/devices/TEST-01")).andExpect(status().isNotFound());
        mvc.perform(get("/api/devices")).andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void validacoes() throws Exception {
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_DEVICE.replace("\"Sensor de teste\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe o nome."));
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_DEVICE.replace("\"field4\"", "\"field9\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Field inválido (use field1 a field8 ou none)."));
        mvc.perform(post("/api/devices").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_DEVICE.replace("\"batteryVoltageMax\":4.2", "\"batteryVoltageMax\":2.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A tensão de bateria cheia deve ser maior que a de bateria vazia."));
        mvc.perform(put("/api/devices/IOT-001").contentType(MediaType.APPLICATION_JSON).content(NEW_DEVICE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O ID do dispositivo não pode ser alterado."));
        mvc.perform(delete("/api/devices/NAO-EXISTE")).andExpect(status().isNotFound());
    }

    @Test
    void consultaCanalSimulado() throws Exception {
        mvc.perform(get("/api/thingspeak/channels/1000003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sensor Ambiental 03"))
                .andExpect(jsonPath("$.fields.field1").value("Bateria (%)"));
    }
}
