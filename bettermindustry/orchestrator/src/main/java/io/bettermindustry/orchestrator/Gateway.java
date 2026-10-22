package io.bettermindustry.orchestrator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class Gateway {
    private static final long CONTROL_LEASE_MILLIS = 15_000L;
    private static final int MAX_SESSIONS = 64;
    private static final String DEFAULT_CLIENT_ID = "local-debugger";

    private final WorkerManager manager;
    private final Path stateFile;
    private final LinkedHashMap<String, SessionState> sessions = new LinkedHashMap<>();
    private final LinkedHashMap<String, ControlLease> leases = new LinkedHashMap<>();

    public Gateway(WorkerManager manager, Path stateFile) {
        this.manager = manager;
        this.stateFile = stateFile.toAbsolutePath().normalize();
        load();
        ensureSession(DEFAULT_CLIENT_ID);
        applyPolicies();
        persist();
    }

    public synchronized Path stateFile() {
        return stateFile;
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
            remaining
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

            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("session.")) continue;
                String[] parts = key.split("\\.");
                if (parts.length < 3) continue;
                String clientId = parts[1];
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

            if (!properties.getProperty("focusSectorId", "").isBlank()) {
                SessionState legacy = ensureSession(DEFAULT_CLIENT_ID);
                String focus = properties.getProperty("focusSectorId");
                if (manager.find(focus) != null) legacy.focusSectorId = focus;
                for (String sectorId : legacy.views.keySet()) {
                    String value = properties.getProperty("sector." + sectorId + ".viewMode");
                    if (value != null) {
                        try {
                            legacy.views.put(sectorId, ViewMode.parse(value));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
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
        while (sessions.size() > MAX_SESSIONS) {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, SessionState> entry : sessions.entrySet()) {
                if (!DEFAULT_CLIENT_ID.equals(entry.getKey()) && entry.getValue().lastUsedMillis < oldestTime) {
                    oldest = entry.getKey();
                    oldestTime = entry.getValue().lastUsedMillis;
                }
            }
            if (oldest == null) return;
            sessions.remove(oldest);
            for (String sectorId : new LinkedHashMap<>(leases).keySet()) {
                ControlLease lease = leases.get(sectorId);
                if (lease != null && lease.ownerId.equals(oldest)) leases.remove(sectorId);
            }
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
        try {
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(stateFile);
                 OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                properties.store(writer, "BetterMindustry Gateway sessions");
            }
        } catch (IOException ignored) {
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

    private static String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) return DEFAULT_CLIENT_ID;
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

    public record Snapshot(
        String clientId,
        String focusSectorId,
        Map<String, ViewMode> views,
        Map<String, ViewMode> effectiveViews,
        String controlOwnerId,
        long controlExpiresAtMillis,
        long controlExpiresInMillis
    ) {
    }
}
