package com.hireflow.network;

import com.hireflow.network.protocol.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HireFlow TCP Protocol & Framing Tests")
public class HireFlowProtocolTest {

    @Test
    @DisplayName("1. NetworkCommand parsing handles valid and unknown commands")
    void testNetworkCommandParsing() {
        assertEquals(NetworkCommand.PING, NetworkCommand.fromString("PING"));
        assertEquals(NetworkCommand.PING, NetworkCommand.fromString("ping"));
        assertEquals(NetworkCommand.SERVER_INFO, NetworkCommand.fromString("SERVER_INFO"));
        assertEquals(NetworkCommand.GET_DRIVES, NetworkCommand.fromString("get_drives"));
        assertEquals(NetworkCommand.SUBSCRIBE_EVENTS, NetworkCommand.fromString("SUBSCRIBE_EVENTS"));
        assertEquals(NetworkCommand.UNKNOWN, NetworkCommand.fromString("FOO_BAR"));
        assertEquals(NetworkCommand.UNKNOWN, NetworkCommand.fromString(null));
        assertEquals(NetworkCommand.UNKNOWN, NetworkCommand.fromString("   "));
    }

    @Test
    @DisplayName("2. SimpleJson serialization and deserialization of complex structures")
    void testSimpleJsonRoundTrip() throws Exception {
        Map<String, Object> original = Map.of(
                "str", "Hello \"World\"\nTest",
                "num", 12345L,
                "float", 9.75,
                "bool", true,
                "list", List.of("A", "B", "C")
        );

        String json = SimpleJson.toJson(original);
        assertNotNull(json);

        Map<String, Object> parsed = SimpleJson.parseObject(json);
        assertEquals("Hello \"World\"\nTest", parsed.get("str"));
        assertEquals(12345L, ((Number) parsed.get("num")).longValue());
        assertEquals(9.75, ((Number) parsed.get("float")).doubleValue());
        assertEquals(true, parsed.get("bool"));
        assertTrue(parsed.get("list") instanceof List<?>);
    }

    @Test
    @DisplayName("3. SimpleJson parser rejects malformed JSON with ProtocolException")
    void testSimpleJsonRejectsMalformed() {
        assertThrows(ProtocolException.class, () -> SimpleJson.parse("not json"));
        assertThrows(ProtocolException.class, () -> SimpleJson.parse("{ unquoted: 1 }"));
        assertThrows(ProtocolException.class, () -> SimpleJson.parse("{\"unclosed\": 1"));
        assertThrows(ProtocolException.class, () -> SimpleJson.parse("{\"key\": }"));
        assertThrows(ProtocolException.class, () -> SimpleJson.parse(""));
        assertThrows(ProtocolException.class, () -> SimpleJson.parse(null));
    }

    @Test
    @DisplayName("4. RequestMessage and ResponseMessage serialization and deserialization")
    void testRequestResponseRoundTrip() throws Exception {
        RequestMessage req = RequestMessage.of("PING", "req-101", Map.of("key", "val"));
        String reqJson = req.toJson();
        RequestMessage reqParsed = RequestMessage.fromJson(reqJson);
        assertEquals("PING", reqParsed.command());
        assertEquals("req-101", reqParsed.requestId());
        assertEquals("1.0", reqParsed.version());
        assertEquals("val", reqParsed.payload().get("key"));

        ResponseMessage resp = ResponseMessage.ok("req-101", "PONG", Map.of("status", "UP"));
        String respJson = resp.toJson();
        ResponseMessage respParsed = ResponseMessage.fromJson(respJson);
        assertTrue(respParsed.isOk());
        assertEquals(200, respParsed.code());
        assertEquals("PONG", respParsed.message());
        assertEquals("req-101", respParsed.requestId());
    }

    @Test
    @DisplayName("5. ProtocolFrameReader enforces bounded frame length and rejects oversized frames")
    void testBoundedFrameReaderEnforcesMax() throws Exception {
        // Line within bounds
        String normal = "Hello HireFlow TCP\n";
        ByteArrayInputStream in = new ByteArrayInputStream(normal.getBytes(StandardCharsets.UTF_8));
        ProtocolFrameReader reader = new ProtocolFrameReader(in, 100);
        assertEquals("Hello HireFlow TCP", reader.readFrame());
        assertNull(reader.readFrame()); // Clean EOF

        // Line exceeding bounds
        String oversized = "A".repeat(150) + "\n";
        ByteArrayInputStream inOver = new ByteArrayInputStream(oversized.getBytes(StandardCharsets.UTF_8));
        ProtocolFrameReader readerOver = new ProtocolFrameReader(inOver, 50);

        ProtocolException ex = assertThrows(ProtocolException.class, readerOver::readFrame);
        assertEquals(413, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("exceeds maximum allowed length"));
    }
}
