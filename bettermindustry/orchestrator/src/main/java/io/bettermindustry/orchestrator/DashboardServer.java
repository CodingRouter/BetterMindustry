package io.bettermindustry.orchestrator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.bettermindustry.protocol.RenderFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DashboardServer {
    private final WorkerManager manager;
    private final Gateway gateway;
    private final int port;
    private final AtomicLong renderSequence = new AtomicLong();
    private HttpServer server;

    public DashboardServer(WorkerManager manager, int port) {
        this.manager = manager;
        this.gateway = new Gateway(
            manager,
            manager.projectRoot().resolve("bettermindustry/runtime/gateway.properties")
        );
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::root);
        server.createContext("/api/overview", this::overview);
        server.createContext("/api/render/snapshot", this::renderSnapshot);
        server.createContext("/api/render/stream", this::renderStream);
        server.createContext("/api/render/world/", this::renderWorld);
        server.createContext("/api/gateway", this::gatewayState);
        server.createContext("/api/gateway/focus/", this::gatewayFocus);
        server.createContext("/api/gateway/views/", this::gatewayView);
        server.createContext("/api/gateway/control/", this::gatewayControl);
        server.createContext("/api/gateway/input/", this::gatewayInput);
        server.createContext("/api/start-all", exchange -> action(exchange, () -> manager.startAll()));
        server.createContext("/api/stop-all", exchange -> action(exchange, () -> manager.stopAll()));
        server.createContext("/api/worker/", this::workerAction);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "bettermindustry-dashboard");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void root(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            send(exchange, 404, "Not found", "text/plain; charset=utf-8");
            return;
        }
        send(exchange, 200, DashboardPage.html(), "text/html; charset=utf-8");
    }

    private void overview(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        String clientId = clientId(exchange);
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"service\":\"bettermindustry-debugger\",");
        json.append("\"projectRoot\":\"").append(escape(manager.projectRoot().toString())).append("\",");
        json.append("\"serverJar\":\"").append(escape(manager.serverJar().toString())).append("\",");
        json.append("\"catalogFile\":\"").append(escape(manager.catalogFile().toString())).append("\",");
        json.append("\"pipeline\":{");
        json.append("\"gameTick\":\"server-worker processes\",");
        json.append("\"renderTick\":\"planned separate process\",");
        json.append("\"transport\":\"not implemented\"},");
        json.append("\"gateway\":");
        appendGateway(json, clientId);
        json.append(',');
        json.append("\"workers\":[");
        Collection<ManagedWorker> workers = manager.workers();
        int index = 0;
        for (ManagedWorker worker : workers) {
            if (index++ > 0) json.append(',');
            appendSnapshot(json, worker, clientId);
        }
        json.append("]}");
        send(exchange, 200, json.toString(), "application/json; charset=utf-8");
    }

    private void renderSnapshot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        send(exchange, 200, createRenderFrame(clientId(exchange)).toJson(), "application/json; charset=utf-8");
    }

    private void renderStream(HttpExchange exchange) {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            } catch (IOException ignored) {
            }
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        String clientId = clientId(exchange);
        try {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                while (!Thread.currentThread().isInterrupted()) {
                    RenderFrame frame = createRenderFrame(clientId);
                    String event = "event: render\nid: " + frame.sequence()
                        + "\ndata: " + frame.toJson() + "\n\n";
                    out.write(event.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(50);
                }
            }
        } catch (IOException ignored) {
            // Client disconnected or the debugger is shutting down.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private RenderFrame createRenderFrame(String clientId) {
        Gateway.Snapshot gatewayState = gateway.snapshot(clientId);
        List<RenderFrame.SectorFrame> sectors = new ArrayList<>();
        for (ManagedWorker worker : manager.workers()) {
            ManagedWorker.Snapshot snapshot = worker.snapshot();
            ManagedWorker.Diagnostics diagnostics = snapshot.diagnostics();
            long age = diagnostics == null ? -1
                : Duration.between(diagnostics.receivedAt(), Instant.now()).toMillis();
            sectors.add(new RenderFrame.SectorFrame(
                snapshot.id(),
                diagnostics == null ? snapshot.map() : diagnostics.map(),
                snapshot.port(),
                snapshot.state(),
                snapshot.alive(),
                snapshot.portOpen(),
                diagnostics == null ? 0 : diagnostics.tick(),
                diagnostics == null ? 0 : diagnostics.gameTimeSeconds(),
                diagnostics == null ? 0 : diagnostics.updateId(),
                diagnostics == null ? 0 : diagnostics.wave(),
                diagnostics != null && diagnostics.paused(),
                diagnostics == null ? 0 : diagnostics.players(),
                diagnostics == null ? 0 : diagnostics.units(),
                diagnostics == null ? 0 : diagnostics.enemies(),
                diagnostics == null ? 0 : diagnostics.buildings(),
                age,
                gatewayState.views().getOrDefault(snapshot.id(), ViewMode.OFF).name(),
                snapshot.worldDataAvailable(),
                snapshot.worldSnapshotTick(),
                snapshot.worldSnapshotAgeMillis(),
                snapshot.worldSnapshotVersion()
            ));
        }
        return new RenderFrame(
            RenderFrame.VERSION,
            renderSequence.incrementAndGet(),
            System.currentTimeMillis(),
            gatewayState.clientId(),
            gatewayState.focusSectorId(),
            gatewayState.controlOwnerId(),
            sectors
        );
    }

    private void gatewayState(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        String clientId = clientId(exchange);
        StringBuilder json = new StringBuilder();
        appendGateway(json, clientId);
        send(exchange, 200, json.toString(), "application/json; charset=utf-8");
    }

    private void gatewayFocus(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length != 5) {
            send(exchange, 400, "Focus action requires a sector id", "text/plain; charset=utf-8");
            return;
        }
        try {
            String clientId = clientId(exchange);
            gateway.setFocus(clientId, parts[4]);
            StringBuilder json = new StringBuilder();
            appendGateway(json, clientId);
            send(exchange, 200, json.toString(), "application/json; charset=utf-8");
        } catch (IllegalArgumentException e) {
            send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        }
    }

    private void gatewayView(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length != 6) {
            send(exchange, 400, "View action requires sector id and mode", "text/plain; charset=utf-8");
            return;
        }
        try {
            String clientId = clientId(exchange);
            gateway.setViewMode(clientId, parts[4], ViewMode.parse(parts[5]));
            StringBuilder json = new StringBuilder();
            appendGateway(json, clientId);
            send(exchange, 200, json.toString(), "application/json; charset=utf-8");
        } catch (IllegalArgumentException e) {
            send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        }
    }

    private void gatewayControl(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length != 6) {
            send(exchange, 400, "Control action requires action and sector id", "text/plain; charset=utf-8");
            return;
        }
        String clientId = clientId(exchange);
        try {
            Gateway.Snapshot state = switch (parts[4]) {
                case "acquire", "renew" -> gateway.acquireControl(clientId, parts[5]);
                case "release" -> gateway.releaseControl(clientId, parts[5]);
                default -> throw new IllegalArgumentException("unknown control action: " + parts[4]);
            };
            StringBuilder json = new StringBuilder();
            appendGateway(json, state);
            send(exchange, 200, json.toString(), "application/json; charset=utf-8");
        } catch (IllegalArgumentException e) {
            send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        } catch (IllegalStateException e) {
            send(exchange, 409, e.getMessage(), "text/plain; charset=utf-8");
        }
    }

    private void gatewayInput(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length != 6) {
            send(exchange, 400, "Input action requires a sector id", "text/plain; charset=utf-8");
            return;
        }

        String clientId = clientId(exchange);
        String sectorId = parts[5];
        String action = parts[4];
        String command;
        String detail;
        if ("status".equals(action)) {
            command = "status";
            detail = "";
        } else if ("pause".equals(action)) {
            command = "pause on";
            detail = "on";
        } else if ("resume".equals(action)) {
            command = "pause off";
            detail = "off";
        } else if ("chat".equals(action)) {
            detail = readBody(exchange)
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
            if (detail.isBlank() || detail.length() > 150) {
                send(exchange, 400, "Chat message must contain 1-150 characters", "text/plain; charset=utf-8");
                return;
            }
            command = "say " + detail;
        } else {
            send(exchange, 400, "Unknown input action: " + action, "text/plain; charset=utf-8");
            return;
        }

        try {
            ManagedWorker worker = gateway.authorizeInput(clientId, sectorId);
            String result = worker.sendCommand(command);
            if (!"Sent".equals(result)) {
                send(exchange, 409, result, "text/plain; charset=utf-8");
                return;
            }
            gateway.recordInput(clientId, sectorId, action, detail);
            send(exchange, 200,
                "{\"ok\":true,\"clientId\":\"" + escape(clientId)
                    + "\",\"sectorId\":\"" + escape(sectorId) + "\",\"command\":\""
                    + escape(action) + "\",\"detail\":\"" + escape(detail) + "\"}",
                "application/json; charset=utf-8");
        } catch (IllegalArgumentException e) {
            send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        } catch (IllegalStateException e) {
            send(exchange, 403, e.getMessage(), "text/plain; charset=utf-8");
        }
    }

    private void appendGateway(StringBuilder json, String clientId) {
        appendGateway(json, gateway.snapshot(clientId));
    }

    private void appendGateway(StringBuilder json, Gateway.Snapshot state) {
        json.append('{');
        json.append("\"clientId\":\"").append(escape(state.clientId())).append("\",");
        json.append("\"focusSectorId\":\"").append(escape(state.focusSectorId())).append("\",");
        json.append("\"stateFile\":\"").append(escape(gateway.stateFile().toString())).append("\",");
        json.append("\"controlOwnerId\":");
        if (state.controlOwnerId() == null) {
            json.append("null");
        } else {
            json.append('"').append(escape(state.controlOwnerId())).append('"');
        }
        json.append(',');
        json.append("\"controlExpiresAtMillis\":").append(state.controlExpiresAtMillis()).append(',');
        json.append("\"controlExpiresInMillis\":").append(state.controlExpiresInMillis()).append(',');
        json.append("\"views\":{");
        int index = 0;
        for (Map.Entry<String, ViewMode> entry : state.views().entrySet()) {
            if (index++ > 0) json.append(',');
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                .append(entry.getValue().name()).append('"');
        }
        json.append("},\"effectiveViews\":{");
        index = 0;
        for (Map.Entry<String, ViewMode> entry : state.effectiveViews().entrySet()) {
            if (index++ > 0) json.append(',');
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                .append(entry.getValue().name()).append('"');
        }
        json.append("},\"recentInputs\":[");
        int inputCount = Math.min(10, state.recentInputs().size());
        for (int i = 0; i < inputCount; i++) {
            if (i > 0) json.append(',');
            Gateway.InputAudit audit = state.recentInputs().get(i);
            json.append('{');
            json.append("\"timestampMillis\":").append(audit.timestampMillis()).append(',');
            json.append("\"clientId\":\"").append(escape(audit.clientId())).append("\",");
            json.append("\"sectorId\":\"").append(escape(audit.sectorId())).append("\",");
            json.append("\"command\":\"").append(escape(audit.command())).append("\",");
            json.append("\"detail\":\"").append(escape(audit.detail())).append("\"");
            json.append('}');
        }
        json.append("]}");
    }

    private void renderWorld(HttpExchange exchange) throws IOException {
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) || parts.length != 6) {
            send(exchange, 404, "World snapshot not found", "text/plain; charset=utf-8");
            return;
        }
        ManagedWorker worker = manager.find(parts[4]);
        if (worker == null) {
            send(exchange, 404, "Worker not found", "text/plain; charset=utf-8");
            return;
        }

        Path file;
        String contentType;
        if ("image".equals(parts[5])) {
            file = worker.config().worldImage();
            contentType = "image/png";
        } else if ("metadata".equals(parts[5])) {
            file = worker.config().worldMetadata();
            contentType = "application/json; charset=utf-8";
        } else {
            send(exchange, 404, "World asset not found", "text/plain; charset=utf-8");
            return;
        }

        if (!Files.isRegularFile(file)) {
            send(exchange, 404, "World snapshot not ready", "text/plain; charset=utf-8");
            return;
        }
        sendBytes(exchange, 200, Files.readAllBytes(file), contentType);
    }

    private void workerAction(HttpExchange exchange) throws IOException {
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length < 5) {
            send(exchange, 404, "Worker action not found", "text/plain; charset=utf-8");
            return;
        }
        ManagedWorker worker = manager.find(parts[3]);
        if (worker == null) {
            send(exchange, 404, "Worker not found", "text/plain; charset=utf-8");
            return;
        }

        String action = parts[4];
        String result;
        switch (action) {
            case "start" -> result = worker.start();
            case "stop" -> result = worker.requestStop();
            case "save" -> result = worker.saveCheckpoint();
            case "command" -> {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
                    return;
                }
                result = worker.sendCommand(readBody(exchange));
            }
            default -> {
                send(exchange, 404, "Unknown action", "text/plain; charset=utf-8");
                return;
            }
        }
        send(exchange, 200, "{\"ok\":true,\"message\":\"" + escape(result) + "\"}",
            "application/json; charset=utf-8");
    }

    private void action(HttpExchange exchange, Action action) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        action.run();
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void appendSnapshot(StringBuilder json, ManagedWorker worker, String clientId) {
        ManagedWorker.Snapshot snapshot = worker.snapshot();
        json.append('{');
        json.append("\"id\":\"").append(escape(snapshot.id())).append("\",");
        json.append("\"map\":\"").append(escape(snapshot.map())).append("\",");
        json.append("\"port\":").append(snapshot.port()).append(',');
        json.append("\"runtimeDir\":\"").append(escape(snapshot.runtimeDir())).append("\",");
        json.append("\"state\":\"").append(escape(snapshot.state())).append("\",");
        json.append("\"alive\":").append(snapshot.alive()).append(',');
        json.append("\"pid\":").append(snapshot.pid() == null ? "null" : snapshot.pid()).append(',');
        json.append("\"uptimeSeconds\":").append(snapshot.uptimeSeconds()).append(',');
        json.append("\"exitCode\":").append(snapshot.exitCode() == null ? "null" : snapshot.exitCode()).append(',');
        json.append("\"portOpen\":").append(snapshot.portOpen()).append(',');
        json.append("\"checkpoint\":").append(snapshot.checkpoint()).append(',');
        json.append("\"viewMode\":\"").append(gateway.viewMode(clientId, snapshot.id()).name()).append("\",");
        json.append("\"diagnostics\":");
        ManagedWorker.Diagnostics diagnostics = snapshot.diagnostics();
        if (diagnostics == null) {
            json.append("null");
        } else {
            json.append('{');
            json.append("\"tick\":").append(diagnostics.tick()).append(',');
            json.append("\"gameTimeSeconds\":").append(diagnostics.gameTimeSeconds()).append(',');
            json.append("\"updateId\":").append(diagnostics.updateId()).append(',');
            json.append("\"wave\":").append(diagnostics.wave()).append(',');
            json.append("\"waveTimeSeconds\":").append(diagnostics.waveTimeSeconds()).append(',');
            json.append("\"paused\":").append(diagnostics.paused()).append(',');
            json.append("\"gameOver\":").append(diagnostics.gameOver()).append(',');
            json.append("\"playing\":").append(diagnostics.playing()).append(',');
            json.append("\"players\":").append(diagnostics.players()).append(',');
            json.append("\"units\":").append(diagnostics.units()).append(',');
            json.append("\"enemies\":").append(diagnostics.enemies()).append(',');
            json.append("\"buildings\":").append(diagnostics.buildings()).append(',');
            json.append("\"map\":\"").append(escape(diagnostics.map())).append("\",");
            json.append("\"ageMs\":").append(Duration.between(diagnostics.receivedAt(), Instant.now()).toMillis());
            json.append('}');
        }
        json.append(',');
        json.append("\"worldDataAvailable\":").append(snapshot.worldDataAvailable()).append(',');
        json.append("\"worldSnapshotTick\":").append(snapshot.worldSnapshotTick()).append(',');
        json.append("\"worldSnapshotAgeMillis\":").append(snapshot.worldSnapshotAgeMillis()).append(',');
        json.append("\"worldSnapshotVersion\":").append(snapshot.worldSnapshotVersion()).append(',');
        String worldImageDataUrl = null;
        if (snapshot.worldDataAvailable()) {
            try {
                Path image = worker.config().worldImage();
                if (Files.size(image) < 2_000_000) {
                    worldImageDataUrl = "data:image/png;base64,"
                        + Base64.getEncoder().encodeToString(Files.readAllBytes(image));
                }
            } catch (IOException ignored) {
            }
        }
        json.append("\"worldImageDataUrl\":");
        if (worldImageDataUrl == null) {
            json.append("null");
        } else {
            json.append('"').append(worldImageDataUrl).append('"');
        }
        json.append(',');
        json.append("\"logs\":[");
        for (int i = 0; i < snapshot.logs().size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(escape(snapshot.logs().get(i))).append('"');
        }
        json.append("]}");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private static String clientId(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-BM-Client-Id");
        if (header != null && !header.isBlank()) return header.trim();

        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String item : query.split("&")) {
                int split = item.indexOf('=');
                if (split > 0 && "clientId".equals(item.substring(0, split))) {
                    String value = URLDecoder.decode(item.substring(split + 1), StandardCharsets.UTF_8);
                    if (!value.isBlank()) return value;
                }
            }
        }
        return "local-debugger";
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendBytes(exchange, status, bytes, contentType);
    }

    private static void sendBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    @FunctionalInterface
    private interface Action {
        void run();
    }
}
