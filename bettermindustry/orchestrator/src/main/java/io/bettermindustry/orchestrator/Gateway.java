package io.bettermindustry.orchestrator;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Gateway {
    private final WorkerManager manager;
    private final LinkedHashMap<String, ViewMode> views = new LinkedHashMap<>();
    private String focusSectorId;

    public Gateway(WorkerManager manager) {
        this.manager = manager;
        for (ManagedWorker worker : manager.workers()) {
            views.put(worker.config().id(), ViewMode.FULL);
        }
        focusSectorId = views.keySet().stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no workers configured"));
    }

    public synchronized String focusSectorId() {
        return focusSectorId;
    }

    public synchronized ViewMode viewMode(String sectorId) {
        return views.getOrDefault(sectorId, ViewMode.OFF);
    }

    public synchronized void setFocus(String sectorId) {
        requireWorker(sectorId);
        focusSectorId = sectorId;
        views.put(sectorId, ViewMode.FULL);
    }

    public synchronized void setViewMode(String sectorId, ViewMode mode) {
        requireWorker(sectorId);
        if (sectorId.equals(focusSectorId) && mode == ViewMode.OFF) {
            throw new IllegalArgumentException("cannot close the focus sector");
        }
        views.put(sectorId, mode);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(focusSectorId, Map.copyOf(views));
    }

    private void requireWorker(String sectorId) {
        if (sectorId == null || manager.find(sectorId) == null) {
            throw new IllegalArgumentException("unknown sector: " + sectorId);
        }
    }

    public record Snapshot(String focusSectorId, Map<String, ViewMode> views) {
    }
}
