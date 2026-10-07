package com.ballastlane.assessment.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local stand-in for PokeAPI serving the captured fixtures over real HTTP (so real client timeouts apply).
 * Special names: {@code glitch} answers 503, {@code slowpoke} never answers until the stub is stopped (so a client
 * timeout is the only way out, with no dependence on how long a sleep lasts), unknown paths answer 404.
 * The whole-catalog name index (limit=100000) is served from {@code index.json}. Numeric ids 1..1025 (except 25, which is the Pikachu fixture) return the Pikachu fixture with that id.
 */
public final class FakePokeApi {

    private static final int LAST_VALID_ID = 1025;

    private final HttpServer server;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean indexDown;

    private FakePokeApi(HttpServer server) {
        this.server = server;
    }

    public static FakePokeApi start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FakePokeApi fake = new FakePokeApi(server);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/v2/", fake::handle);
            server.start();
            return fake;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/api/v2";
    }

    public int totalHits() {
        return hits.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    /** Makes the full name index (limit=100000) answer 503 until switched back. */
    public void setIndexDown(boolean down) {
        indexDown = down;
    }

    public void resetHits() {
        hits.clear();
    }

    public void stop() {
        release.countDown();
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring("/api/v2".length());
        hits.computeIfAbsent(path + "?" + exchange.getRequestURI().getQuery(), requestKey -> new AtomicInteger()).incrementAndGet();
        try {
            if (path.startsWith("/pokemon/") && path.substring(9).matches("\\d+")) {
                int id = Integer.parseInt(path.substring(9));
                if (id >= 1 && id <= LAST_VALID_ID) {
                    respond(exchange, 200, withId(id));
                    return;
                }
            }
            switch (path) {
                case "/pokemon" -> {
                    String query = String.valueOf(exchange.getRequestURI().getQuery());
                    if (query.contains("limit=100000")) {
                        respond(exchange, indexDown ? 503 : 200, indexDown ? "{}" : fixture("index.json"));
                    } else {
                        respond(exchange, 200, fixture("list.json"));
                    }
                }
                case "/pokemon/pikachu" -> respond(exchange, 200, fixture("pokemon-25.json"));
                case "/pokemon-species/25" -> respond(exchange, 200, fixture("species-25.json"));
                case "/evolution-chain/10" -> respond(exchange, 200, fixture("evolution-10.json"));
                case "/pokemon/glitch" -> respond(exchange, 503, "{}");
                case "/pokemon/slowpoke" -> {
                    release.await(30, TimeUnit.SECONDS);
                    respond(exchange, 200, fixture("pokemon-25.json"));
                }
                default -> respond(exchange, 404, "Not Found");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String withId(int id) {
        return fixture("pokemon-25.json").replaceFirst("\"id\": 25", "\"id\": " + id);
    }

    private static String fixture(String name) {
        try (InputStream in = FakePokeApi.class.getResourceAsStream("/pokeapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
