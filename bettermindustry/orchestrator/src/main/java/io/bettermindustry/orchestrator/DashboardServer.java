package io.bettermindustry.orchestrator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.bettermindustry.protocol.InputEnvelope;
import io.bettermindustry.protocol.RenderFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DashboardServer {
    private static final String SESSION_COOKIE = "bm_session";
    private final WorkerManager manager;
    private final Gateway gateway;
    private final LoginThrottle loginThrottle = new LoginThrottle();
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
        server.createContext("/", secure(this::root));
        server.createContext("/api/overview", secure(this::overview));
        server.createContext("/api/render/snapshot", secure(this::renderSnapshot));
        server.createContext("/api/render/stream", secure(this::renderStream));
        server.createContext("/api/render/world/", secure(this::renderWorld));
        server.createContext("/api/auth/register", secure(this::registerAccount));
        server.createContext("/api/account/sessions", secure(this::accountSessions));
        server.createContext("/api/admin/sessions", secure(this::adminSessions));
        server.createContext("/api/gateway/session", secure(this::gatewaySession));
        server.createContext("/api/gateway", secure(this::gatewayState));
        server.createContext("/api/gateway/focus/", secure(this::gatewayFocus));
        server.createContext("/api/gateway/views/", secure(this::gatewayView));
        server.createContext("/api/gateway/control/", secure(this::gatewayControl));
        server.createContext("/api/gateway/input/", secure(this::gatewayInput));
        server.createContext("/api/start-all", secure(exchange -> action(exchange, () -> manager.startAll())));
        server.createContext("/api/stop-all", secure(exchange -> action(exchange, () -> manager.stopAll())));
        server.createContext("/api/worker/", secure(this::workerAction));
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

    private HttpHandler secure(HttpHandler handler) {
        return exchange -> {
            if (!validHost(exchange)) {
                send(exchange, 421, "Misdirected Request", "text/plain; charset=utf-8");
                return;
            }
            if (!validOrigin(exchange)) {
                send(exchange, 403, "Cross-origin request denied", "text/plain; charset=utf-8");
                return;
            }
            handler.handle(exchange);
        };
    }

    private boolean validHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null) return false;
        String value = host.trim().toLowerCase(Locale.ROOT);
        return value.equals("127.0.0.1:" + port) || value.equals("localhost:" + port);
    }

    private boolean validOrigin(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) return true;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) return true;
        String value = origin.trim().toLowerCase(Locale.ROOT);
        return value.equals("http://127.0.0.1:" + port) || value.equals("http://localhost:" + port);
    }

    private void root(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            send(exchange, 404, "Not found", "text/plain; charset=utf-8");
            return;
        }
        send(exchange, 200, DashboardPage.html(), "text/html; charset=utf-8");
    }

    private void gatewaySession(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String token = sessionToken(exchange);
        if ("GET".equalsIgnoreCase(method)) {
            Gateway.Identity identity = gateway.authenticate(token);
            if (identity == null) {
                expireSessionCookie(exchange);
                send(exchange, 401, "Authentication required", "text/plain; charset=utf-8");
                return;
            }
            sendSession(exchange, identity);
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            String body = readBody(exchange);
            String label = userAgent(exchange);
            AuthSessionBook.Issued issued;
            if (body.isBlank()) {
                if (token != null) gateway.revokeSession(token);
                issued = gateway.issueSession(label);
            } else {
                try {
                    Map<String, String> form = parseForm(body);
                    String username = form.get("username");
                    String throttleKey = loginThrottleKey(exchange, username);
                    long now = System.currentTimeMillis();
                    long blocked = loginThrottle.blockedForMillis(throttleKey, now);
                    if (blocked > 0) {
                        sendRateLimited(exchange, blocked);
                        return;
                    }
                    issued = gateway.loginSession(username, form.get("password"), label);
                    if (issued == null) {
                        long retry = loginThrottle.recordFailure(throttleKey, now);
                        if (retry > 0) sendRateLimited(exchange, retry);
                        else send(exchange, 401, "Invalid username or password", "text/plain; charset=utf-8");
                        return;
                    }
                    loginThrottle.recordSuccess(throttleKey);
                } catch (IllegalArgumentException e) {
                    send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
                    return;
                }
                if (token != null) gateway.revokeSession(token);
            }
            Gateway.Identity identity = gateway.authenticate(issued.token());
            setSessionCookie(exchange, issued.token());
            sendSession(exchange, identity);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            if (gateway.revokeSession(token) == null) {
                expireSessionCookie(exchange);
                send(exchange, 401, "Authentication required", "text/plain; charset=utf-8");
                return;
            }
            expireSessionCookie(exchange);
            send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
            return;
        }
        send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
    }

    private void registerAccount(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        try {
            Map<String, String> form = parseForm(readBody(exchange));
            AccountBook.AccountView account = gateway.registerAccount(
                form.get("username"),
                form.get("password")
            );
            send(exchange, 200,
                "{\"username\":\"" + escape(account.username())
                    + "\",\"role\":\"" + account.role().name() + "\"}",
                "application/json; charset=utf-8");
        } catch (IllegalArgumentException e) {
            send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        } catch (IllegalStateException e) {
            send(exchange, 409, e.getMessage(), "text/plain; charset=utf-8");
        }
    }

    private void accountSessions(HttpExchange exchange) throws IOException {
        Gateway.Identity identity = requireRole(exchange, Role.PLAYER);
        if (identity == null) return;
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && parts.length == 4) {
            sendSessions(exchange, gateway.accountSessions(identity));
            return;
        }
        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) && parts.length == 4) {
            int revoked = gateway.revokeOtherSessions(identity);
            send(exchange, 200, "{\"ok\":true,\"revoked\":" + revoked + "}",
                "application/json; charset=utf-8");
            return;
        }
        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) && parts.length == 5) {
            if (!gateway.revokeAccountSession(identity, parts[4])) {
                send(exchange, 404, "Session not found", "text/plain; charset=utf-8");
                return;
            }
            send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
            return;
        }
        send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
    }

    private void adminSessions(HttpExchange exchange) throws IOException {
        Gateway.Identity identity = requireRole(exchange, Role.ADMIN);
        if (identity == null) return;
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && parts.length == 4) {
            sendSessions(exchange, gateway.allSessions(identity));
            return;
        }
        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) && parts.length == 5) {
            if (!gateway.revokeAdminSession(identity, parts[4])) {
                send(exchange, 404, "Session not found", "text/plain; charset=utf-8");
                return;
            }
            send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
            return;
        }
        send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
    }

    private void sendSessions(
        HttpExchange exchange,
        List<Gateway.SessionSummary> sessions
    ) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\"sessions\":[");
        for (int i = 0; i < sessions.size(); i++) {
            if (i > 0) json.append(',');
            Gateway.SessionSummary session = sessions.get(i);
            json.append('{');
            json.append("\"clientId\":\"").append(escape(session.clientId())).append("\",");
            json.append("\"username\":");
            if (session.username() == null) {
                json.append("null");
            } else {
                json.append('"').append(escape(session.username())).append('"');
            }
            json.append(',');
            json.append("\"role\":\"").append(session.role().name()).append("\",");
            json.append("\"label\":\"").append(escape(session.label())).append("\",");
            json.append("\"createdAtMillis\":").append(session.createdAtMillis()).append(',');
            json.append("\"expiresAtMillis\":").append(session.expiresAtMillis()).append(',');
            json.append("\"expiresInMillis\":").append(session.expiresInMillis()).append(',');
            json.append("\"current\":").append(session.current());
            json.append('}');
        }
        json.append("]}");
        send(exchange, 200, json.toString(), "application/json; charset=utf-8");
    }

    private void sendSession(
        HttpExchange exchange,
        Gateway.Identity identity
    ) throws IOException {
        send(exchange, 200,
            "{\"clientId\":\"" + escape(identity.clientId())
                + "\",\"username\":"
                + (identity.username() == null ? "null" : "\"" + escape(identity.username()) + "\"")
                + ",\"role\":\"" + identity.role().name()
                + "\",\"expiresAtMillis\":" + identity.expiresAtMillis() + "}",
            "application/json; charset=utf-8");
    }

    private void overview(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return;
        String clientId = identity.clientId();
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
        appendGateway(json, identity);
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
        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return;
        String clientId = identity.clientId();
        send(exchange, 200, createRenderFrame(clientId).toJson(), "application/json; charset=utf-8");
    }

    private void renderStream(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            } catch (IOException ignored) {
            }
            return;
        }

        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return;
        String clientId = identity.clientId();

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
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
        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return;
        StringBuilder json = new StringBuilder();
        appendGateway(json, identity);
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
        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return;
        try {
            gateway.setFocus(identity.clientId(), parts[4]);
            StringBuilder json = new StringBuilder();
            appendGateway(json, identity);
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
        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return;
        try {
            gateway.setViewMode(identity.clientId(), parts[4], ViewMode.parse(parts[5]));
            StringBuilder json = new StringBuilder();
            appendGateway(json, identity);
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
        Gateway.Identity identity = requireRole(exchange, Role.PLAYER);
        if (identity == null) return;
        try {
            Gateway.Snapshot state = switch (parts[4]) {
                case "acquire", "renew" -> gateway.acquireControl(identity.clientId(), parts[5]);
                case "release" -> gateway.releaseControl(identity.clientId(), parts[5]);
                default -> throw new IllegalArgumentException("unknown control action: " + parts[4]);
            };
            StringBuilder json = new StringBuilder();
            appendGateway(json, state, identity);
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
        if (parts.length == 5 && "envelope".equals(parts[4])) {
            gatewayInputEnvelope(exchange);
            return;
        }
        if (parts.length != 6) {
            send(exchange, 400, "Input action requires a sector id", "text/plain; charset=utf-8");
            return;
        }

        Gateway.Identity identity = requireRole(exchange, Role.PLAYER);
        if (identity == null) return;
        String clientId = identity.clientId();
        String sectorId = parts[5];
        String action = parts[4];
        String detail;
        if ("status".equals(action)) {
            detail = "";
        } else if ("pause".equals(action)) {
            detail = "on";
        } else if ("resume".equals(action)) {
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
        } else {
            send(exchange, 400, "Unknown input action: " + action, "text/plain; charset=utf-8");
            return;
        }

        try {
            ManagedWorker worker = gateway.authorizeInput(clientId, identity.role(), sectorId);
            String commandId = "legacy-" + UUID.randomUUID();
            ManagedWorker.ActionResult result = worker.executeAction(
                commandId,
                action,
                "chat".equals(action) ? detail : null
            );
            if (!result.applied()) {
                send(exchange, 409, result.detail(), "text/plain; charset=utf-8");
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

    private void gatewayInputEnvelope(HttpExchange exchange) throws IOException {
        Gateway.Identity identity = requireRole(exchange, Role.PLAYER);
        if (identity == null) return;
        String clientId = identity.clientId();
        try {
            InputEnvelope envelope = InputEnvelope.parse(readBody(exchange));
            Gateway.InputAdmission admission = gateway.beginInput(clientId, identity.role(), envelope);
            if (admission.duplicate()) {
                sendInputReceipt(exchange, admission.receipt(), true);
                return;
            }

            String detail = switch (envelope.type()) {
                case STATUS -> "";
                case PAUSE -> "on";
                case RESUME -> "off";
                case CHAT -> envelope.message();
            };
            String action = envelope.type().name().toLowerCase(Locale.ROOT);
            ManagedWorker.ActionResult result = admission.worker().executeAction(
                envelope.commandId(),
                action,
                envelope.type() == io.bettermindustry.protocol.InputCommandType.CHAT
                    ? envelope.message()
                    : null
            );
            if (!result.applied()) {
                InputReceiptBook.Receipt failed = gateway.completeInput(
                    envelope.commandId(),
                    InputReceiptBook.Status.FAILED,
                    result.detail(),
                    result.acknowledgement()
                );
                sendInputReceipt(exchange, failed, false);
                return;
            }

            InputReceiptBook.Receipt applied = gateway.completeInput(
                envelope.commandId(),
                InputReceiptBook.Status.APPLIED,
                detail,
                result.acknowledgement()
            );
            gateway.recordInput(
                clientId,
                envelope.sectorId(),
                envelope.type().name().toLowerCase(Locale.ROOT),
                detail
            );
            sendInputReceipt(exchange, applied, false);
        } catch (IllegalArgumentException e) {
            send(exchange, 400, e.getMessage(), "text/plain; charset=utf-8");
        } catch (Gateway.InputDeniedException e) {
            send(exchange, 403, e.getMessage(), "text/plain; charset=utf-8");
        } catch (Gateway.InputConflictException e) {
            send(exchange, 409, e.getMessage(), "text/plain; charset=utf-8");
        } catch (IllegalStateException e) {
            send(exchange, 409, e.getMessage(), "text/plain; charset=utf-8");
        }
    }

    private void sendInputReceipt(
        HttpExchange exchange,
        InputReceiptBook.Receipt receipt,
        boolean duplicate
    ) throws IOException {
        int status = switch (receipt.status()) {
            case PENDING -> 202;
            case APPLIED -> 200;
            case FAILED -> 409;
        };
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"protocolVersion\":").append(InputEnvelope.VERSION).append(',');
        json.append("\"commandId\":\"").append(escape(receipt.commandId())).append("\",");
        json.append("\"sectorId\":\"").append(escape(receipt.sectorId())).append("\",");
        json.append("\"type\":\"").append(receipt.type().name().toLowerCase(Locale.ROOT)).append("\",");
        json.append("\"status\":\"").append(receipt.status().name()).append("\",");
        json.append("\"duplicate\":").append(duplicate).append(',');
        json.append("\"detail\":\"").append(escape(receipt.detail())).append("\",");
        json.append("\"acknowledgementTick\":").append(receipt.acknowledgementTick()).append(',');
        json.append("\"acknowledgementPaused\":");
        if (receipt.acknowledgementPaused() == null) {
            json.append("null");
        } else {
            json.append(receipt.acknowledgementPaused());
        }
        json.append(',');
        json.append("\"updatedAtMillis\":").append(receipt.updatedAtMillis());
        json.append('}');
        send(exchange, status, json.toString(), "application/json; charset=utf-8");
    }

    private void appendGateway(StringBuilder json, Gateway.Identity identity) {
        appendGateway(json, gateway.snapshot(identity.clientId()), identity);
    }

    private void appendGateway(
        StringBuilder json,
        Gateway.Snapshot state,
        Gateway.Identity identity
    ) {
        json.append('{');
        json.append("\"clientId\":\"").append(escape(state.clientId())).append("\",");
        json.append("\"username\":");
        if (identity.username() == null) {
            json.append("null");
        } else {
            json.append('"').append(escape(identity.username())).append('"');
        }
        json.append(',');
        json.append("\"role\":\"").append(identity.role().name()).append("\",");
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
        if (requireSession(exchange) == null) return;
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
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
            return;
        }
        if (requireRole(exchange, Role.ADMIN) == null) return;
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
        if (requireRole(exchange, Role.ADMIN) == null) return;
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

    private static Map<String, String> parseForm(String body) {
        if (body == null || body.isBlank()) return Map.of();
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        for (String item : body.split("&")) {
            int split = item.indexOf('=');
            if (split <= 0) continue;
            String key = URLDecoder.decode(item.substring(0, split), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(item.substring(split + 1), StandardCharsets.UTF_8);
            fields.put(key, value);
        }
        return Map.copyOf(fields);
    }

    private Gateway.Identity requireSession(HttpExchange exchange) throws IOException {
        Gateway.Identity identity = gateway.authenticate(sessionToken(exchange));
        if (identity != null) return identity;
        expireSessionCookie(exchange);
        send(exchange, 401, "Authentication required", "text/plain; charset=utf-8");
        return null;
    }

    private Gateway.Identity requireRole(HttpExchange exchange, Role required) throws IOException {
        Gateway.Identity identity = requireSession(exchange);
        if (identity == null) return null;
        if (identity.role().ordinal() < required.ordinal()) {
            send(exchange, 403, "Insufficient role: " + required.name().toLowerCase(Locale.ROOT),
                "text/plain; charset=utf-8");
            return null;
        }
        return identity;
    }

    private static String sessionToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (!token.isBlank()) return token;
        }

        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String item : cookies.split(";")) {
                int split = item.indexOf('=');
                if (split > 0 && SESSION_COOKIE.equals(item.substring(0, split).trim())) {
                    String token = item.substring(split + 1).trim();
                    if (!token.isBlank()) return token;
                }
            }
        }
        return null;
    }

    private static String loginThrottleKey(HttpExchange exchange, String username) {
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return address + "|" + normalized;
    }

    private static String userAgent(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("User-Agent");
        return value == null || value.isBlank() ? "Unknown device" : value;
    }

    private static void sendRateLimited(HttpExchange exchange, long retryAfterMillis) throws IOException {
        long seconds = Math.max(1, (retryAfterMillis + 999L) / 1_000L);
        exchange.getResponseHeaders().set("Retry-After", Long.toString(seconds));
        send(exchange, 429, "Too many failed login attempts; retry later", "text/plain; charset=utf-8");
    }

    private static void setSessionCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().add(
            "Set-Cookie",
            SESSION_COOKIE + "=" + token
                + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + (AuthSessionBook.SESSION_LIFETIME_MILLIS / 1_000L)
        );
    }

    private static void expireSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add(
            "Set-Cookie",
            SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"
        );
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        sendBytes(exchange, status, bytes, contentType);
    }

    private static void sendBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
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
