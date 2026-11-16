package br.com.ecosense.thingspeak;

import java.time.Instant;
import java.util.Map;

public record ChannelMeta(long id, String name, String description, Double latitude, Double longitude,
                          Map<String, String> fields, Instant lastEntryAt) {}
