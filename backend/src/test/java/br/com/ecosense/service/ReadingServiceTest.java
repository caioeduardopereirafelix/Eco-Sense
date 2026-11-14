package br.com.ecosense.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.service.ReadingService.DeviceData;
import br.com.ecosense.thingspeak.ChannelMeta;
import br.com.ecosense.thingspeak.Feed;
import br.com.ecosense.thingspeak.ThingSpeakClient;

class ReadingServiceTest {

    static final Instant NOW = Instant.parse("2026-11-14T12:30:00Z");

    static class CountingClient implements ThingSpeakClient {
        final AtomicInteger latest = new AtomicInteger();
        final AtomicInteger averaged = new AtomicInteger();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        volatile Long failingChannel;

        private void slow() {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
        }

        @Override
        public List<Feed> latest(long channelId, String key, int results) {
            latest.incrementAndGet();
            slow();
            return List.of(new Feed(NOW.minusSeconds(60), Map.of("field1", "80")));
        }

        @Override
        public List<Feed> averaged(long channelId, String key, Instant start, Instant end, int minutes) {
            averaged.incrementAndGet();
            slow();
            if (Long.valueOf(channelId).equals(failingChannel)) throw new IllegalStateException("HTTP 500");
            List<Feed> feeds = new ArrayList<>();
            for (Instant h = start.truncatedTo(ChronoUnit.HOURS); h.isBefore(end); h = h.plus(Duration.ofHours(1)))
                if (!h.isBefore(start)) feeds.add(new Feed(h, Map.of("field1", "80")));
            return feeds;
        }

        @Override
        public ChannelMeta channel(long channelId, String key) {
            throw new UnsupportedOperationException();
        }
    }

    final CountingClient client = new CountingClient();
    final ReadingService service = new ReadingService(client,
            new EcoSenseProperties(null, null, null, null, null, null, null), Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void stop() {
        service.shutdown();
    }

    static DeviceConfig device(String id, long channel, int chunkDays) {
        return new DeviceConfig(id, id, null, null, null, null, null, null, null, null, null,
                channel, null, chunkDays, null, null);
    }

    static final int CHUNKS = (int) Math.ceil(ReadingService.HISTORY_DAYS / 2.0);

    @Test
    void historicoEmPedacosParalelosComLimiteEMesmoResultado() {
        DeviceData data = service.data(device("A", 1, 2));

        assertThat(client.averaged).hasValue(CHUNKS);
        assertThat(client.maxRunning.get()).isGreaterThan(1).isLessThanOrEqualTo(ReadingService.MAX_PARALLEL_REQUESTS);
        List<Instant> hours = data.hourly().stream().map(Reading::at).toList();
        assertThat(hours).isSorted().doesNotHaveDuplicates();
        assertThat(hours).hasSize(ReadingService.HISTORY_DAYS * 24);
        assertThat(data.latest()).isNotNull();
    }

    @Test
    void requisicoesSimultaneasFazemUmaBuscaSoPorDispositivo() throws Exception {
        DeviceConfig d = device("A", 1, 2);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> callers = new ArrayList<>();
        List<DeviceData> results = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 8; i++) {
            callers.add(Thread.ofPlatform().start(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                results.add(service.data(d));
            }));
        }
        go.countDown();
        for (Thread t : callers) t.join();

        assertThat(client.latest).hasValue(1);
        assertThat(client.averaged).hasValue(CHUNKS);
        assertThat(results).hasSize(8).allSatisfy(r -> assertThat(r).isSameAs(results.get(0)));
    }

    @Test
    void cargaAoLigarPreencheOCache() {
        List<DeviceConfig> devices = List.of(device("A", 1, 2), device("B", 2, 31), device("C", 3, 5));
        service.warmUp(devices);
        int calls = client.latest.get() + client.averaged.get();

        assertThat(client.latest).hasValue(3);
        devices.forEach(service::data);
        assertThat(client.latest.get() + client.averaged.get()).isEqualTo(calls);
    }

    @Test
    void falhaEmUmPedacoNaoDerrubaAApi() {
        client.failingChannel = 7L;
        DeviceData data = service.data(device("X", 7, 2));

        assertThat(data.latest()).isNull();
        assertThat(data.hourly()).isEmpty();
        int calls = client.averaged.get();
        service.data(device("X", 7, 2));
        assertThat(client.averaged).hasValue(calls);
    }
}
