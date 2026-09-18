package com.Shaurya.miniredis;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int port;
    private final CommandParser parser;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public Server(int port, CommandParser parser) {
        this.port = port;
        this.parser = parser;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Mini-Redis server listening on port " + port);

            while(true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                pool.submit(() -> handleClient(clientSocket));
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = parser.execute(line);
                out.println(response);
            }
        } catch (IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
            System.out.println("Client disconnected");
        }
    }
}