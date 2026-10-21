package io.bettermindustry.orchestrator;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class DebuggerMain {
    private DebuggerMain() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("bettermindustry.root",
            System.getProperty("user.dir"))).toAbsolutePath().normalize();
        int port = 19090;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--root".equals(args[i]) && i + 1 < args.length) {
                root = Path.of(args[++i]).toAbsolutePath().normalize();
            }
        }

        WorkerManager manager = new WorkerManager(root);
        DashboardServer dashboard = new DashboardServer(manager, port);
        dashboard.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            manager.stopAll();
            dashboard.stop();
        }, "debugger-shutdown"));

        System.out.println("BetterMindustry debugger: http://127.0.0.1:" + port + "/");
        System.out.println("Project root: " + manager.projectRoot());
        System.out.println("Server jar: " + manager.serverJar());
        manager.startAll().forEach(System.out::println);

        new CountDownLatch(1).await();
    }
}
