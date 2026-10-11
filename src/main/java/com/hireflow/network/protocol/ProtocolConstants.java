package com.hireflow.network.protocol;

/**
 * Constants governing the HireFlow TCP networking protocol and connection limits.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
    }

    public static final String PROTOCOL_VERSION = "1.0";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8085;

    /**
     * Maximum allowed size for a single incoming protocol frame (64 KB).
     * Requests exceeding this limit are rejected to prevent buffer bloat and memory exhaustion.
     */
    public static final int MAX_FRAME_LENGTH = 65536;

    /**
     * Default socket read timeout (10 seconds) for idle client socket read operations.
     */
    public static final int DEFAULT_SO_TIMEOUT_MS = 10000;

    /**
     * Default socket connection timeout (5 seconds).
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    /**
     * Maximum concurrent client connection worker threads.
     */
    public static final int MAX_CONCURRENT_CLIENTS = 25;

    /**
     * Bounded event queue capacity per connected subscriber (50 messages).
     * Protects server memory against slow or unresponsive subscribers.
     */
    public static final int DEFAULT_SUBSCRIBER_QUEUE_CAPACITY = 50;

    /**
     * Number of idle socket timeout cycles permitted for subscribers before issuing a connection health probe.
     */
    public static final int MAX_IDLE_CYCLES_BEFORE_PROBE = 3;
}
