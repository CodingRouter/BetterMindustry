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

    private final WorkerManager manager;
    private final Path stateFile;
    private final LinkedHashMap<String, ViewMode> views = new LinkedHashMap<>();
    private String focusSectorId;
    private String controlOwnerId;
    private long controlExpiresAtMillis;

    public Gateway(WorkerManager manager, Path stateFile) {
        this.manager = manager;
        this.stateFile = stateFile.toAbsolutePath().normalize();
        load();
        applyPolicies();
        persist();
    }

    public synchronized String focusSectorId() {
        return focusSectorId;
    }

    public synchronized Path stateFile() {
        return stateFile;
    }

    public synchronized ViewMode viewMode(String sectorId) {
        return views.getOrDefault(sectorId, ViewMode.OFF);
    }

    public synchronized void setFocus(String sectorId) {
        requireWorker(sectorId);
        if (!sectorId.equals(focusSectorId)) {
            clearControl();
        }
        focusSectorId = sectorId;
        views.put(sectorId, ViewMode.FULL);
        applyPolicies();
        persist();
    }

    public synchronized Snapshot acquireControl(String sectorId, String clientId) {
        requireWorker(sectorId);
        requireClient(clientId);
        if (!sectorId.equals(focusSectorId)) {
            throw new IllegalStateException("control can only be acquired for the focus sector");
        }
        long now = System.currentTimeMillis();
        expireControl(now);
        if (controlOwnerId != null && !controlOwnerId.equals(clientId)) {
            throw new IllegalStateException("control is held by " + controlOwnerId);
        }
        controlOwnerId = clientId;
        controlExpiresAtMillis = now + CONTROL_LEASE_MILLIS;
        return snapshot();
    }

    public synchronized Snapshot releaseControl(String sectorId, String clientId) {
        requireWorker(sectorId);
        requireClient(clientId);
        long now = System.currentTimeMillis();
        expireControl(now);
        if (controlOwnerId != null && !controlOwnerId.equals(clientId)) {
            throw new IllegalStateException("control is held by " + controlOwnerId);
        }
        if (controlOwnerId != null) {
            clearControl();
        }
        return snapshot();
    }

    public synchronized void setViewMode(String sectorId, ViewMode mode) {
        requireWorker(sectorId);
        if (sectorId.equals(focusSectorId) && mode == ViewMode.OFF) {
            throw new IllegalArgumentException("cannot close the focus sector");
        }
        views.put(sectorId, mode);
        applyPolicies();
        persist();
    }

    public synchronized Snapshot snapshot() {
        long now = System.currentTimeMillis();
        expireControl(now);
        long remaining = controlOwnerId == null ? 0 : Math.max(0, controlExpiresAtMillis - now);
        return new Snapshot(focusSectorId, Map.copyOf(views), controlOwnerId, controlExpiresAtMillis, remaining);
    }

    private void load() {
        for (ManagedWorker worker : manager.workers()) {
            views.put(worker.config().id(), ViewMode.FULL);
        }

        if (Files.isRegularFile(stateFile)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(stateFile);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException ignored) {
            }

            for (String sectorId : views.keySet()) {
                String value = properties.getProperty("sector." + sectorId + ".viewMode");
                if (value != null) {
                    try {
                        views.put(sectorId, ViewMode.parse(value));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            focusSectorId = properties.getProperty("focusSectorId");
        }

        if (focusSectorId == null || !views.containsKey(focusSectorId)) {
            focusSectorId = views.keySet().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no workers configured"));
        }
        views.put(focusSectorId, ViewMode.FULL);
    }

    private void persist() {
        Properties properties = new Properties();
        properties.setProperty("focusSectorId", focusSectorId);
        for (Map.Entry<String, ViewMode> entry : views.entrySet()) {
            properties.setProperty("sector." + entry.getKey() + ".viewMode", entry.getValue().name());
        }
        try {
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(stateFile);
                 OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                properties.store(writer, "BetterMindustry Gateway state");
            }
        } catch (IOException ignored) {
        }
    }

    private void applyPolicies() {
        for (Map.Entry<String, ViewMode> entry : views.entrySet()) {
            ManagedWorker worker = manager.find(entry.getKey());
            if (worker != null) worker.setViewPolicy(entry.getValue());
        }
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

    private void expireControl(long now) {
        if (controlOwnerId != null && now >= controlExpiresAtMillis) {
            clearControl();
        }
    }

    private void clearControl() {
        controlOwnerId = null;
        controlExpiresAtMillis = 0;
    }

    public record Snapshot(
        String focusSectorId,
        Map<String, ViewMode> views,
        String controlOwnerId,
        long controlExpiresAtMillis,
        long controlExpiresInMillis
    ) {
    }
}
