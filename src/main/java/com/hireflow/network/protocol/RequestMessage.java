package com.hireflow.network.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured client request message.
 */
public record RequestMessage(
        String version,
        String command,
        String requestId,
        Map<String, Object> payload
) {
    public RequestMessage {
        version = version != null ? version : ProtocolConstants.PROTOCOL_VERSION;
        command = command != null ? command.trim().toUpperCase() : "UNKNOWN";
        requestId = requestId != null ? requestId : "req-" + System.nanoTime();
        payload = payload != null ? payload : Collections.emptyMap();
    }

    public static RequestMessage of(String command) {
        return new RequestMessage(ProtocolConstants.PROTOCOL_VERSION, command, "req-" + System.nanoTime(), Collections.emptyMap());
    }

    public static RequestMessage of(String command, String requestId) {
        return new RequestMessage(ProtocolConstants.PROTOCOL_VERSION, command, requestId, Collections.emptyMap());
    }

    public static RequestMessage of(String command, String requestId, Map<String, Object> payload) {
        return new RequestMessage(ProtocolConstants.PROTOCOL_VERSION, command, requestId, payload);
    }

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", version);
        map.put("command", command);
        map.put("requestId", requestId);
        map.put("payload", payload);
        return SimpleJson.toJson(map);
    }

    @SuppressWarnings("unchecked")
    public static RequestMessage fromJson(String json) throws ProtocolException {
        Map<String, Object> map = SimpleJson.parseObject(json);
        String version = Objects.toString(map.get("version"), null);
        String command = Objects.toString(map.get("command"), null);
        String requestId = Objects.toString(map.get("requestId"), null);
        Object rawPayload = map.get("payload");
        Map<String, Object> payload = (rawPayload instanceof Map<?, ?> m)
                ? (Map<String, Object>) m
                : Collections.emptyMap();

        if (command == null || command.trim().isEmpty()) {
            throw new ProtocolException(400, "Missing required 'command' field in request");
        }

        return new RequestMessage(version, command, requestId, payload);
    }
}
