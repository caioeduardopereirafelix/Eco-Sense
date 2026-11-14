package br.com.ecosense.thingspeak;

import java.time.Instant;
import java.util.List;

public interface ThingSpeakClient {

    List<Feed> latest(long channelId, String readApiKey, int results);

    List<Feed> averaged(long channelId, String readApiKey, Instant start, Instant end, int averageMinutes);

    ChannelMeta channel(long channelId, String readApiKey);
}
