package io.bettermindustry.orchestrator;

import io.bettermindustry.protocol.InputEnvelope;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class Gateway {
    private static final long CONTROL_LEASE_MILLIS = 15_000L;

    private final WorkerManager manager;
    private final Path stateFile;
    private final AccountBook accounts;
    private final AuthSessionBook authSessions = new AuthSessionBook();
    private final LinkedHashMap<String, SessionState> sessions = new LinkedHashMap<>();
    private final LinkedHashMap<String, ControlLease> leases = new LinkedHashMap<>();
    private final InputReceiptBook inputReceipts = new InputReceiptBook();
    private final ArrayDeque<InputAudit> inputAudits = new ArrayDeque<>();

    public Gateway(WorkerManager manager, Path stateFile) {
        this.manager = manager;
        this.stateFile = stateFile.toAbsolutePath().normalize();
        this.accounts = new AccountBook(this.stateFile.resolveSibling("accounts.properties"));
        load();
        applyPolicies();
        persist();
    }

    public synchronized Path stateFile() {
        return stateFile;
    }

    synchronized AuthSessionBook.Issued issueSession() {
        AuthSessionBook.Issued issued = authSessions.issue(System.currentTimeMillis());
        ensureSession(issued.session().clientId());
        applyPolicies();
        persist();
        return issued;
    }

    synchronized AuthSessionBook.Issued loginSession(String username, String password) {
        AccountBook.AccountView account = accounts.authenticate(username, password);
        if (account == null) return null;
        AuthSessionBook.Issued issued = authSessions.issue(
            account.username(),
            System.currentTimeMillis()
        );
        ensureSession(issued.session().clientId());
        applyPolicies();
        persist();
        return issued;
    }

    synchronized AccountBook.AccountView registerAccount(String username, String password) {
        return accounts.register(username, password, System.currentTimeMillis());
    }

    synchronized Identity authenticate(String token) {
        AuthSessionBook.Session auth = authSessions.authenticate(token, System.currentTimeMillis());
        if (auth == null) return null;
        pruneSessions();
        if (auth.username() == null) {
            return new Identity(auth.clientId(), null, Role.GUEST, auth.expiresAtMillis());
        }
        AccountBook.AccountView account = accounts.find(auth.username());
        if (account == null) {
            authSessions.revokeClient(auth.clientId());
            removeSession(auth.clientId());
            return null;
        }
        return new Identity(
            auth.clientId(),
            account.username(),
            account.role(),
            auth.expiresAtMillis()
        );
    }

    synchronized String revokeSession(String token) {
        String clientId = authSessions.revoke(token, System.currentTimeMillis());
        if (clientId == null) return null;
        removeSession(clientId);
        applyPolicies();
        persist();
        return clientId;
    }

    public synchronized String focusSectorId(String clientId) {
        return session(clientId).focusSectorId;
    }

    public synchronized ViewMode viewMode(String clientId, String sectorId) {
        return session(clientId).views.getOrDefault(sectorId, ViewMode.OFF);
    }

    public synchronized ViewMode effectiveViewMode(String sectorId) {
        ViewMode result = ViewMode.OFF;
        for (SessionState session : sessions.values()) {
            ViewMode mode = session.views.getOrDefault(sectorId, ViewMode.OFF);
            if (mode.ordinal() < result.ordinal()) result = mode;
        }
        return result;
    }

    public synchronized void setFocus(String clientId, String sectorId) {
        requireWorker(sectorId);
        SessionState session = session(clientId);
        if (!sectorId.equals(session.focusSectorId)) {
            releaseOwnedControl(session.focusSectorId, clientId);
            session.focusSectorId = sectorId;
        }
        session.views.put(sectorId, ViewMode.FULL);
        touch(session);
        applyPolicies();
        persist();
    }

    public synchronized void setViewMode(String clientId, String sectorId, ViewMode mode) {
        requireWorker(sectorId);
        SessionState session = session(clientId);
        if (sectorId.equals(session.focusSectorId) && mode == ViewMode.OFF) {
            throw new IllegalArgumentException("cannot close the focus sector");
        }
        session.views.put(sectorId, mode);
        touch(session);
        applyPolicies();
        persist();
    }

    public synchronized Snapshot acquireControl(String clientId, String sectorId) {
        requireWorker(sectorId);
        requireClient(clientId);
        SessionState session = session(clientId);
        if (!sectorId.equals(session.focusSectorId)) {
            throw new IllegalStateException("control can only be acquired for the client's focus sector");
        }
        long now = System.currentTimeMillis();
        expireLeases(now);
        ControlLease current = leases.get(sectorId);
        if (current != null && !current.ownerId.equals(clientId)) {
            throw new IllegalStateException("control is held by " + current.ownerId);
        }
        leases.put(sectorId, new ControlLease(clientId, now + CONTROL_LEASE_MILLIS));
        touch(session);
        return snapshot(clientId);
    }

    public synchronized Snapshot releaseControl(String clientId, String sectorId) {
        requireWorker(sectorId);
        requireClient(clientId);
        SessionState session = session(clientId);
        long now = System.currentTimeMillis();
        expireLeases(now);
        ControlLease current = leases.get(sectorId);
        if (current != null && !current.ownerId.equals(clientId)) {
            throw new IllegalStateException("control is held by " + current.ownerId);
        }
        releaseOwnedControl(sectorId, clientId);
        touch(session);
        return snapshot(clientId);
    }

    public synchronized ManagedWorker authorizeInput(
        String clientId,
        Role role,
        String sectorId
    ) {
        requireWorker(sectorId);
        requireClient(clientId);
        requirePlayer(role);
        long now = System.currentTimeMillis();
        expireLeases(now);
        SessionState session = session(clientId);
        if (!sectorId.equals(session.focusSectorId)) {
            throw new InputDeniedException("input denied: sector is not the client's focus");
        }
        ControlLease lease = leases.get(sectorId);
        if (lease == null || !lease.ownerId.equals(clientId)) {
            throw new InputDeniedException("input denied: active control lease required");
        }
        touch(session);
        return manager.find(sectorId);
    }

    synchronized InputAdmission beginInput(String clientId, Role role, InputEnvelope envelope) {
        String normalizedClientId = normalizeClientId(clientId);
        long now = System.currentTimeMillis();
        try {
            InputReceiptBook.Admission existing = inputReceipts.find(normalizedClientId, envelope, now);
            if (existing != null) {
                return new InputAdmission(null, existing.receipt(), true);
            }
        } catch (InputReceiptBook.ConflictException e) {
            throw new InputConflictException(e.getMessage());
        }

        ManagedWorker worker = authorizeInput(normalizedClientId, role, envelope.sectorId());
        ManagedWorker.Diagnostics diagnostics = worker.snapshot().diagnostics();
        if (diagnostics == null) {
            throw new IllegalStateException("worker diagnostics unavailable");
        }

        long currentTick = (long)Math.floor(diagnostics.tick());
        if (currentTick > envelope.deadlineTick()) {
            throw new IllegalStateException("input deadline exceeded");
        }
        if (envelope.expectedTick() > currentTick + 120) {
            throw new IllegalStateException("input tick is too far in the future");
        }

        InputReceiptBook.Admission admission = inputReceipts.begin(
            normalizedClientId,
            envelope,
            now
        );
        persist();
        return new InputAdmission(worker, admission.receipt(), admission.duplicate());
    }

    synchronized InputReceiptBook.Receipt completeInput(
        String commandId,
        InputReceiptBook.Status status,
        String detail
    ) {
        InputReceiptBook.Receipt updated = inputReceipts.complete(
            commandId,
            status,
            detail,
            System.currentTimeMillis()
        );
        if (updated != null) persist();
        return updated;
    }

    public synchronized void recordInput(String clientId, String sectorId, String command, String detail) {
        InputAudit audit = new InputAudit(
            Instant.now().toEpochMilli(),
            normalizeClientId(clientId),
            sectorId,
            command,
            detail == null ? "" : detail
        );
        inputAudits.addFirst(audit);
        while (inputAudits.size() > 100) inputAudits.removeLast();

        Path auditFile = stateFile.resolveSibling("gateway-audit.log");
        try {
            Path parent = auditFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            String line = audit.timestampMillis() + "\t" + audit.clientId() + "\t"
                + audit.sectorId() + "\t" + audit.command() + "\t" + audit.detail() + System.lineSeparator();
            Files.writeString(auditFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public synchronized Snapshot snapshot(String clientId) {
        long now = System.currentTimeMillis();
        expireLeases(now);
        SessionState session = session(clientId);
        touch(session);
        ControlLease lease = leases.get(session.focusSectorId);
        long remaining = lease == null ? 0 : Math.max(0, lease.expiresAtMillis - now);
        return new Snapshot(
            clientId,
            session.focusSectorId,
            Map.copyOf(session.views),
            effectiveViews(),
            lease == null ? null : lease.ownerId,
            lease == null ? 0 : lease.expiresAtMillis,
            remaining,
            List.copyOf(new ArrayList<>(inputAudits))
        );
    }

    private void load() {
        if (Files.isRegularFile(stateFile)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(stateFile);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException ignored) {
            }

            authSessions.load(properties, System.currentTimeMillis());
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("session.")) continue;
                String[] parts = key.split("\\.");
                if (parts.length < 3) continue;
                String clientId = parts[1];
                if (!authSessions.hasClient(clientId)) continue;
                SessionState session = ensureSession(clientId);
                if (parts.length == 3 && parts[2].equals("focusSectorId")) {
                    String focus = properties.getProperty(key);
                    if (manager.find(focus) != null) session.focusSectorId = focus;
                } else if (parts.length == 5 && parts[2].equals("sector") && parts[4].equals("viewMode")) {
                    String sectorId = parts[3];
                    if (manager.find(sectorId) != null) {
                        try {
                            session.views.put(sectorId, ViewMode.parse(properties.getProperty(key)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }

            inputReceipts.load(properties, System.currentTimeMillis());
        }
    }

    private SessionState ensureSession(String clientId) {
        String normalized = normalizeClientId(clientId);
        SessionState existing = sessions.get(normalized);
        if (existing != null) return existing;

        SessionState session = new SessionState();
        for (ManagedWorker worker : manager.workers()) {
            session.views.put(worker.config().id(), ViewMode.FULL);
        }
        session.focusSectorId = session.views.keySet().stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no workers configured"));
        session.lastUsedMillis = System.currentTimeMillis();
        sessions.put(normalized, session);
        pruneSessions();
        return session;
    }

    private SessionState session(String clientId) {
        String normalized = normalizeClientId(clientId);
        SessionState session = sessions.get(normalized);
        if (session == null) {
            session = ensureSession(normalized);
            applyPolicies();
            persist();
        }
        session.focusSectorId = validFocus(session.focusSectorId);
        session.views.put(session.focusSectorId, ViewMode.FULL);
        return session;
    }

    private String validFocus(String focus) {
        if (focus != null && manager.find(focus) != null) return focus;
        return sessions.values().stream()
            .flatMap(s -> s.views.keySet().stream())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no workers configured"));
    }

    private void touch(SessionState session) {
        session.lastUsedMillis = System.currentTimeMillis();
    }

    private void pruneSessions() {
        for (String clientId : new LinkedHashMap<>(sessions).keySet()) {
            if (!authSessions.hasClient(clientId)) {
                removeSession(clientId);
            }
        }
    }

    private void removeSession(String clientId) {
        sessions.remove(clientId);
        for (String sectorId : new LinkedHashMap<>(leases).keySet()) {
            ControlLease lease = leases.get(sectorId);
            if (lease != null && lease.ownerId.equals(clientId)) leases.remove(sectorId);
        }
    }

    private Map<String, ViewMode> effectiveViews() {
        LinkedHashMap<String, ViewMode> result = new LinkedHashMap<>();
        for (ManagedWorker worker : manager.workers()) {
            result.put(worker.config().id(), effectiveViewMode(worker.config().id()));
        }
        return Map.copyOf(result);
    }

    private void applyPolicies() {
        for (ManagedWorker worker : manager.workers()) {
            worker.setViewPolicy(effectiveViewMode(worker.config().id()));
        }
    }

    private void persist() {
        Properties properties = new Properties();
        for (Map.Entry<String, SessionState> entry : sessions.entrySet()) {
            String prefix = "session." + entry.getKey() + ".";
            SessionState session = entry.getValue();
            properties.setProperty(prefix + "focusSectorId", session.focusSectorId);
            for (Map.Entry<String, ViewMode> view : session.views.entrySet()) {
                properties.setProperty(prefix + "sector." + view.getKey() + ".viewMode", view.getValue().name());
            }
        }
        authSessions.write(properties);
        inputReceipts.prune(System.currentTimeMillis());
        inputReceipts.write(properties);
        Path temporary = null;
        try {
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "gateway-", ".properties.tmp");
            try (OutputStream output = Files.newOutputStream(temporary);
                 OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                properties.store(writer, "BetterMindustry Gateway sessions");
            }
            try {
                Files.move(temporary, stateFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (AccessDeniedException e) {
                try (OutputStream output = Files.newOutputStream(stateFile);
                     OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                    properties.store(writer, "BetterMindustry Gateway sessions");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist gateway state", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void expireLeases(long now) {
        leases.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMillis);
    }

    private void releaseOwnedControl(String sectorId, String clientId) {
        ControlLease lease = leases.get(sectorId);
        if (lease != null && lease.ownerId.equals(clientId)) leases.remove(sectorId);
    }

    private void requireWorker(String sectorId) {
        if (sectorId == null || manager.find(sectorId) == null) {
            throw new IllegalArgumentException("unknown sector: " + sectorId);
        }
    }

    private void requireClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client id is required");
        }
    }

    private static void requirePlayer(Role role) {
        if (role == null || role == Role.GUEST) {
            throw new InputDeniedException("player account required");
        }
    }

    private static String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("client id is required");
        }
        String value = clientId.trim();
        return value.length() > 96 ? value.substring(0, 96) : value;
    }

    private static final class SessionState {
        String focusSectorId;
        long lastUsedMillis;
        final LinkedHashMap<String, ViewMode> views = new LinkedHashMap<>();
    }

    private record ControlLease(String ownerId, long expiresAtMillis) {
    }

    public record InputAudit(
        long timestampMillis,
        String clientId,
        String sectorId,
        String command,
        String detail
    ) {
    }

    public static final class InputDeniedException extends IllegalStateException {
        public InputDeniedException(String message) {
            super(message);
        }
    }

    public static final class InputConflictException extends IllegalStateException {
        public InputConflictException(String message) {
            super(message);
        }
    }

    record InputAdmission(
        ManagedWorker worker,
        InputReceiptBook.Receipt receipt,
        boolean duplicate
    ) {
    }

    record Identity(String clientId, String username, Role role, long expiresAtMillis) {
    }

    public record Snapshot(
        String clientId,
        String focusSectorId,
        Map<String, ViewMode> views,
        Map<String, ViewMode> effectiveViews,
        String controlOwnerId,
        long controlExpiresAtMillis,
        long controlExpiresInMillis,
        List<InputAudit> recentInputs
    ) {
    }
}
