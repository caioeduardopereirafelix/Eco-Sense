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

    @Override
    public ChannelMeta channel(long channelId, String readApiKey) {
        FeedsResponse body = fetch(channelId, readApiKey, uri -> uri.queryParam("results", 1));
        Map<String, Object> c = body == null || body.channel() == null ? Map.of() : body.channel();
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 1; i <= 8; i++) {
            Object label = c.get("field" + i);
            if (label != null && !label.toString().isBlank()) names.put("field" + i, label.toString());
        }
        List<Feed> feeds = body == null || body.feeds() == null ? List.of() : toFeeds(body.feeds());
        return new ChannelMeta(channelId, text(c.get("name")), text(c.get("description")),
                number(c.get("latitude")), number(c.get("longitude")), names,
                feeds.isEmpty() ? null : feeds.get(feeds.size() - 1).createdAt());
    }

    private List<Feed> get(long channelId, String readApiKey, java.util.function.UnaryOperator<UriBuilder> params) {
        FeedsResponse body = fetch(channelId, readApiKey, params);
        return body == null || body.feeds() == null ? List.of() : toFeeds(body.feeds());
    }

    private FeedsResponse fetch(long channelId, String readApiKey, java.util.function.UnaryOperator<UriBuilder> params) {
        return http.get()
                .uri(uri -> {
                    UriBuilder b = uri.path("/channels/{id}/feeds.json").queryParam("timezone", "UTC");
                    if (readApiKey != null && !readApiKey.isBlank()) b = b.queryParam("api_key", readApiKey);
                    return params.apply(b).build(channelId);
                })
                .retrieve()
                .body(FeedsResponse.class);
    }

    private static String text(Object v) {
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }

    /** O ThingSpeak usa "0.0" quando o canal não tem localização: vira null. */
    private static Double number(Object v) {
        try {
            double d = Double.parseDouble(String.valueOf(v));
            return d == 0 || !Double.isFinite(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
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
    record FeedsResponse(Map<String, Object> channel, List<Map<String, Object>> feeds) {}
}
