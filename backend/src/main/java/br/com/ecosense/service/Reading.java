package br.com.ecosense.service;

import java.time.Instant;

public record Reading(Instant at, Double battery, Double consumptionW, Double temperature, Double harvestedW) {}
