package com.Shaurya.miniredis;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A minimal HTTP + WebSocket server, written the same way Server.java
 * handles the raw Redis TCP protocol: plain java.net sockets, no frameworks.
 *
 * It serves two things on one port, so a single free-tier deployment works:
 *  - GET /        -> the browser terminal frontend (Frontend.INDEX_HTML)
 *  - Upgrade: websocket -> a WebSocket connection bridged directly into
 *                          CommandParser, so browser commands run through
 *                          the exact same code path as a telnet client on
 *                          the TCP port.
 *
 * This hand-rolls the RFC 6455 handshake and frame format instead of using a
 * library, since the protocol needed here is tiny: one text frame in, one
 * text frame out, matching the existing line-based Redis protocol.
 */
public class WebServer {
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final CommandParser parser;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public WebServer(int port, CommandParser parser) {
        this.port = port;
        this.parser = parser;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Web/WebSocket server listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handleConnection(socket));
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            String requestLine = readHttpLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                socket.close();
                return;
            }

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readHttpLine(in)) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(), line.substring(idx + 1).trim());
                }
            }

            String upgrade = headers.get("upgrade");
            if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
                handleWebSocketSession(socket, in, out, headers);
            } else {
                serveFrontend(out);
                socket.close();
            }
        } catch (IOException e) {
            // client disconnected mid-request; nothing to do
        }
    }

    /**
     * Reads one \r\n-terminated line directly off the socket, one byte at a
     * time. Deliberately NOT using a BufferedReader here: a BufferedReader
     * pulls a whole chunk from the stream on each read, which risks
     * swallowing the first bytes of the WebSocket frames that follow right
     * after the handshake headers. Reading byte-by-byte keeps the stream
     * position exactly where the HTTP headers end.
     */
    private String readHttpLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int prev = -1;
        int b;
        while ((b = in.read()) != -1) {
            if (prev == '\r' && b == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8); // drop trailing \r
            }
            line.write(b);
            prev = b;
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
    }

    private void serveFrontend(OutputStream out) throws IOException {
        byte[] body = Frontend.INDEX_HTML.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private void handleWebSocketSession(Socket socket, InputStream in, OutputStream out,
                                         Map<String, String> headers) throws IOException {
        String key = headers.get("sec-websocket-key");
        if (key == null) {
            socket.close();
            return;
        }

        String acceptKey;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            acceptKey = Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            socket.close();
            return;
        }

        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();

        // From this point on, the socket speaks WebSocket frames, not HTTP.
        try {
            while (true) {
                String message = readTextFrame(in);
                if (message == null) break; // client sent a close frame or hung up
                String reply = parser.execute(message.trim());
                writeTextFrame(out, reply);
            }
        } finally {
            socket.close();
        }
    }

    /** Reads one WebSocket text frame from the client. Returns null on close/EOF. */
    private String readTextFrame(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null;
        int opcode = b1 & 0x0F;
        if (opcode == 0x8) return null; // close frame

        int b2 = in.read();
        if (b2 == -1) return null;
        boolean masked = (b2 & 0x80) != 0; // client-to-server frames are always masked per spec
        long payloadLen = b2 & 0x7F;

        if (payloadLen == 126) {
            payloadLen = ((long) (in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (payloadLen == 127) {
            long len = 0;
            for (int i = 0; i < 8; i++) {
                len = (len << 8) | (in.read() & 0xFF);
            }
            payloadLen = len; // Redis commands are tiny; this branch just avoids mis-parsing the stream
        }

        byte[] maskKey = new byte[4];
        if (masked) {
            if (readFully(in, maskKey, 4) != 4) return null;
        }

        byte[] payload = new byte[(int) payloadLen];
        if (readFully(in, payload, payload.length) != payload.length) return null;

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private int readFully(InputStream in, byte[] buf, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int n = in.read(buf, total, length - total);
            if (n == -1) return total;
            total += n;
        }
        return total;
    }

    /** Writes one unmasked WebSocket text frame (server-to-client frames are never masked). */
    private void writeTextFrame(OutputStream out, String message) throws IOException {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN bit set + text opcode

        int len = payload.length;
        if (len <= 125) {
            frame.write(len);
        } else if (len <= 65535) {
            frame.write(126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((len >> (8 * i)) & 0xFF);
            }
        }
        frame.write(payload);
        out.write(frame.toByteArray());
        out.flush();
    }
}
