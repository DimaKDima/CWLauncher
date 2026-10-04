package ru.cw.launcher.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный JSON кодек (без внешних зависимостей).
 * parse -> Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Double / Boolean / null
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object o = parse(text);
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    // ------------------------------------------------------------------ write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0, true);
        return sb.toString();
    }

    public static String writeCompact(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0, false);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object v, int indent, boolean pretty) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            escape(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(v.toString());
            }
        } else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (e.getValue() == null) continue;
                if (!first) sb.append(',');
                first = false;
                if (pretty) {
                    sb.append('\n');
                    pad(sb, indent + 1);
                }
                escape(sb, e.getKey());
                sb.append(pretty ? ": " : ":");
                writeValue(sb, e.getValue(), indent + 1, pretty);
            }
            if (pretty) {
                sb.append('\n');
                pad(sb, indent);
            }
            sb.append('}');
        } else if (v instanceof List) {
            List<Object> l = (List<Object>) v;
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) sb.append(',');
                first = false;
                if (pretty) {
                    sb.append('\n');
                    pad(sb, indent + 1);
                }
                writeValue(sb, o, indent + 1, pretty);
            }
            if (pretty) {
                sb.append('\n');
                pad(sb, indent);
            }
            sb.append(']');
        } else if (v instanceof Object[]) {
            writeValue(sb, List.of((Object[]) v), indent, pretty);
        } else {
            escape(sb, String.valueOf(v));
        }
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append("  ");
    }

    private static void escape(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> src, String key) {
        Object o = src.get(key);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> src, String key) {
        Object o = src.get(key);
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String str(Map<String, Object> src, String key, String def) {
        Object o = src.get(key);
        return o instanceof String s ? s : def;
    }

    public static int integer(Map<String, Object> src, String key, int def) {
        Object o = src.get(key);
        return o instanceof Number n ? n.intValue() : def;
    }

    public static double num(Map<String, Object> src, String key, double def) {
        Object o = src.get(key);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public static long lng(Map<String, Object> src, String key, long def) {
        Object o = src.get(key);
        return o instanceof Number n ? n.longValue() : def;
    }

    public static boolean bool(Map<String, Object> src, String key, boolean def) {
        Object o = src.get(key);
        return o instanceof Boolean b ? b : def;
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\uFEFF') i++;
                else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    while (i < s.length() && s.charAt(i) != '\n') i++;
                } else break;
            }
        }

        Object value() {
            skipWs();
            if (i >= s.length()) throw err("unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't', 'f' -> boolLit();
                case 'n' -> nullLit();
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                String k = string();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ':') throw err("expected ':'");
                i++;
                Object v = value();
                m.put(k, v);
                skipWs();
                if (i >= s.length()) throw err("unterminated object");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw err("expected ',' or '}'");
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            skipWs();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                skipWs();
                if (i >= s.length()) throw err("unterminated array");
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw err("expected ',' or ']'");
            }
        }

        String string() {
            if (s.charAt(i) != '"') throw err("expected string");
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case '/' -> sb.append('/');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw err("bad unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
            throw err("unterminated string");
        }

        Object boolLit() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw err("bad literal");
        }

        Object nullLit() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw err("bad literal");
        }

        Object number() {
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i).replace("+", "");
            if (t.isEmpty() || t.equals("-")) throw err("bad number");
            try {
                return Double.valueOf(t);
            } catch (NumberFormatException ex) {
                throw err("bad number '" + t + "'");
            }
        }

        RuntimeException err(String msg) {
            int line = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
            return new IllegalArgumentException("JSON: " + msg + " (offset " + i + ", line " + line + ")");
        }
    }
}
