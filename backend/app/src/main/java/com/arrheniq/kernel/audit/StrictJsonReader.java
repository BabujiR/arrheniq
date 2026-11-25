package com.arrheniq.kernel.audit;

import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, strict JSON reader used to read stored audit {@code changes} back for chain
 * verification. Numbers become {@link BigDecimal} (exact), objects {@link LinkedHashMap},
 * arrays {@link ArrayList}. Kept dependency-free so the verifier behaves identically in the app,
 * in the CLI and in an inspector's offline check.
 */
public final class StrictJsonReader {

    private final String s;
    private int pos;

    private StrictJsonReader(String s) {
        this.s = s;
    }

    public static @Nullable Object read(String json) {
        StrictJsonReader r = new StrictJsonReader(json);
        r.skipWs();
        @Nullable Object v = r.value();
        r.skipWs();
        if (r.pos != r.s.length()) {
            throw r.error("trailing content");
        }
        return v;
    }

    private @Nullable Object value() {
        if (pos >= s.length()) {
            throw error("unexpected end");
        }
        char c = s.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw error("unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, @Nullable Object> object() {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        pos++;
        skipWs();
        if (peek() == '}') {
            pos++;
            return m;
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                throw error("expected object key");
            }
            String key = string();
            skipWs();
            expect(':');
            skipWs();
            if (m.containsKey(key)) {
                throw error("duplicate key " + key);
            }
            m.put(key, value());
            skipWs();
            char c = next();
            if (c == '}') {
                return m;
            }
            if (c != ',') {
                throw error("expected , or }");
            }
        }
    }

    private List<@Nullable Object> array() {
        List<@Nullable Object> list = new ArrayList<>();
        pos++;
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(value());
            skipWs();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("expected , or ]");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return b.toString();
            }
            if (c < 0x20) {
                throw error("unescaped control character");
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case '"' -> b.append('"');
                case '\\' -> b.append('\\');
                case '/' -> b.append('/');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (pos + 4 > s.length()) {
                        throw error("bad unicode escape");
                    }
                    b.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("bad escape \\" + e);
            }
        }
    }

    private BigDecimal number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < s.length() && "0123456789.eE+-".indexOf(s.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return new BigDecimal(s.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private @Nullable Object literal(String word, @Nullable Object value) {
        if (!s.startsWith(word, pos)) {
            throw error("bad literal");
        }
        pos += word.length();
        return value;
    }

    private void skipWs() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                return;
            }
        }
    }

    private char peek() {
        if (pos >= s.length()) {
            throw error("unexpected end");
        }
        return s.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("invalid JSON at " + pos + ": " + msg);
    }
}
