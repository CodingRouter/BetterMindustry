package io.bettermindustry.orchestrator;

import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkerManager {
    private final Path projectRoot;
    private final Path catalogFile;
    private final Path serverJar;
    private final Map<String, ManagedWorker> workers = new LinkedHashMap<>();

    public WorkerManager(Path projectRoot, Path catalogFile) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.serverJar = this.projectRoot.resolve("server/build/libs/server-release.jar");
        this.catalogFile = catalogFile.toAbsolutePath().normalize();
        for (WorkerConfig config : WorkerCatalog.load(this.projectRoot, this.catalogFile)) {
            register(config);
        }
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Path serverJar() {
        return serverJar;
    }

    public Path catalogFile() {
        return catalogFile;
    }

    public Collection<ManagedWorker> workers() {
        return workers.values();
    }

    public ManagedWorker find(String id) {
        return workers.get(id);
    }

    public List<String> startAll() {
        List<String> result = new ArrayList<>();
        for (ManagedWorker worker : workers.values()) {
            result.add(worker.start());
        }
        return result;
    }

    public List<String> stopAll() {
        List<String> result = new ArrayList<>();
        for (ManagedWorker worker : workers.values()) {
            result.add(worker.requestStop());
        }
        return result;
    }

    private void register(WorkerConfig config) {
        workers.put(config.id(), new ManagedWorker(config, serverJar));
    }
}
