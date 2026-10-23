package io.bettermindustry.desktop;

import io.bettermindustry.protocol.InputCommandType;
import io.bettermindustry.protocol.InputEnvelope;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GatewayClient {
    private final URI baseUri;
    private final HttpClient http;
    private String sessionCookie;

    public GatewayClient(URI baseUri) {
        this.baseUri = baseUri;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    public synchronized SessionInfo connect() throws IOException, InterruptedException {
        HttpResponse<String> current = get("/api/gateway/session");
        if (current.statusCode() == 200) return parseSession(current.body());
        if (current.statusCode() != 401) throw failure(current);
        HttpResponse<String> created = post("/api/gateway/session", "", "text/plain");
        requireSuccess(created);
        captureCookie(created);
        return parseSession(created.body());
    }

    public synchronized SessionInfo login(String username, String password)
        throws IOException, InterruptedException {
        HttpResponse<String> response = postForm("/api/gateway/session", Map.of(
            "username", username,
            "password", password
        ));
        requireSuccess(response);
        captureCookie(response);
        return parseSession(response.body());
    }

    public synchronized void register(String username, String password)
        throws IOException, InterruptedException {
        requireSuccess(postForm("/api/auth/register", Map.of(
            "username", username,
            "password", password
        )));
    }

    public synchronized Snapshot snapshot() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/api/render/snapshot");
        requireSuccess(response);
        return parseSnapshot(response.body());
    }

    public synchronized byte[] worldImage(String sectorId) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(
            request("/api/render/world/" + encodePath(sectorId) + "/image")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
        requireSuccess(response.statusCode(), response.body());
        return response.body();
    }

    public synchronized void setFocus(String sectorId) throws IOException, InterruptedException {
        requireSuccess(post("/api/gateway/focus/" + encodePath(sectorId), "", "text/plain"));
    }

    public synchronized void acquireControl(String sectorId) throws IOException, InterruptedException {
        requireSuccess(post("/api/gateway/control/acquire/" + encodePath(sectorId), "", "text/plain"));
    }

    public synchronized void releaseControl(String sectorId) throws IOException, InterruptedException {
        requireSuccess(post("/api/gateway/control/release/" + encodePath(sectorId), "", "text/plain"));
    }

    public synchronized InputResult action(
        String sectorId,
        long tick,
        InputCommandType type,
        String message
    ) throws IOException, InterruptedException {
        String commandId = UUID.randomUUID().toString();
        InputEnvelope envelope = new InputEnvelope(
            InputEnvelope.VERSION,
            commandId,
            sectorId,
            Math.max(0, tick),
            Math.max(1, tick + 6_000),
            type,
            message == null ? "" : message
        );
        HttpResponse<String> response = post(
            "/api/gateway/input/envelope",
            envelope.toJson(),
            "application/json"
        );
        requireSuccess(response);
        String body = response.body();
        return new InputResult(
            GatewayJson.string(body, "status"),
            GatewayJson.bool(body, "duplicate"),
            GatewayJson.number(body, "acknowledgementTick"),
            GatewayJson.nullableBool(body, "acknowledgementPaused"),
            GatewayJson.string(body, "detail")
        );
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(
            request(path).timeout(Duration.ofSeconds(5)).GET().build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private HttpResponse<String> post(String path, String body, String contentType)
        throws IOException, InterruptedException {
        return http.send(
            request(path)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", contentType + "; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private HttpResponse<String> postForm(String path, Map<String, String> fields)
        throws IOException, InterruptedException {
        String body = fields.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        return post(path, body, "application/x-www-form-urlencoded");
    }

    private URI uri(String path) {
        return baseUri.resolve(path);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path));
        if (sessionCookie != null) {
            builder.header("Cookie", sessionCookie);
        }
        return builder;
    }

    private void captureCookie(HttpResponse<?> response) {
        for (String value : response.headers().allValues("Set-Cookie")) {
            String parsed = sessionCookie(value);
            if (parsed != null) {
                sessionCookie = parsed.isEmpty() ? null : parsed;
            }
        }
    }

    static String sessionCookie(String setCookie) {
        if (setCookie == null || !setCookie.startsWith("bm_session=")) return null;
        int semicolon = setCookie.indexOf(';');
        String pair = semicolon < 0 ? setCookie : setCookie.substring(0, semicolon);
        String value = pair.substring("bm_session=".length()).trim();
        return value.isEmpty() ? "" : pair.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static void requireSuccess(HttpResponse<String> response) {
        requireSuccess(response.statusCode(), response.body());
    }

    private static void requireSuccess(int status, Object body) {
        if (status < 200 || status >= 300) {
            throw new GatewayException(status, String.valueOf(body));
        }
    }

    private static GatewayException failure(HttpResponse<String> response) {
        return new GatewayException(response.statusCode(), response.body());
    }

    static SessionInfo parseSession(String json) {
        String username = GatewayJson.string(json, "username");
        return new SessionInfo(
            GatewayJson.string(json, "clientId"),
            username,
            GatewayJson.string(json, "role"),
            GatewayJson.longNumber(json, "expiresAtMillis")
        );
    }

    static Snapshot parseSnapshot(String json) {
        List<Sector> sectors = new ArrayList<>();
        for (String object : GatewayJson.objectArray(json, "sectors")) {
            sectors.add(new Sector(
                GatewayJson.string(object, "id"),
                GatewayJson.string(object, "map"),
                (int)GatewayJson.number(object, "port"),
                GatewayJson.string(object, "state"),
                GatewayJson.bool(object, "alive"),
                GatewayJson.bool(object, "portOpen"),
                GatewayJson.number(object, "gameTick"),
                GatewayJson.number(object, "gameTimeSeconds"),
                GatewayJson.bool(object, "paused"),
                (int)GatewayJson.number(object, "players"),
                (int)GatewayJson.number(object, "units"),
                (int)GatewayJson.number(object, "enemies"),
                (int)GatewayJson.number(object, "buildings"),
                GatewayJson.string(object, "viewMode"),
                GatewayJson.bool(object, "worldDataAvailable"),
                GatewayJson.longNumber(object, "worldSnapshotVersion")
            ));
        }
        return new Snapshot(
            GatewayJson.string(json, "clientId"),
            GatewayJson.string(json, "focusSectorId"),
            GatewayJson.string(json, "controlOwnerId"),
            sectors
        );
    }

    public record SessionInfo(String clientId, String username, String role, long expiresAtMillis) {
    }

    public record Snapshot(String clientId, String focusSectorId, String controlOwnerId, List<Sector> sectors) {
    }

    public record Sector(
        String id,
        String map,
        int port,
        String state,
        boolean alive,
        boolean portOpen,
        double gameTick,
        double gameTimeSeconds,
        boolean paused,
        int players,
        int units,
        int enemies,
        int buildings,
        String viewMode,
        boolean worldDataAvailable,
        long worldSnapshotVersion
    ) {
    }

    public record InputResult(
        String status,
        boolean duplicate,
        double acknowledgementTick,
        Boolean acknowledgementPaused,
        String detail
    ) {
    }

    public static final class GatewayException extends RuntimeException {
        private final int status;

        public GatewayException(int status, String body) {
            super("Gateway HTTP " + status + ": " + body);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
