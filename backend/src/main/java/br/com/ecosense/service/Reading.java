package br.com.ecosense.service;

import java.time.Instant;

/**
 * Uma leitura de dispositivo já convertida dos fields do ThingSpeak.
 * battery em %, consumptionW e harvestedW em W, temperature em °C. Qualquer valor pode ser null.
 */
public record Reading(Instant at, Double battery, Double consumptionW, Double temperature, Double harvestedW) {}
