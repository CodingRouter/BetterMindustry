package io.bettermindustry.orchestrator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class WorkerCatalog {
    private WorkerCatalog() {
    }

    public static List<WorkerConfig> load(Path projectRoot, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Worker catalog not found: " + file);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String list = required(properties, "workers");
        List<WorkerConfig> workers = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Integer> ports = new HashSet<>();
        Set<Path> directories = new HashSet<>();

        for (String rawId : list.split(",")) {
            String id = rawId.trim();
            if (id.isEmpty()) continue;
            if (!id.matches("[a-z0-9][a-z0-9-]*")) {
                throw new IllegalArgumentException("Invalid worker id: " + id);
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate worker id: " + id);
            }

            String map = required(properties, id + ".map");
            int port;
            try {
                port = Integer.parseInt(required(properties, id + ".port"));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port for worker " + id, e);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port out of range for worker " + id + ": " + port);
            }
            if (!ports.add(port)) {
                throw new IllegalArgumentException("Duplicate worker port: " + port);
            }

            String runtimeValue = required(properties, id + ".runtimeDir");
            Path runtime = Path.of(runtimeValue);
            runtime = runtime.isAbsolute() ? runtime.normalize() : projectRoot.resolve(runtime).normalize();
            if (!runtime.startsWith(projectRoot)) {
                throw new IllegalArgumentException("Worker runtime directory escapes project root: " + runtime);
            }
            if (!directories.add(runtime)) {
                throw new IllegalArgumentException("Duplicate worker runtime directory: " + runtime);
            }

            workers.add(new WorkerConfig(id, map, port, runtime));
        }

        if (workers.isEmpty()) {
            throw new IllegalArgumentException("Worker catalog contains no workers");
        }
        return List.copyOf(workers);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required worker config: " + key);
        }
        return value.trim();
    }
}
