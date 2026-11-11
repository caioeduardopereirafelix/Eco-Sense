package br.com.ecosense.thingspeak;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import br.com.ecosense.config.EcoSenseProperties;

/**
 * Cliente da API REST do ThingSpeak (GET /channels/{id}/feeds.json).
 * Ativo quando {@code ecosense.thingspeak.simulate=false}.
 */
@Component
@ConditionalOnProperty(name = "ecosense.thingspeak.simulate", havingValue = "false", matchIfMissing = true)
public class HttpThingSpeakClient implements ThingSpeakClient {

    /** O ThingSpeak espera "AAAA-MM-DD HH:NN:SS" no fuso informado em {@code timezone}. */
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final RestClient http;

    public HttpThingSpeakClient(EcoSenseProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.thingspeak().baseUrl()).build();
    }

    @Override
    public List<Feed> latest(long channelId, String readApiKey, int results) {
        return get(channelId, readApiKey, uri -> uri.queryParam("results", results));
    }

    @Override
    public List<Feed> averaged(long channelId, String readApiKey, Instant start, Instant end, int averageMinutes) {
        return get(channelId, readApiKey, uri -> uri
                .queryParam("start", TS_FORMAT.format(start))
                .queryParam("end", TS_FORMAT.format(end))
                .queryParam("average", averageMinutes));
    }

    private List<Feed> get(long channelId, String readApiKey, java.util.function.UnaryOperator<UriBuilder> params) {
        FeedsResponse body = http.get()
                .uri(uri -> {
                    UriBuilder b = uri.path("/channels/{id}/feeds.json").queryParam("timezone", "UTC");
                    if (readApiKey != null && !readApiKey.isBlank()) b = b.queryParam("api_key", readApiKey);
                    return params.apply(b).build(channelId);
                })
                .retrieve()
                .body(FeedsResponse.class);
        return body == null || body.feeds() == null ? List.of() : toFeeds(body.feeds());
    }

    private static List<Feed> toFeeds(List<Map<String, Object>> raw) {
        List<Feed> feeds = new ArrayList<>(raw.size());
        for (Map<String, Object> entry : raw) {
            Object createdAt = entry.get("created_at");
            if (createdAt == null) continue;
            Map<String, String> fields = new LinkedHashMap<>();
            entry.forEach((k, v) -> {
                if (k.startsWith("field") && v != null) fields.put(k, v.toString());
            });
            feeds.add(new Feed(Instant.parse(createdAt.toString()), fields));
        }
        return feeds;
    }

    /** Resposta do ThingSpeak: {"channel": {...}, "feeds": [{"created_at": "...", "field1": "..."}]} */
    record FeedsResponse(List<Map<String, Object>> feeds) {}
}
