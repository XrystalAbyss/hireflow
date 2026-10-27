package com.hireflow.network.server;

import com.hireflow.context.ApplicationContext;
import com.hireflow.network.protocol.ProtocolConstants;
import com.hireflow.network.protocol.ResponseMessage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standard Java TCP server providing the HireFlow networking foundation.
 * <p>
 * Key design features:
 * - Strictly binds to localhost (127.0.0.1) by default to prevent exposure to the public internet.
 * - Uses ServerSocket and Socket standard Java APIs without external networking frameworks.
 * - Dispatches client sessions to a bounded ExecutorService thread pool.
 * - Configures explicit read timeouts on client sockets.
 * - Enforces connection limits to protect against resource starvation.
 * - Provides graceful shutdown, releasing worker threads, active client sockets, and server socket.
 * - Decoupled from Swing UI components; consumes ApplicationContext domain services directly.
 */
public class HireFlowTcpServer implements AutoCloseable {

    private final String bindHost;
    private final int configuredPort;
    private final ApplicationContext context;
    private final int maxConcurrentClients;
    private final int socketTimeoutMs;

    private final Set<ClientSessionHandler> activeSessions = ConcurrentHashMap.newKeySet();
    private final NetworkEventBroadcaster eventBroadcaster;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final java.util.concurrent.atomic.AtomicLong totalConnections = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalDisconnections = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong totalRejectedClients = new java.util.concurrent.atomic.AtomicLong(0);
    private final ConcurrentLinkedDeque<NetworkActivity> recentActivities = new ConcurrentLinkedDeque<>();
    private static final int MAX_ACTIVITY_LOGS = 200;
    private final CopyOnWriteArrayList<java.util.function.Consumer<NetworkActivity>> activityListeners = new CopyOnWriteArrayList<>();

    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private Thread acceptThread;
    private int actualPort;
    private long startTimeMillis;

    public HireFlowTcpServer(ApplicationContext context) {
        this(context, ProtocolConstants.DEFAULT_PORT);
    }

    public HireFlowTcpServer(ApplicationContext context, int port) {
        this(context, ProtocolConstants.DEFAULT_HOST, port,
                ProtocolConstants.MAX_CONCURRENT_CLIENTS, ProtocolConstants.DEFAULT_SO_TIMEOUT_MS);
    }

    public HireFlowTcpServer(ApplicationContext context, String bindHost, int port,
                             int maxConcurrentClients, int socketTimeoutMs) {
        this.context = Objects.requireNonNull(context, "ApplicationContext cannot be null");
        this.eventBroadcaster = new NetworkEventBroadcaster(this.context, this);
        this.bindHost = (bindHost != null && !bindHost.trim().isEmpty()) ? bindHost.trim() : ProtocolConstants.DEFAULT_HOST;
        this.configuredPort = port >= 0 ? port : ProtocolConstants.DEFAULT_PORT;
        this.maxConcurrentClients = Math.max(1, maxConcurrentClients);
        this.socketTimeoutMs = Math.max(1000, socketTimeoutMs);
    }

    /**
     * Starts the TCP server listening on the configured host and port.
     *
     * @throws IOException if server socket initialization or binding fails
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        InetAddress bindAddress = InetAddress.getByName(bindHost);
        this.serverSocket = new ServerSocket(configuredPort, 50, bindAddress);
        this.actualPort = serverSocket.getLocalPort();
        this.startTimeMillis = System.currentTimeMillis();

        // Initialize bounded thread pool with custom named daemon threads
        AtomicInteger threadCount = new AtomicInteger(1);
        this.clientExecutor = new ThreadPoolExecutor(
                maxConcurrentClients,
                maxConcurrentClients,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "hireflow-tcp-worker-" + threadCount.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        // Attach event broadcaster to domain services if available
        if (context.getRecruitmentDriveService() != null) {
            context.getRecruitmentDriveService().addEventListener(eventBroadcaster);
        }

        running.set(true);
        logActivity("SERVER_STARTED", "Bound on " + bindHost + ":" + actualPort + " (Localhost)", "ONLINE");

        this.acceptThread = new Thread(this::runAcceptLoop, "hireflow-tcp-acceptor");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    private void runAcceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(socketTimeoutMs);
                totalConnections.incrementAndGet();

                // Enforce concurrent client limit
                if (activeSessions.size() >= maxConcurrentClients) {
                    rejectOverCapacityClient(clientSocket);
                    continue;
                }

                ClientSessionHandler session = new ClientSessionHandler(this, clientSocket, context, eventBroadcaster);
                activeSessions.add(session);
                logActivity("CLIENT_CONNECTED", "Connected: " + clientSocket.getRemoteSocketAddress(), "CONNECTED");

                try {
                    clientExecutor.submit(session);
                } catch (RejectedExecutionException ree) {
                    activeSessions.remove(session);
                    rejectOverCapacityClient(clientSocket);
                }
            } catch (IOException ex) {
                if (!running.get() || serverSocket.isClosed()) {
                    // Normal server shutdown
                    break;
                }
            }
        }
    }

    private void rejectOverCapacityClient(Socket socket) {
        totalRejectedClients.incrementAndGet();
        logActivity("CLIENT_REJECTED", "Rejected: max limit (" + maxConcurrentClients + ") reached", "REJECTED");
        try (socket) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            ResponseMessage busy = ResponseMessage.error("none", 503, "Server busy: maximum concurrent client connections reached");
            writer.write(busy.toJson());
            writer.write("\n");
            writer.flush();
        } catch (IOException ignored) {
        }
    }

    public void removeSession(ClientSessionHandler session) {
        if (activeSessions.remove(session)) {
            totalDisconnections.incrementAndGet();
            String addr = (session != null) ? session.getRemoteAddress() : "unknown";
            logActivity("CLIENT_DISCONNECTED", "Client session closed: " + addr, "DISCONNECTED");
        }
    }

    /**
     * Gracefully stops the TCP server, disconnecting all active clients and stopping workers.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        // 1. Detach domain event broadcaster
        if (context.getRecruitmentDriveService() != null) {
            context.getRecruitmentDriveService().removeEventListener(eventBroadcaster);
        }
        eventBroadcaster.clearSubscribers();

        // 2. Close ServerSocket to stop accepting connections
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        // 3. Close all active client sessions cleanly
        for (ClientSessionHandler session : activeSessions) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        activeSessions.clear();

        // 4. Shut down executor service
        if (clientExecutor != null && !clientExecutor.isShutdown()) {
            clientExecutor.shutdown();
            try {
                if (!clientExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    clientExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 5. Join acceptor thread
        if (acceptThread != null && acceptThread.isAlive()) {
            acceptThread.interrupt();
        }

        logActivity("SERVER_STOPPED", "TCP server stopped cleanly", "OFFLINE");
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return actualPort;
    }

    public String getBindHost() {
        return bindHost;
    }

    public int getActiveClientCount() {
        return activeSessions.size();
    }

    public long getUptimeSeconds() {
        if (!running.get() || startTimeMillis == 0) {
            return 0L;
        }
        return (System.currentTimeMillis() - startTimeMillis) / 1000L;
    }

    public ApplicationContext getContext() {
        return context;
    }

    public NetworkEventBroadcaster getEventBroadcaster() {
        return eventBroadcaster;
    }

    public long getTotalConnections() {
        return totalConnections.get();
    }

    public long getTotalDisconnections() {
        return totalDisconnections.get();
    }

    public long getTotalRejectedClients() {
        return totalRejectedClients.get();
    }

    public long getTotalEventsDispatched() {
        return eventBroadcaster.getDispatchedEventCount();
    }

    public void logActivity(String action, String details, String status) {
        NetworkActivity act = new NetworkActivity(java.time.LocalDateTime.now(), action, details, status);
        recentActivities.addFirst(act);
        while (recentActivities.size() > MAX_ACTIVITY_LOGS) {
            recentActivities.removeLast();
        }
        for (java.util.function.Consumer<NetworkActivity> listener : activityListeners) {
            try {
                listener.accept(act);
            } catch (Throwable ignored) {
            }
        }
    }

    public java.util.List<NetworkActivity> getRecentActivities() {
        return new java.util.ArrayList<>(recentActivities);
    }

    public void addActivityListener(java.util.function.Consumer<NetworkActivity> listener) {
        if (listener != null) {
            activityListeners.add(listener);
        }
    }

    public void removeActivityListener(java.util.function.Consumer<NetworkActivity> listener) {
        activityListeners.remove(listener);
    }

    public void clearActivityHistory() {
        recentActivities.clear();
    }
}
