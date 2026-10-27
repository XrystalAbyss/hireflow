package com.hireflow.network.protocol;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Lightweight, zero-dependency JSON parser and serializer designed for the HireFlow TCP protocol.
 * Guarantees safe deserialization by only returning standard Java primitive wrappers, Strings,
 * Lists, and Maps. Strictly avoids arbitrary class instantiation or Java serialization mechanisms.
 */
public final class SimpleJson {

    private static final int MAX_NESTING_DEPTH = 32;

    private SimpleJson() {
    }

    /**
     * Serializes any supported Java object (Map, List, Array, Primitive, String, null) into a JSON string.
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        serializeValue(value, sb, 0);
        return sb.toString();
    }

    private static void serializeValue(Object value, StringBuilder sb, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("Max nesting depth exceeded in JSON serialization");
        }
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            escapeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                escapeString(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                serializeValue(entry.getValue(), sb, depth + 1);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                serializeValue(item, sb, depth + 1);
            }
            sb.append(']');
        } else if (value.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                serializeValue(Array.get(value, i), sb, depth + 1);
            }
            sb.append(']');
        } else {
            // Fallback: serialize as string
            escapeString(value.toString(), sb);
        }
    }

    private static void escapeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /**
     * Parses a JSON string into standard Java data structures (Map, List, String, Number, Boolean, null).
     *
     * @param json JSON formatted text
     * @return deserialized object
     * @throws ProtocolException if JSON syntax is invalid
     */
    public static Object parse(String json) throws ProtocolException {
        if (json == null) {
            throw new ProtocolException(400, "JSON payload cannot be null");
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            throw new ProtocolException(400, "JSON payload cannot be empty");
        }
        Parser parser = new Parser(trimmed);
        Object result = parser.parseValue(0);
        parser.skipWhitespace();
        if (parser.hasMore()) {
            throw new ProtocolException(400, "Unexpected trailing characters after JSON root at index " + parser.pos);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) throws ProtocolException {
        Object parsed = parse(json);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ProtocolException(400, "Expected JSON object but received: " + (parsed != null ? parsed.getClass().getSimpleName() : "null"));
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        boolean hasMore() {
            return pos < src.length();
        }

        void skipWhitespace() {
            while (hasMore() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        char peek() throws ProtocolException {
            skipWhitespace();
            if (!hasMore()) {
                throw new ProtocolException(400, "Unexpected end of JSON input");
            }
            return src.charAt(pos);
        }

        char next() throws ProtocolException {
            char c = peek();
            pos++;
            return c;
        }

        Object parseValue(int depth) throws ProtocolException {
            if (depth > MAX_NESTING_DEPTH) {
                throw new ProtocolException(400, "Max JSON nesting depth exceeded");
            }
            skipWhitespace();
            if (!hasMore()) {
                throw new ProtocolException(400, "Unexpected end of input while parsing JSON value");
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObjectInternal(depth);
            } else if (c == '[') {
                return parseArrayInternal(depth);
            } else if (c == '"') {
                return parseStringInternal();
            } else if (c == 't' || c == 'f') {
                return parseBooleanInternal();
            } else if (c == 'n') {
                return parseNullInternal();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumberInternal();
            } else {
                throw new ProtocolException(400, "Unexpected character '" + c + "' at position " + pos);
            }
        }

        Map<String, Object> parseObjectInternal(int depth) throws ProtocolException {
            next(); // consume '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (hasMore() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }

            while (true) {
                skipWhitespace();
                if (!hasMore() || src.charAt(pos) != '"') {
                    throw new ProtocolException(400, "Expected string key in object at position " + pos);
                }
                String key = parseStringInternal();
                skipWhitespace();
                if (!hasMore() || next() != ':') {
                    throw new ProtocolException(400, "Expected ':' after key in object at position " + pos);
                }
                Object val = parseValue(depth + 1);
                map.put(key, val);

                skipWhitespace();
                if (!hasMore()) {
                    throw new ProtocolException(400, "Unclosed object at end of input");
                }
                char nextChar = src.charAt(pos);
                if (nextChar == '}') {
                    pos++;
                    return map;
                } else if (nextChar == ',') {
                    pos++;
                } else {
                    throw new ProtocolException(400, "Expected ',' or '}' in object at position " + pos);
                }
            }
        }

        List<Object> parseArrayInternal(int depth) throws ProtocolException {
            next(); // consume '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (hasMore() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }

            while (true) {
                Object val = parseValue(depth + 1);
                list.add(val);
                skipWhitespace();
                if (!hasMore()) {
                    throw new ProtocolException(400, "Unclosed array at end of input");
                }
                char nextChar = src.charAt(pos);
                if (nextChar == ']') {
                    pos++;
                    return list;
                } else if (nextChar == ',') {
                    pos++;
                } else {
                    throw new ProtocolException(400, "Expected ',' or ']' in array at position " + pos);
                }
            }
        }

        String parseStringInternal() throws ProtocolException {
            next(); // consume initial '"'
            StringBuilder sb = new StringBuilder();
            while (hasMore()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (!hasMore()) {
                        throw new ProtocolException(400, "Unfinished escape sequence at end of input");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new ProtocolException(400, "Incomplete unicode escape at position " + pos);
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException nfe) {
                                throw new ProtocolException(400, "Invalid unicode hex digits: " + hex);
                            }
                        }
                        default -> throw new ProtocolException(400, "Invalid escape char '\\" + esc + "' at position " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new ProtocolException(400, "Unclosed string literal starting before position " + pos);
        }

        Boolean parseBooleanInternal() throws ProtocolException {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new ProtocolException(400, "Invalid boolean token at position " + pos);
        }

        Object parseNullInternal() throws ProtocolException {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new ProtocolException(400, "Invalid null token at position " + pos);
        }

        Number parseNumberInternal() throws ProtocolException {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            boolean isFloating = false;
            while (hasMore()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+') {
                    isFloating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, pos);
            try {
                if (isFloating) {
                    return Double.parseDouble(numStr);
                } else {
                    return Long.parseLong(numStr);
                }
            } catch (NumberFormatException nfe) {
                throw new ProtocolException(400, "Invalid number format '" + numStr + "' at position " + start);
            }
        }
    }
}
