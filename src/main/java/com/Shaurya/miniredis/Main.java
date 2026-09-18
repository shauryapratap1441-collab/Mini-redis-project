package com.Shaurya.miniredis;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String SNAPSHOT_FILE = "dump.rdb";
    private static final int SNAPSHOT_INTERVAL_SECONDS = 30;

    public static void main(String[] args) throws Exception {
        Storage storage = new Storage();
        storage.loadSnapshot(SNAPSHOT_FILE);

        ScheduledExecutorService snapshotScheduler = Executors.newSingleThreadScheduledExecutor();
        snapshotScheduler.scheduleAtFixedRate(
                () -> storage.saveSnapshot(SNAPSHOT_FILE),
                SNAPSHOT_INTERVAL_SECONDS,
                SNAPSHOT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        // Runs on Ctrl+C or a container/host sending SIGTERM (e.g. during
        // deployment). Without this, up to 30s of writes could be lost on
        // every restart.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down, saving snapshot...");
            storage.saveSnapshot(SNAPSHOT_FILE);
            snapshotScheduler.shutdown();
        }));

        CommandParser parser = new CommandParser(storage);

        // The raw TCP server (telnet-compatible, real Redis protocol) runs
        // on a background thread so the JVM can also run the web server.
        Server tcpServer = new Server(6379, parser);
        Thread tcpThread = new Thread(() -> {
            try {
                tcpServer.start();
            } catch (Exception e) {
                System.out.println("TCP server failed: " + e.getMessage());
            }
        }, "tcp-server");
        tcpThread.setDaemon(true);
        tcpThread.start();

        // Most PaaS platforms (Render, Railway, etc.) assign a public port
        // via the PORT env var and only expose that one port - the browser
        // demo needs to bind to it. Defaults to 8080 for local runs.
        int webPort = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        WebServer webServer = new WebServer(webPort, parser);
        webServer.start(); // blocks the main thread, keeping the JVM alive
    }
}