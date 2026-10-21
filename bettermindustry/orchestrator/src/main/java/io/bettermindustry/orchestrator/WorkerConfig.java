package io.bettermindustry.orchestrator;

import java.nio.file.Path;

public record WorkerConfig(String id, String map, int port, Path runtimeDir) {
    public WorkerConfig {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("worker id is required");
        if (map == null || map.isBlank()) throw new IllegalArgumentException("worker map is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid worker port: " + port);
        if (runtimeDir == null) throw new IllegalArgumentException("worker runtime dir is required");
    }

    public Path checkpoint() {
        return runtimeDir.resolve("config/saves/debugger-checkpoint.msav");
    }

    public Path logFile() {
        return runtimeDir.resolve("debugger-worker.log");
    }
}
