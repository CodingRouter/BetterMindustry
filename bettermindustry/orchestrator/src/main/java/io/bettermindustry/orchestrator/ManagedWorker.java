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

public class ManagedWorker {
    public enum State {
        STOPPED, STARTING, RUNNING, STOPPING, FAILED
    }

    private static final int MAX_LOG_LINES = 240;
    private static final String CHECKPOINT_NAME = "debugger-checkpoint";

    private final WorkerConfig config;
    private final Path serverJar;
    private final List<String> logs = Collections.synchronizedList(new ArrayList<>());

    private volatile Process process;
    private volatile State state = State.STOPPED;
    private volatile boolean stopRequested;
    private volatile boolean startupSent;
    private volatile Instant startedAt;
    private volatile Instant stoppedAt;
    private volatile int exitCode = Integer.MIN_VALUE;
    private volatile boolean portOpen;
    private BufferedWriter stdin;

    public ManagedWorker(WorkerConfig config, Path serverJar) {
        this.config = config;
        this.serverJar = serverJar;
    }

    public WorkerConfig config() {
        return config;
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

    public Snapshot snapshot() {
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
            tail
        );
    }

    private void readOutput(Process source) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = stripAnsi(line);
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

    private boolean probePort() {
        Process current = process;
        if (current == null || !current.isAlive()) return false;
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
        List<String> logs
    ) {
    }
}
