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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DashboardServer {
    private final WorkerManager manager;
    private final int port;
    private final AtomicLong renderSequence = new AtomicLong();
    private HttpServer server;

    public DashboardServer(WorkerManager manager, int port) {
        this.manager = manager;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::root);
        server.createContext("/api/overview", this::overview);
        server.createContext("/api/render/snapshot", this::renderSnapshot);
        server.createContext("/api/render/stream", this::renderStream);
        server.createContext("/api/render/world/", this::renderWorld);
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
        json.append("\"workers\":[");
        Collection<ManagedWorker> workers = manager.workers();
        int index = 0;
        for (ManagedWorker worker : workers) {
            if (index++ > 0) json.append(',');
            appendSnapshot(json, worker.snapshot());
        }
        json.append("]}");
        send(exchange, 200, json.toString(), "application/json; charset=utf-8");
    }

    private void renderSnapshot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        send(exchange, 200, createRenderFrame().toJson(), "application/json; charset=utf-8");
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
        try {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                while (!Thread.currentThread().isInterrupted()) {
                    RenderFrame frame = createRenderFrame();
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

    private RenderFrame createRenderFrame() {
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
            sectors
        );
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

    private static void appendSnapshot(StringBuilder json, ManagedWorker.Snapshot snapshot) {
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
