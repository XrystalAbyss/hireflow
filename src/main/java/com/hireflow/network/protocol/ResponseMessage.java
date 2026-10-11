package com.hireflow.network.protocol;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured server response message returned to connected clients.
 * Supports synchronous request replies and stable asynchronous event envelopes.
 */
public record ResponseMessage(
        String version,
        String requestId,
        String status,
        int code,
        String message,
        Object data
) {
    public ResponseMessage {
        version = version != null ? version : ProtocolConstants.PROTOCOL_VERSION;
        requestId = requestId != null ? requestId : "none";
        status = status != null ? status : "OK";
        message = message != null ? message : "";
    }

    public static ResponseMessage ok(String requestId, String message, Object data) {
        return new ResponseMessage(ProtocolConstants.PROTOCOL_VERSION, requestId, "OK", 200, message, data);
    }

    public static ResponseMessage error(String requestId, int code, String message) {
        return new ResponseMessage(ProtocolConstants.PROTOCOL_VERSION, requestId, "ERROR", code, message, null);
    }

    /**
     * Constructs a stable event envelope containing protocol version, event type,
     * event identifier, timestamp, and approved public payload.
     */
    public static ResponseMessage event(String eventType, String eventId, String timestamp, String message, Object payload) {
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("eventType", eventType);
        eventData.put("eventId", eventId != null ? eventId : "EVT-" + System.currentTimeMillis());
        eventData.put("timestamp", timestamp != null ? timestamp : LocalDateTime.now().toString());
        eventData.put("payload", payload != null ? payload : Map.of());
        return new ResponseMessage(ProtocolConstants.PROTOCOL_VERSION, "event-" + System.currentTimeMillis(), "EVENT", 200, message, eventData);
    }

    public static ResponseMessage event(String eventType, String message, Object payload) {
        return event(eventType, "EVT-" + System.currentTimeMillis(), LocalDateTime.now().toString(), message, payload);
    }

    public static ResponseMessage heartbeat(String requestId) {
        Map<String, Object> hbData = new LinkedHashMap<>();
        hbData.put("timestamp", LocalDateTime.now().toString());
        return new ResponseMessage(ProtocolConstants.PROTOCOL_VERSION, requestId != null ? requestId : "hb-" + System.currentTimeMillis(),
                "OK", 200, "HEARTBEAT_ACK", hbData);
    }

    public static ResponseMessage heartbeatProbe() {
        return new ResponseMessage(ProtocolConstants.PROTOCOL_VERSION, "probe-" + System.currentTimeMillis(),
                "HEARTBEAT", 200, "HEARTBEAT_PROBE", Map.of("timestamp", LocalDateTime.now().toString()));
    }

    public boolean isOk() {
        return "OK".equalsIgnoreCase(status) && code >= 200 && code < 300;
    }

    public boolean isEvent() {
        return "EVENT".equalsIgnoreCase(status);
    }

    public boolean isHeartbeat() {
        return "HEARTBEAT".equalsIgnoreCase(status)
                || "HEARTBEAT_ACK".equalsIgnoreCase(message)
                || "HEARTBEAT_PROBE".equalsIgnoreCase(message);
    }

    public String getEventType() {
        if (data instanceof Map<?, ?> m) {
            Object val = m.get("eventType");
            return val != null ? val.toString() : null;
        }
        return null;
    }

    public String getEventId() {
        if (data instanceof Map<?, ?> m) {
            Object val = m.get("eventId");
            return val != null ? val.toString() : null;
        }
        return null;
    }

    public String getEventTimestamp() {
        if (data instanceof Map<?, ?> m) {
            Object val = m.get("timestamp");
            return val != null ? val.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getEventPayload() {
        if (data instanceof Map<?, ?> m && m.get("payload") instanceof Map<?, ?> p) {
            return (Map<String, Object>) p;
        }
        return Map.of();
    }

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", version);
        map.put("requestId", requestId);
        map.put("status", status);
        map.put("code", code);
        map.put("message", message);
        map.put("data", data);
        return SimpleJson.toJson(map);
    }

    public static ResponseMessage fromJson(String json) throws ProtocolException {
        Map<String, Object> map = SimpleJson.parseObject(json);
        String version = Objects.toString(map.get("version"), ProtocolConstants.PROTOCOL_VERSION);
        String requestId = Objects.toString(map.get("requestId"), "none");
        String status = Objects.toString(map.get("status"), "OK");
        int code = 200;
        if (map.get("code") instanceof Number num) {
            code = num.intValue();
        }
        String message = Objects.toString(map.get("message"), "");
        Object data = map.get("data");
        return new ResponseMessage(version, requestId, status, code, message, data);
    }
}
