package com.hireflow.network.client;

import com.hireflow.network.protocol.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Standard Java TCP Client for HireFlow.
 * Provides structured request-response communication and asynchronous event listening
 * without external networking dependencies.
 * <p>
 * Supports multiplexing: when subscribed to asynchronous push events, synchronous commands
 * (such as PING, GET_DRIVES, and HEARTBEAT) can still be safely executed over the same socket
 * without race conditions or frame corruption.
 */
public class HireFlowTcpClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private Socket socket;
    private BufferedWriter writer;
    private ProtocolFrameReader frameReader;
    private Thread eventListenerThread;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Object writeLock = new Object();
    private final Map<String, CompletableFuture<ResponseMessage>> pendingRequests = new ConcurrentHashMap<>();
    private volatile Consumer<ResponseMessage> currentEventConsumer;

    public HireFlowTcpClient() {
        this(ProtocolConstants.DEFAULT_HOST, ProtocolConstants.DEFAULT_PORT);
    }

    public HireFlowTcpClient(String host, int port) {
        this(host, port, ProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS, ProtocolConstants.DEFAULT_SO_TIMEOUT_MS);
    }

    public HireFlowTcpClient(String host, int port, int connectTimeoutMs, int readTimeoutMs) {
        this.host = (host != null && !host.trim().isEmpty()) ? host.trim() : ProtocolConstants.DEFAULT_HOST;
        this.port = port;
        this.connectTimeoutMs = Math.max(1000, connectTimeoutMs);
        this.readTimeoutMs = Math.max(1000, readTimeoutMs);
    }

    /**
     * Connects to the HireFlow TCP server with the configured connection timeout.
     *
     * @throws IOException on connection failure
     */
    public synchronized void connect() throws IOException {
        if (connected.get()) {
            return;
        }

        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        this.socket.setSoTimeout(readTimeoutMs);

        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.frameReader = new ProtocolFrameReader(socket.getInputStream(), ProtocolConstants.MAX_FRAME_LENGTH);

        this.connected.set(true);
    }

    /**
     * Sends a structured RequestMessage and waits for the corresponding ResponseMessage.
     * Safely handles both direct synchronous reads and multiplexed reading when a background
     * push listener thread is active.
     */
    public ResponseMessage sendRequest(RequestMessage request) throws IOException, ProtocolException {
        ensureConnected();
        String json = request.toJson();

        if (eventListenerThread != null && eventListenerThread.isAlive()) {
            // Multiplexed mode: register pending future correlated by requestId
            CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
            pendingRequests.put(request.requestId(), future);

            synchronized (writeLock) {
                writer.write(json);
                writer.write("\n");
                writer.flush();
            }

            try {
                return future.get(readTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                pendingRequests.remove(request.requestId());
                throw new SocketTimeoutException("Read timeout waiting for response to " + request.requestId());
            } catch (ExecutionException ee) {
                pendingRequests.remove(request.requestId());
                if (ee.getCause() instanceof ProtocolException pe) {
                    throw pe;
                }
                throw new IOException("Request execution failed: " + ee.getMessage(), ee.getCause());
            } catch (InterruptedException ie) {
                pendingRequests.remove(request.requestId());
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", ie);
            }
        } else {
            // Direct synchronous mode
            synchronized (writeLock) {
                writer.write(json);
                writer.write("\n");
                writer.flush();
            }

            String responseFrame = frameReader.readFrame();
            if (responseFrame == null) {
                throw new IOException("Server closed connection unexpectedly (EOF)");
            }
            return ResponseMessage.fromJson(responseFrame);
        }
    }

    /**
     * Sends a raw string frame and returns the raw string response frame.
     * Useful for protocol conformance testing (e.g. malformed or oversized payloads).
     */
    public String sendRaw(String rawLine) throws IOException, ProtocolException {
        ensureConnected();
        synchronized (writeLock) {
            writer.write(rawLine);
            if (!rawLine.endsWith("\n")) {
                writer.write("\n");
            }
            writer.flush();
        }
        return frameReader.readFrame();
    }

    /**
     * Checks connectivity via PING command.
     */
    public ResponseMessage ping() throws IOException, ProtocolException {
        return sendRequest(RequestMessage.of(NetworkCommand.PING.getCommandName()));
    }

    /**
     * Issues a HEARTBEAT ping to verify connection health.
     */
    public ResponseMessage heartbeat() throws IOException, ProtocolException {
        return sendRequest(RequestMessage.of(NetworkCommand.HEARTBEAT.getCommandName()));
    }

    /**
     * Retrieves server version, capabilities, and mode info.
     */
    public ResponseMessage getServerInfo() throws IOException, ProtocolException {
        return sendRequest(RequestMessage.of(NetworkCommand.SERVER_INFO.getCommandName()));
    }

    /**
     * Retrieves public recruitment drive catalog.
     */
    public ResponseMessage getDrives() throws IOException, ProtocolException {
        return sendRequest(RequestMessage.of(NetworkCommand.GET_DRIVES.getCommandName()));
    }

    /**
     * Subscribes to permitted recruitment announcements.
     * Spawns a multiplexed background listener thread to dispatch incoming event frames to the provided consumer.
     */
    public ResponseMessage subscribeEvents(Consumer<ResponseMessage> eventConsumer) throws IOException, ProtocolException {
        if (eventConsumer != null) {
            this.currentEventConsumer = eventConsumer;
        }
        ResponseMessage subResponse = sendRequest(RequestMessage.of(NetworkCommand.SUBSCRIBE_EVENTS.getCommandName()));

        if (subResponse.isOk() && (eventListenerThread == null || !eventListenerThread.isAlive())) {
            this.eventListenerThread = new Thread(this::runEventListener, "hireflow-tcp-subscriber");
            this.eventListenerThread.setDaemon(true);
            this.eventListenerThread.start();
        }

        return subResponse;
    }

    /**
     * Unsubscribes from public recruitment announcements.
     */
    public ResponseMessage unsubscribeEvents() throws IOException, ProtocolException {
        ResponseMessage response = sendRequest(RequestMessage.of(NetworkCommand.UNSUBSCRIBE_EVENTS.getCommandName()));
        this.currentEventConsumer = null;
        return response;
    }

    private void runEventListener() {
        while (connected.get() && socket != null && !socket.isClosed()) {
            try {
                String frame = frameReader.readFrame();
                if (frame == null) {
                    break;
                }
                ResponseMessage msg = ResponseMessage.fromJson(frame);

                if (msg.isEvent()) {
                    Consumer<ResponseMessage> consumer = currentEventConsumer;
                    if (consumer != null) {
                        consumer.accept(msg);
                    }
                } else if (msg.isHeartbeat()) {
                    // Check if this was a reply to a heartbeat request from client
                    CompletableFuture<ResponseMessage> future = pendingRequests.remove(msg.requestId());
                    if (future != null) {
                        future.complete(msg);
                    } else {
                        // Server-initiated HEARTBEAT_PROBE: send an immediate HEARTBEAT acknowledgement
                        // to actively prove client liveness to the server
                        sendHeartbeatAck();
                    }
                } else {
                    // Synchronous reply to a pending request
                    CompletableFuture<ResponseMessage> future = pendingRequests.remove(msg.requestId());
                    if (future != null) {
                        future.complete(msg);
                    }
                }
            } catch (SocketTimeoutException ste) {
                // Read timeout during idle listening is normal; loop and continue
            } catch (Exception ex) {
                if (!connected.get() || socket.isClosed()) {
                    break;
                }
            }
        }
    }

    private void sendHeartbeatAck() {
        if (!connected.get() || socket == null || socket.isClosed()) {
            return;
        }
        synchronized (writeLock) {
            try {
                if (writer != null) {
                    RequestMessage hb = RequestMessage.of(NetworkCommand.HEARTBEAT.getCommandName());
                    writer.write(hb.toJson());
                    writer.write("\n");
                    writer.flush();
                }
            } catch (IOException ignored) {
            }
        }
    }

    public ResponseMessage readNextResponse() throws IOException, ProtocolException {
        ensureConnected();
        String frame = frameReader.readFrame();
        if (frame == null) {
            return null;
        }
        return ResponseMessage.fromJson(frame);
    }

    private void ensureConnected() throws IOException {
        if (!connected.get() || socket == null || socket.isClosed()) {
            connect();
        }
    }

    public boolean isConnected() {
        return connected.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public synchronized void close() {
        if (connected.compareAndSet(true, false)) {
            // Cancel any pending futures
            for (CompletableFuture<ResponseMessage> future : pendingRequests.values()) {
                future.completeExceptionally(new IOException("Client connection closed"));
            }
            pendingRequests.clear();

            if (eventListenerThread != null && eventListenerThread.isAlive()) {
                eventListenerThread.interrupt();
            }
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }
}
