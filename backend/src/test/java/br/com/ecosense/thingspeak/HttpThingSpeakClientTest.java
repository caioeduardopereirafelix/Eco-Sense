package br.com.ecosense.thingspeak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.ecosense.config.EcoSenseProperties;

class HttpThingSpeakClientTest {

    private static final String BODY = """
            {"channel":{"id":12345,"name":"Sensor 01","field1":"Bateria"},
             "feeds":[
               {"created_at":"2026-11-10T14:00:00Z","entry_id":1,"field1":"87.5","field2":"0.48","field3":null},
               {"created_at":"2026-11-10T14:05:00Z","entry_id":2,"field1":"87.4","field2":"abc"}
             ]}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpThingSpeakClient client = new HttpThingSpeakClient(
            new EcoSenseProperties(new EcoSenseProperties.ThingSpeak("https://api.thingspeak.com", null, false),
                    null, null, null, null, null, null),
            builder);

    @Test
    void lerUltimasEntradasConverteFieldsEDatas() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.thingspeak.com/channels/12345/feeds.json")))
                .andExpect(queryParam("results", "2"))
                .andExpect(queryParam("api_key", "CHAVE"))
                .andExpect(queryParam("timezone", "UTC"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        List<Feed> feeds = client.latest(12345, "CHAVE", 2);

        assertThat(feeds).hasSize(2);
        assertThat(feeds.get(0).createdAt()).isEqualTo(Instant.parse("2026-11-10T14:00:00Z"));
        assertThat(feeds.get(0).number("field1")).isEqualTo(87.5);
        assertThat(feeds.get(0).number("field3")).isNull();
        assertThat(feeds.get(1).number("field2")).as("texto não numérico vira null").isNull();
        server.verify();
    }

    @Test
    void mediasEnviamPeriodoNoFormatoDoThingSpeakSemChaveParaCanalPublico() {
        server.expect(requestTo(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("api_key"))))
                .andExpect(queryParam("start", "2026-11-09%2000:00:00"))
                .andExpect(queryParam("end", "2026-11-10%2000:00:00"))
                .andExpect(queryParam("average", "60"))
                .andRespond(withSuccess("{\"feeds\":[]}", MediaType.APPLICATION_JSON));

        List<Feed> feeds = client.averaged(12345, "", Instant.parse("2026-11-09T00:00:00Z"),
                Instant.parse("2026-11-10T00:00:00Z"), 60);

        assertThat(feeds).isEmpty();
        server.verify();
    }
}
