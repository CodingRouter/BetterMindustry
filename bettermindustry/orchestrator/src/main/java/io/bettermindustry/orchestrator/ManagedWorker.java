package io.bettermindustry.orchestrator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManagedWorker {
    public enum State {
        STOPPED, STARTING, RUNNING, STOPPING, FAILED
    }

    private static final int MAX_LOG_LINES = 240;
    private static final String CHECKPOINT_NAME = "debugger-checkpoint";
    private static final long ACTION_TIMEOUT_MILLIS = 3_000L;

    private final WorkerConfig config;
    private final Path serverJar;
    private final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, CompletableFuture<WorkerActionAck>> pendingActions =
        new ConcurrentHashMap<>();

    private volatile Process process;
    private volatile State state = State.STOPPED;
    private volatile boolean stopRequested;
    private volatile boolean startupSent;
    private volatile Instant startedAt;
    private volatile Instant stoppedAt;
    private volatile int exitCode = Integer.MIN_VALUE;
    private volatile boolean portOpen;
    private volatile long lastPortProbeNanos;
    private volatile long lastDiagnosticRequestNanos;
    private volatile long lastWorldRequestNanos;
    private volatile long diagnosticIntervalNanos = 1_000_000_000L;
    private volatile long worldIntervalNanos = 2_000_000_000L;
    private volatile boolean worldRequestsEnabled = true;
    private volatile long worldSnapshotUpdatedNanos;
    private volatile double worldSnapshotTick = -1;
    private volatile Diagnostics diagnostics;
    private BufferedWriter stdin;

    public ManagedWorker(WorkerConfig config, Path serverJar) {
        this.config = config;
        this.serverJar = serverJar;
    }

    public WorkerConfig config() {
        return config;
    }

    public synchronized void setViewPolicy(ViewMode mode) {
        switch (mode) {
            case FULL -> {
                diagnosticIntervalNanos = 1_000_000_000L;
                worldIntervalNanos = 2_000_000_000L;
                worldRequestsEnabled = true;
            }
            case SUMMARY -> {
                diagnosticIntervalNanos = 2_500_000_000L;
                worldIntervalNanos = 5_000_000_000L;
                worldRequestsEnabled = true;
            }
            case OFF -> {
                diagnosticIntervalNanos = 5_000_000_000L;
                worldIntervalNanos = 0;
                worldRequestsEnabled = false;
            }
        }
        lastDiagnosticRequestNanos = 0;
        lastWorldRequestNanos = 0;
    }

    public synchronized String start() {
        if (process != null && process.isAlive()) {
            return "Worker " + config.id() + " is already running";
        }
        if (!Files.isRegularFile(serverJar)) {
            state = State.FAILED;
            addLog("Server jar not found: " + serverJar);
            return "Server jar not found: " + serverJar;
        }

        try {
            Files.createDirectories(config.runtimeDir());
            stopRequested = false;
            startupSent = false;
            exitCode = Integer.MIN_VALUE;
            stoppedAt = null;
            diagnostics = null;
            lastPortProbeNanos = 0;
            lastDiagnosticRequestNanos = 0;
            lastWorldRequestNanos = 0;
            worldSnapshotUpdatedNanos = 0;
            worldSnapshotTick = -1;
            failPendingActions("Worker restarted");
            state = State.STARTING;
            startedAt = Instant.now();

            Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
            ProcessBuilder builder = new ProcessBuilder(
                java.toString(),
                "-Dfile.encoding=UTF-8",
                "-jar",
                serverJar.toString()
            );
            builder.directory(config.runtimeDir().toFile());
            builder.redirectErrorStream(true);

            Process started = builder.start();
            process = started;
            stdin = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
            addLog("Started PID " + started.pid() + " in " + config.runtimeDir());

            Thread reader = new Thread(() -> readOutput(started), "worker-output-" + config.id());
            reader.setDaemon(true);
            reader.start();

            Thread waiter = new Thread(() -> waitForExit(started), "worker-wait-" + config.id());
            waiter.setDaemon(true);
            waiter.start();
            return "Starting " + config.id();
        } catch (Exception e) {
            state = State.FAILED;
            addLog("Failed to start worker: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "Failed to start " + config.id() + ": " + e.getMessage();
        }
    }

    public synchronized String requestStop() {
        if (process == null || !process.isAlive()) {
            state = State.STOPPED;
            return "Worker " + config.id() + " is not running";
        }

        stopRequested = true;
        state = State.STOPPING;
        sendCommand("save " + CHECKPOINT_NAME);
        sendCommand("exit");
        return "Stopping " + config.id();
    }

    public synchronized String saveCheckpoint() {
        if (process == null || !process.isAlive()) {
            return "Worker " + config.id() + " is not running";
        }
        sendCommand("save " + CHECKPOINT_NAME);
        return "Saving " + config.id();
    }

    public synchronized String sendCommand(String command) {
        if (command == null || command.isBlank()) return "Command is empty";
        if (process == null || !process.isAlive() || stdin == null) {
            return "Worker " + config.id() + " is not running";
        }

        String line = command.replace("\r", "").replace("\n", " ").trim();
        try {
            stdin.write(line);
            stdin.newLine();
            stdin.flush();
            addLog("> " + line);
            return "Sent";
        } catch (IOException e) {
            addLog("Failed to send command: " + e.getMessage());
            return "Failed to send command: " + e.getMessage();
        }
    }

    public ActionResult executeAction(String commandId, String type, String message) {
        if (commandId == null || !commandId.matches("[A-Za-z0-9_-]{1,96}")) {
            return ActionResult.failed("Invalid command ID");
        }
        if (type == null || !type.matches("[a-z]+")) {
            return ActionResult.failed("Invalid action type");
        }
        if (process == null || !process.isAlive()) {
            return ActionResult.failed("Worker " + config.id() + " is not running");
        }

        CompletableFuture<WorkerActionAck> future = new CompletableFuture<>();
        if (pendingActions.putIfAbsent(commandId, future) != null) {
            return ActionResult.failed("Action is already pending");
        }

        String command = "bmaction " + commandId + " " + type;
        if (message != null && !message.isBlank()) {
            command += " " + message;
        }
        String sent = sendCommand(command);
        if (!"Sent".equals(sent)) {
            pendingActions.remove(commandId, future);
            return ActionResult.failed(sent);
        }

        try {
            WorkerActionAck ack = future.get(ACTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            boolean applied = ack.status() == WorkerActionAck.Status.APPLIED;
            return new ActionResult(applied, ack.detail(), ack);
        } catch (TimeoutException e) {
            pendingActions.remove(commandId, future);
            return ActionResult.failed("Worker action acknowledgement timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingActions.remove(commandId, future);
            return ActionResult.failed("Worker action interrupted");
        } catch (ExecutionException e) {
            pendingActions.remove(commandId, future);
            return ActionResult.failed("Worker action failed: " + e.getCause());
        } catch (RuntimeException e) {
            pendingActions.remove(commandId, future);
            return ActionResult.failed("Worker action failed: " + e.getMessage());
        }
    }

    public Snapshot snapshot() {
        requestDiagnosticsIfDue();
        requestWorldSnapshotIfDue();
        List<String> tail;
        synchronized (logs) {
            int from = Math.max(0, logs.size() - MAX_LOG_LINES);
            tail = new ArrayList<>(logs.subList(from, logs.size()));
        }
        Process current = process;
        boolean listening = probePort();
        if (listening && !portOpen) {
            addLog("Port " + config.port() + " is listening");
        }
        portOpen = listening;
        boolean worldDataAvailable = Files.isRegularFile(config.worldImage())
            && Files.isRegularFile(config.worldMetadata());
        long worldSnapshotAgeMillis = worldSnapshotUpdatedNanos == 0 ? -1
            : (System.nanoTime() - worldSnapshotUpdatedNanos) / 1_000_000L;
        long worldSnapshotVersion = -1;
        if (worldDataAvailable) {
            try {
                worldSnapshotVersion = Files.getLastModifiedTime(config.worldMetadata()).toMillis();
            } catch (IOException ignored) {
            }
        }
        return new Snapshot(
            config.id(),
            config.map(),
            config.port(),
            config.runtimeDir().toString(),
            state.name(),
            current != null && current.isAlive(),
            current == null ? null : current.pid(),
            startedAt == null ? 0 : Duration.between(startedAt, Instant.now()).toSeconds(),
            exitCode == Integer.MIN_VALUE ? null : exitCode,
            listening,
            Files.isRegularFile(config.checkpoint()),
            diagnostics,
            worldDataAvailable,
            worldSnapshotTick,
            worldSnapshotAgeMillis,
            worldSnapshotVersion,
            tail
        );
    }

    private void readOutput(Process source) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = stripAnsi(line);
                int diagnosticStart = clean.indexOf("BMSTATUS ");
                if (diagnosticStart >= 0) {
                    updateDiagnostics(clean.substring(diagnosticStart + "BMSTATUS ".length()));
                    continue;
                }
                int worldStart = clean.indexOf("BMWORLD ");
                if (worldStart >= 0) {
                    updateWorldSnapshot(clean.substring(worldStart + "BMWORLD ".length()));
                    continue;
                }
                int actionStart = clean.indexOf("BMACTION ");
                if (actionStart >= 0) {
                    updateActionAck(clean.substring(actionStart + "BMACTION ".length()));
                    continue;
                }
                addLog(clean);
                if (!startupSent && clean.contains("Server loaded.")) {
                    startupSent = true;
                    sendStartupCommands();
                }
                if (clean.contains("Opened a server on port")) {
                    state = State.RUNNING;
                }
            }
        } catch (IOException e) {
            addLog("Output reader stopped: " + e.getMessage());
        }
    }

    private void updateActionAck(String json) {
        try {
            WorkerActionAck ack = WorkerActionAck.parse(json);
            CompletableFuture<WorkerActionAck> future = pendingActions.remove(ack.commandId());
            if (future == null) {
                addLog("Unexpected action acknowledgement: " + ack.commandId());
            } else {
                future.complete(ack);
            }
        } catch (RuntimeException e) {
            addLog("Failed to parse bmaction: " + e.getMessage());
        }
    }

    private void sendStartupCommands() {
        sendCommand("config port " + config.port());
        sendCommand("config autoPause off");
        sendCommand("config autosave on");
        sendCommand("config autosaveSpacing 30");
        if (Files.isRegularFile(config.checkpoint())) {
            addLog("Checkpoint found; loading " + CHECKPOINT_NAME);
            sendCommand("load " + CHECKPOINT_NAME);
        } else {
            addLog("No checkpoint; hosting map " + config.map());
            sendCommand("host " + config.map() + " survival");
        }
    }

    private synchronized void requestDiagnosticsIfDue() {
        Process current = process;
        if (current == null || !current.isAlive()) return;
        long now = System.nanoTime();
        if (now - lastDiagnosticRequestNanos >= diagnosticIntervalNanos) {
            lastDiagnosticRequestNanos = now;
            writeCommand("bmstatus", false);
        }
    }

    private synchronized void requestWorldSnapshotIfDue() {
        Process current = process;
        if (current == null || !current.isAlive()) return;
        if (!worldRequestsEnabled) return;
        long now = System.nanoTime();
        if (now - lastWorldRequestNanos >= worldIntervalNanos) {
            lastWorldRequestNanos = now;
            writeCommand("bmworld", false);
        }
    }

    private void updateWorldSnapshot(String value) {
        try {
            String token = value.trim().split("\\s+")[0];
            worldSnapshotTick = Double.parseDouble(token);
            worldSnapshotUpdatedNanos = System.nanoTime();
        } catch (RuntimeException e) {
            addLog("Failed to parse bmworld: " + e.getMessage());
        }
    }

    private void updateDiagnostics(String json) {
        try {
            diagnostics = new Diagnostics(
                number(json, "tick"),
                number(json, "gameTimeSeconds"),
                integer(json, "updateId"),
                integer(json, "wave"),
                number(json, "waveTimeSeconds"),
                bool(json, "paused"),
                bool(json, "gameOver"),
                bool(json, "playing"),
                integer(json, "players"),
                integer(json, "units"),
                integer(json, "enemies"),
                integer(json, "buildings"),
                string(json, "map"),
                Instant.now()
            );
        } catch (RuntimeException e) {
            addLog("Failed to parse bmstatus: " + e.getMessage());
        }
    }

    private void waitForExit(Process source) {
        try {
            int code = source.waitFor();
            synchronized (this) {
                if (process != source) return;
                exitCode = code;
                stoppedAt = Instant.now();
                if (stopRequested) {
                    state = State.STOPPED;
                    portOpen = false;
                    addLog("Worker stopped with exit code " + code);
                } else {
                    state = State.FAILED;
                    portOpen = false;
                    addLog("Worker exited unexpectedly with code " + code);
                }
                failPendingActions("Worker exited before action acknowledgement");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void addLog(String line) {
        synchronized (logs) {
            logs.add(line);
            while (logs.size() > MAX_LOG_LINES) logs.remove(0);
        }
    }

    private void failPendingActions(String detail) {
        for (String commandId : new ArrayList<>(pendingActions.keySet())) {
            CompletableFuture<WorkerActionAck> future = pendingActions.remove(commandId);
            if (future != null) {
                future.complete(new WorkerActionAck(
                    WorkerActionAck.VERSION,
                    commandId,
                    WorkerActionAck.Status.FAILED,
                    diagnostics == null ? 0 : diagnostics.tick(),
                    diagnostics != null && diagnostics.paused(),
                    detail,
                    Instant.now()
                ));
            }
        }
    }

    private synchronized void writeCommand(String command, boolean echo) {
        if (command == null || command.isBlank()) return;
        Process current = process;
        if (current == null || !current.isAlive() || stdin == null) return;
        String line = command.replace("\r", "").replace("\n", " ").trim();
        try {
            stdin.write(line);
            stdin.newLine();
            stdin.flush();
            if (echo) addLog("> " + line);
        } catch (IOException e) {
            addLog("Failed to send command: " + e.getMessage());
        }
    }

    private static double number(String json, String key) {
        Matcher matcher = fieldPattern(key).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing numeric field: " + key);
        return Double.parseDouble(matcher.group(1));
    }

    private static int integer(String json, String key) {
        return (int) number(json, key);
    }

    private static boolean bool(String json, String key) {
        Matcher matcher = fieldPattern(key).matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing boolean field: " + key);
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String string(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("missing string field: " + key);
        return matcher.group(1);
    }

    private static Pattern fieldPattern(String key) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\":([-0-9.eE]+|true|false)");
    }

    private boolean probePort() {
        Process current = process;
        if (current == null || !current.isAlive()) return false;
        long now = System.nanoTime();
        if (now - lastPortProbeNanos < 1_000_000_000L) return portOpen;
        lastPortProbeNanos = now;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", config.port()), 250);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String stripAnsi(String line) {
        return line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    public record Snapshot(
        String id,
        String map,
        int port,
        String runtimeDir,
        String state,
        boolean alive,
        Long pid,
        long uptimeSeconds,
        Integer exitCode,
        boolean portOpen,
        boolean checkpoint,
        Diagnostics diagnostics,
        boolean worldDataAvailable,
        double worldSnapshotTick,
        long worldSnapshotAgeMillis,
        long worldSnapshotVersion,
        List<String> logs
    ) {
    }

    public record Diagnostics(
        double tick,
        double gameTimeSeconds,
        long updateId,
        int wave,
        double waveTimeSeconds,
        boolean paused,
        boolean gameOver,
        boolean playing,
        int players,
        int units,
        int enemies,
        int buildings,
        String map,
        Instant receivedAt
    ) {
    }

    public record ActionResult(boolean applied, String detail, WorkerActionAck acknowledgement) {
        static ActionResult failed(String detail) {
            return new ActionResult(false, detail, null);
        }
    }
}
