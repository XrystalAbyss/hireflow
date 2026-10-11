package com.hireflow.network.protocol;

/**
 * Enumeration of supported client-to-server commands in the HireFlow TCP protocol.
 */
public enum NetworkCommand {
    PING("PING"),
    SERVER_INFO("SERVER_INFO"),
    GET_DRIVES("GET_DRIVES"),
    SUBSCRIBE_EVENTS("SUBSCRIBE_EVENTS"),
    UNSUBSCRIBE_EVENTS("UNSUBSCRIBE_EVENTS"),
    HEARTBEAT("HEARTBEAT"),
    UNKNOWN("UNKNOWN");

    private final String commandName;

    NetworkCommand(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }

    public static NetworkCommand fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return UNKNOWN;
        }
        for (NetworkCommand cmd : values()) {
            if (cmd != UNKNOWN && cmd.commandName.equalsIgnoreCase(raw.trim())) {
                return cmd;
            }
        }
        return UNKNOWN;
    }
}
