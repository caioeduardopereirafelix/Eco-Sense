package br.com.ecosense.thingspeak;

import java.time.Instant;
import java.util.Map;

/**
 * Dados descritivos de um canal do ThingSpeak.
 *
 * @param fields      nome de cada field usado (ex.: field1 → "Vbat [mV]")
 * @param lastEntryAt data da última entrada (null se o canal nunca recebeu dados)
 */
public record ChannelMeta(long id, String name, String description, Double latitude, Double longitude,
                          Map<String, String> fields, Instant lastEntryAt) {}
