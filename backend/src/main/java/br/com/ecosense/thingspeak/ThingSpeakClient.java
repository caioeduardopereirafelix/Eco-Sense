package br.com.ecosense.thingspeak;

import java.time.Instant;
import java.util.List;

/** Leitura de canais do ThingSpeak. Implementações: HTTP real e simulada. */
public interface ThingSpeakClient {

    /** Últimas {@code results} entradas do canal, da mais antiga para a mais nova. */
    List<Feed> latest(long channelId, String readApiKey, int results);

    /**
     * Entradas entre {@code start} e {@code end} agregadas pela média em janelas de
     * {@code averageMinutes} minutos (o ThingSpeak aceita 10, 15, 20, 30, 60, 240, 720 e 1440).
     */
    List<Feed> averaged(long channelId, String readApiKey, Instant start, Instant end, int averageMinutes);

    /** Nome, descrição, localização e nomes dos fields do canal (para o cadastro de dispositivos). */
    ChannelMeta channel(long channelId, String readApiKey);
}
