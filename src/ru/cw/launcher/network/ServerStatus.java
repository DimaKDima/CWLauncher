package ru.cw.launcher.network;

import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реальная проверка статуса Minecraft-сервера по протоколу Server List Ping
 * (VarInt, handshake → status → JSON). Никаких выдуманных данных: если ответ не
 * получен — состояние "недоступен".
 */
public final class ServerStatus {

    public record Info(String host, int port, boolean online, int players, int maxPlayers, long pingMs,
                       String version, String motd, long checkedAt, String error) {

        public boolean available() {
            return error == null && checkedAt > 0;
        }

        public String textPlayers() {
            if (!available() || !online) return "—";
            return players + " / " + maxPlayers;
        }

        public String textPing() {
            if (!available() || !online) return "—";
            return pingMs + " мс";
        }

        public String textStatus() {
            if (!available()) return "Статус сервера недоступен";
            if (online) return "Сервер доступен";
            return "Сервер не отвечает";
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("host", host);
            m.put("port", port);
            m.put("online", online);
            m.put("players", players);
            m.put("maxPlayers", maxPlayers);
            m.put("pingMs", pingMs);
            m.put("version", version);
            m.put("motd", motd);
            m.put("checkedAt", checkedAt);
            m.put("error", error);
            return m;
        }

        public static Info unavailable(String host, int port, String error) {
            return new Info(host, port, false, 0, 0, -1, null, null, System.currentTimeMillis(), error);
        }
    }

    /** Кэшированный последний результат — чтобы не пинговать сервер каждую секунду. */
    private static volatile Info cached = null;

    public static Info cached() {
        return cached;
    }

    public static Info check(String address, int timeoutMs) {
        String host = address == null ? "" : address.trim();
        int port = 25965;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && colon < host.length() - 1) {
            String tail = host.substring(colon + 1).trim();
            if (tail.chars().allMatch(Character::isDigit)) {
                host = host.substring(0, colon);
                port = (int) Utils.port(tail, 25965);
            }
        }
        if (port <= 0 || port > 65535) port = 25965;
        Info info = ping(host, port, timeoutMs);
        cached = info;
        return info;
    }

    public static Info ping(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.max(1500, timeoutMs));
            socket.setSoTimeout(Math.max(1500, timeoutMs));
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            writeVarInt(payload, 0);                                  // id
            writeVarInt(payload, 763);                                // protocol version
            writeVarIntStr(payload, host);
            payload.write((port >> 8) & 0xFF);
            payload.write(port & 0xFF);
            writeVarInt(payload, 1);                                  // Next State: status

            writeVarInt(out, payload.size());
            out.write(payload.toByteArray());
            // Запрос статуса: длина пакета 1 и id 0. Без длины сервер пакет не читает.
            writeVarInt(out, 1);
            writeVarInt(out, 0);
            out.flush();
            Log.info("Запрос статуса " + host + ":" + port);

            int length = readVarInt(in);
            byte[] body = in.readNBytes(length);
            long ping = System.currentTimeMillis() - start;
            int idx = 0;
            idx = skipVarInt(body, idx);                              // packet id
            int jsonLen = varIntOf(body, idx);
            idx = skipVarInt(body, idx);
            String json = new String(java.util.Arrays.copyOfRange(body, idx, idx + jsonLen),
                    StandardCharsets.UTF_8);
            Map<String, Object> root = Json.parseObject(json);
            Map<String, Object> players = Json.obj(root, "players");
            Map<String, Object> version = Json.obj(root, "version");
            Object motdObj = root.get("description");
            String motd = "";
            if (motdObj instanceof String s) motd = s;
            else if (motdObj instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                motd = Json.str(mm, "text", "");
            }
            Info info = new Info(host, port, true,
                    players == null ? 0 : (int) Json.num(players, "online", 0),
                    players == null ? 0 : (int) Json.num(players, "max", 0),
                    ping,
                    version == null ? "" : Json.str(version, "name", ""),
                    motd, System.currentTimeMillis(), null);
            cached = info;
            Log.debug("Сервер " + host + ":" + port + " → online=" + info.online() + " ping=" + ping + "мс");
            return info;
        } catch (IOException e) {
            Log.debug("Сервер " + host + ":" + port + " недоступен: " + Log.reason(e));
            Info info = Info.unavailable(host, port, Log.reason(e));
            cached = info;
            return info;
        }
    }

    // ------------------------------------------------------------------ VarInt

    private static void writeVarInt(java.io.OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeVarIntStr(java.io.OutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b, 0, b.length);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.read();
            if (b < 0) throw new IOException("Сервер закрыл соединение");
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("Некорректный VarInt от сервера");
    }

    private static int varIntOf(byte[] buf, int offset) {
        int value = 0;
        int shift = 0;
        int i = offset;
        while (i < buf.length) {
            int b = buf[i++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return value;
    }

    private static int skipVarInt(byte[] buf, int offset) {
        int i = offset;
        while (i < buf.length && (buf[i++] & 0x80) != 0) {
            // идём до конца VarInt
        }
        return i;
    }

    /** Вспомогательный парсер порта. */
    static final class Utils {
        static long port(String s, long def) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception e) {
                return def;
            }
        }
    }
}
