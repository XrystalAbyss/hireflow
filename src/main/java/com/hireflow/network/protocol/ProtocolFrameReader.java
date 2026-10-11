package com.hireflow.network.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Bounded frame reader reading line-delimited protocol frames from an InputStream.
 * Strictly enforces maximum frame lengths to protect against buffer overflow and memory exhaustion.
 */
public final class ProtocolFrameReader {

    private final InputStream in;
    private final int maxFrameLength;

    public ProtocolFrameReader(InputStream in) {
        this(in, ProtocolConstants.MAX_FRAME_LENGTH);
    }

    public ProtocolFrameReader(InputStream in, int maxFrameLength) {
        this.in = in;
        this.maxFrameLength = maxFrameLength;
    }

    /**
     * Reads a single frame line terminated by '\n' (with optional '\r'), up to maxFrameLength bytes.
     *
     * @return String frame content, or null if clean EOF is reached before reading any byte
     * @throws IOException on I/O failure or timeout
     * @throws ProtocolException if the incoming frame exceeds the maximum allowed length before '\n'
     */
    public String readFrame() throws IOException, ProtocolException {
        byte[] buffer = new byte[1024];
        int count = 0;
        int b;

        while ((b = in.read()) != -1) {
            if (b == '\n') {
                if (count > 0 && buffer[count - 1] == '\r') {
                    count--;
                }
                return new String(buffer, 0, count, StandardCharsets.UTF_8);
            }

            if (count >= maxFrameLength) {
                // Drain the rest of the oversized line to keep stream in known state if possible
                drainUntilNewline();
                throw new ProtocolException(413, "Message exceeds maximum allowed length of " + maxFrameLength + " bytes");
            }

            if (count == buffer.length) {
                byte[] newBuf = new byte[Math.min(buffer.length * 2, maxFrameLength + 1)];
                System.arraycopy(buffer, 0, newBuf, 0, count);
                buffer = newBuf;
            }
            buffer[count++] = (byte) b;
        }

        if (count == 0) {
            return null; // Clean EOF
        }
        if (count > 0 && buffer[count - 1] == '\r') {
            count--;
        }
        return new String(buffer, 0, count, StandardCharsets.UTF_8);
    }

    private void drainUntilNewline() throws IOException {
        int b;
        int drained = 0;
        while (drained < 65536 && (b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            drained++;
        }
    }
}
