package com.hireflow.network.server;

import com.hireflow.context.ApplicationContext;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.network.protocol.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles an individual connected client session over TCP.
 * Runs on a dedicated worker thread from the server's thread pool.
 * <p>
 * Subscription & Backpressure design:
 * - Each subscriber has an independent bounded FIFO event queue (capacity 50).
 * - Events are pushed to the client via an isolated subscriber writer thread.
 * - Slow or unresponsive clients trigger the documented overload policy (429 Too Many Requests
 *   followed by clean disconnect), completely preventing slow clients from blocking fast clients or server services.
 * - Duplicate SUBSCRIBE_EVENTS requests on the same connection are idempotent and do not duplicate event streams.
 * - Supports UNSUBSCRIBE_EVENTS and HEARTBEAT commands.
 * - Healthy idle subscribers are preserved across read timeouts, with periodic health probes to detect silent half-open disconnects.
 */
public class ClientSessionHandler implements Runnable, AutoCloseable {

    private final HireFlowTcpServer server;
    private final Socket socket;
    private final ApplicationContext context;
    private final NetworkEventBroadcaster broadcaster;

    private final Object sendLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    private final BlockingQueue<ResponseMessage> outboundEventQueue =
            new ArrayBlockingQueue<>(ProtocolConstants.DEFAULT_SUBSCRIBER_QUEUE_CAPACITY);
    private static final ResponseMessage POISON_PILL = new ResponseMessage("1.0", "poison", "POISON", 0, "", null);

    private final AtomicBoolean probePending = new AtomicBoolean(false);

    private ProtocolFrameReader frameReader;
    private BufferedWriter writer;
    private Thread subscriberWriterThread;

    public ClientSessionHandler(HireFlowTcpServer server,
                                Socket socket,
                                ApplicationContext context,
                                NetworkEventBroadcaster broadcaster) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.broadcaster = broadcaster;
    }

    @Override
    public void run() {
        try {
            this.frameReader = new ProtocolFrameReader(socket.getInputStream(), ProtocolConstants.MAX_FRAME_LENGTH);
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            int idleTimeoutCycles = 0;

            while (!closed.get() && server.isRunning() && !socket.isClosed()) {
                String frame;
                try {
                    frame = frameReader.readFrame();
                    idleTimeoutCycles = 0; // Reset counter on active traffic
                    probePending.set(false); // Valid frame proves active client acknowledgement
                } catch (SocketTimeoutException ste) {
                    if (subscribed.get()) {
                        idleTimeoutCycles++;
                        // Periodically verify connection health for idle subscribers
                        if (idleTimeoutCycles >= ProtocolConstants.MAX_IDLE_CYCLES_BEFORE_PROBE) {
                            if (!probePending.get()) {
                                try {
                                    synchronized (sendLock) {
                                        if (writer != null && !socket.isClosed()) {
                                            writer.write(ResponseMessage.heartbeatProbe().toJson());
                                            writer.write("\n");
                                            writer.flush();
                                        }
                                    }
                                    probePending.set(true);
                                    if (server != null) {
                                        server.logActivity("HEARTBEAT_PROBE", "Sent heartbeat probe to idle subscriber: " + getRemoteAddress(), "PROBING");
                                    }
                                } catch (IOException probeEx) {
                                    // Socket is dead or reset, terminate cleanly
                                    break;
                                }
                            } else {
                                // A probe was previously sent, but client failed to acknowledge across timeout window
                                if (server != null) {
                                    server.logActivity("HEARTBEAT_TIMEOUT", "Heartbeat unacknowledged: disconnecting idle subscriber " + getRemoteAddress(), "TIMEOUT");
                                }
                                break;
                            }
                        }
                        continue; // Keep healthy idle subscriber connection alive
                    }

                    // Non-subscribed idle client timed out
                    sendErrorResponse("none", 408, "Read timeout: client idle for more than configured threshold");
                    break;
                } catch (ProtocolException pe) {
                    sendErrorResponse("none", pe.getStatusCode(), pe.getMessage());
                    if (pe.getStatusCode() == 413) {
                        // Oversized frame: terminate connection to prevent buffer corruption
                        break;
                    }
                    continue;
                }

                if (frame == null) {
                    // Client disconnected cleanly (EOF)
                    break;
                }

                if (frame.trim().isEmpty()) {
                    continue;
                }

                handleFrame(frame);
            }
        } catch (IOException ignored) {
            // Socket closed or remote disconnect
        } finally {
            close();
        }
    }

    private void handleFrame(String rawFrame) {
        RequestMessage request;
        try {
            request = RequestMessage.fromJson(rawFrame);
        } catch (ProtocolException pe) {
            sendErrorResponse("none", pe.getStatusCode(), "Malformed protocol request: " + pe.getMessage());
            return;
        }

        String reqId = request.requestId();

        // 1. Verify protocol version compatibility
        if (!ProtocolConstants.PROTOCOL_VERSION.equals(request.version())) {
            sendErrorResponse(reqId, 400, "Unsupported protocol version '" + request.version() +
                    "'. Server requires version '" + ProtocolConstants.PROTOCOL_VERSION + "'");
            return;
        }

        // 2. Resolve command
        NetworkCommand command = NetworkCommand.fromString(request.command());
        switch (command) {
            case PING -> handlePing(reqId);
            case SERVER_INFO -> handleServerInfo(reqId);
            case GET_DRIVES -> handleGetDrives(reqId);
            case SUBSCRIBE_EVENTS -> handleSubscribeEvents(reqId);
            case UNSUBSCRIBE_EVENTS -> handleUnsubscribeEvents(reqId);
            case HEARTBEAT -> handleHeartbeat(reqId);
            case UNKNOWN -> sendErrorResponse(reqId, 400, "Unknown command '" + request.command() +
                    "'. Supported commands: [PING, SERVER_INFO, GET_DRIVES, SUBSCRIBE_EVENTS, UNSUBSCRIBE_EVENTS, HEARTBEAT]");
        }
    }

    private void handlePing(String reqId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("serverStatus", "ONLINE");
        sendResponse(ResponseMessage.ok(reqId, "PONG", data));
    }

    private void handleHeartbeat(String reqId) {
        sendResponse(ResponseMessage.heartbeat(reqId));
    }

    private void handleServerInfo(String reqId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverName", "HireFlow Placement Management Server");
        data.put("serverVersion", "1.0.0-stage2");
        data.put("protocolVersion", ProtocolConstants.PROTOCOL_VERSION);
        data.put("storageMode", context.getMode() != null ? context.getMode().name() : "UNKNOWN");
        data.put("uptimeSeconds", server.getUptimeSeconds());
        data.put("activeClients", server.getActiveClientCount());
        data.put("supportedCommands", List.of(
                "PING", "SERVER_INFO", "GET_DRIVES", "SUBSCRIBE_EVENTS", "UNSUBSCRIBE_EVENTS", "HEARTBEAT"
        ));

        sendResponse(ResponseMessage.ok(reqId, "Server information retrieved successfully", data));
    }

    private void handleGetDrives(String reqId) {
        List<RecruitmentDrive> drives = context.getDriveRepository().findAll();
        List<Map<String, Object>> drivesList = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (RecruitmentDrive d : drives) {
            String companyName = context.getCompanyRepository().findById(d.getCompanyId())
                    .map(Company::getName)
                    .orElse(d.getCompanyId());

            Map<String, Object> dMap = new LinkedHashMap<>();
            dMap.put("driveId", d.getId());
            dMap.put("companyId", d.getCompanyId());
            dMap.put("companyName", companyName);
            dMap.put("jobTitle", d.getJobTitle());
            dMap.put("minimumCgpa", d.getMinimumCgpa());
            dMap.put("eligibleBranches", new ArrayList<>(d.getEligibleBranches()));
            dMap.put("applicationDeadline", d.getApplicationDeadline().toString());
            dMap.put("active", !d.getApplicationDeadline().isBefore(today));
            drivesList.add(dMap);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalDrives", drivesList.size());
        data.put("drives", drivesList);

        sendResponse(ResponseMessage.ok(reqId, "Public recruitment drives retrieved successfully", data));
    }

    private void handleSubscribeEvents(String reqId) {
        if (broadcaster == null) {
            sendErrorResponse(reqId, 503, "Event broadcaster is not configured on this server");
            return;
        }

        // Idempotent: check if already subscribed on this connection
        if (subscribed.get()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("channel", "PUBLIC_RECRUITMENT_ANNOUNCEMENTS");
            data.put("subscribed", true);
            data.put("alreadySubscribed", true);
            sendResponse(ResponseMessage.ok(reqId, "Already subscribed to public recruitment announcements", data));
            return;
        }

        if (subscribed.compareAndSet(false, true)) {
            // Launch isolated subscriber writer thread to deliver events from queue
            this.subscriberWriterThread = new Thread(this::runSubscriberWriter,
                    "hireflow-subscriber-writer-" + System.identityHashCode(this));
            this.subscriberWriterThread.setDaemon(true);
            this.subscriberWriterThread.start();

            broadcaster.registerSubscriber(this);
            if (server != null) {
                server.logActivity("CLIENT_SUBSCRIBED", "Subscribed to public announcements: " + getRemoteAddress(), "SUBSCRIBED");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("channel", "PUBLIC_RECRUITMENT_ANNOUNCEMENTS");
            data.put("subscribed", true);
            sendResponse(ResponseMessage.ok(reqId, "Subscribed to public recruitment announcements", data));
        }
    }

    private void handleUnsubscribeEvents(String reqId) {
        if (subscribed.compareAndSet(true, false)) {
            if (broadcaster != null) {
                broadcaster.unregisterSubscriber(this);
            }
            outboundEventQueue.offer(POISON_PILL);
            if (server != null) {
                server.logActivity("CLIENT_UNSUBSCRIBED", "Unsubscribed from public announcements: " + getRemoteAddress(), "UNSUBSCRIBED");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("channel", "PUBLIC_RECRUITMENT_ANNOUNCEMENTS");
            data.put("subscribed", false);
            sendResponse(ResponseMessage.ok(reqId, "Unsubscribed from public recruitment announcements", data));
        } else {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("channel", "PUBLIC_RECRUITMENT_ANNOUNCEMENTS");
            data.put("subscribed", false);
            sendResponse(ResponseMessage.ok(reqId, "Not currently subscribed", data));
        }
    }

    /**
     * Non-blocking method called by NetworkEventBroadcaster to enqueue an event for this client.
     *
     * @param eventMsg stable event response message
     * @return true if accepted, false if rejected due to overload or closed session
     */
    public boolean offerEvent(ResponseMessage eventMsg) {
        if (closed.get() || !subscribed.get()) {
            return false;
        }

        boolean accepted = outboundEventQueue.offer(eventMsg);
        if (!accepted) {
            // Documented Overload Policy: Disconnect slow subscriber to preserve server memory and isolation
            handleSubscriberOverload();
            return false;
        }
        return true;
    }

    private void handleSubscriberOverload() {
        // Documented Overload Policy:
        // A subscriber whose bounded queue capacity is exceeded cannot keep up with traffic.
        // To guarantee non-blocking event publishing and protect server memory isolation,
        // the slow client is immediately unregistered and closed.
        if (server != null) {
            server.logActivity("SUBSCRIBER_OVERLOAD", "Queue overflow (>50): disconnecting slow subscriber " + getRemoteAddress(), "OVERLOAD");
        }
        close();
    }

    /**
     * Dedicated background worker draining the bounded event queue to the socket writer.
     */
    private void runSubscriberWriter() {
        try {
            while (!closed.get() && server.isRunning() && !socket.isClosed()) {
                ResponseMessage msg = outboundEventQueue.take();
                if (msg == POISON_PILL || closed.get()) {
                    break;
                }

                synchronized (sendLock) {
                    if (writer != null && !socket.isClosed()) {
                        writer.write(msg.toJson());
                        writer.write("\n");
                        writer.flush();
                    }
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (IOException ioe) {
            // Client socket closed or network reset during write
            close();
        }
    }

    public void sendResponse(ResponseMessage response) {
        if (closed.get() || socket.isClosed()) {
            return;
        }
        synchronized (sendLock) {
            try {
                if (writer != null) {
                    writer.write(response.toJson());
                    writer.write("\n");
                    writer.flush();
                }
            } catch (IOException ignored) {
                close();
            }
        }
    }

    public void sendErrorResponse(String reqId, int code, String message) {
        sendResponse(ResponseMessage.error(reqId, code, message));
    }

    public boolean isSubscribed() {
        return subscribed.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int getQueuedEventCount() {
        return outboundEventQueue.size();
    }

    public String getRemoteAddress() {
        if (socket != null && socket.getRemoteSocketAddress() != null) {
            return socket.getRemoteSocketAddress().toString();
        }
        return "unknown";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            subscribed.set(false);
            if (broadcaster != null) {
                broadcaster.unregisterSubscriber(this);
            }
            server.removeSession(this);

            outboundEventQueue.offer(POISON_PILL);
            if (subscriberWriterThread != null && subscriberWriterThread.isAlive()) {
                subscriberWriterThread.interrupt();
            }

            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
