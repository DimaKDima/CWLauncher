package ru.cw.launcher.accounts;

import ru.cw.launcher.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Minecraft-аккаунт (отдельная сущность от профиля лаунчера — раздел 22 доп. требований).
 * Пароли не хранятся: для Ely.by сохраняется только accessToken/clientToken в DPAPI-хранилище.
 */
public class Account {

    public enum Type {
        OFFLINE("Offline"),
        ELY("Ely.by"),
        MOJANG("Mojang (Microsoft)");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        public static Type of(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return OFFLINE;
        }
    }

    public String id = UUID.randomUUID().toString();
    public Type type = Type.OFFLINE;
    public String username = "";
    public String uuid = offlineUuidOf("").toString();
    public String skinUrl = "";
    /** Ely.by: логин (ник или e-mail). Пароль нигде не сохраняется. */
    public String login = "";
    public String clientToken = "";
    public long createdAt = System.currentTimeMillis();

    public Account() {
    }

    public Account(Type type, String username) {
        this.type = type;
        this.username = username;
        this.login = username;
        this.uuid = offlineUuidOf(username).toString();
    }

    /** Ник оффлайн-аккаунта: 3–16 символов, латиница, цифры и подчёркивание. */
    public static boolean validNick(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    /** Оффлайн UUID = nameUUIDFromBytes("OfflinePlayer:" + name) — как у ванильного сервера. */
    public static UUID offlineUuidOf(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + (name == null ? "" : name))
                .getBytes(StandardCharsets.UTF_8));
    }

    public String shortUuid() {
        return uuid == null ? "" : uuid.replace("-", "");
    }

    public boolean isOnline() {
        return type != Type.OFFLINE;
    }

    public String display() {
        String n = username == null || username.isBlank() ? "без имени" : username;
        return n + " · " + type.label;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type.name());
        m.put("username", username);
        m.put("uuid", uuid);
        m.put("skinUrl", skinUrl);
        m.put("login", login);
        m.put("clientToken", clientToken);
        m.put("createdAt", createdAt);
        return m;
    }

    public static Account fromMap(Map<String, Object> m) {
        Account a = new Account();
        a.id = Json.str(m, "id", a.id);
        a.type = Type.of(Json.str(m, "type", "OFFLINE"));
        a.username = Json.str(m, "username", "");
        a.uuid = Json.str(m, "uuid", a.uuid);
        a.skinUrl = Json.str(m, "skinUrl", "");
        a.login = Json.str(m, "login", "");
        a.clientToken = Json.str(m, "clientToken", "");
        a.createdAt = (long) Json.num(m, "createdAt", System.currentTimeMillis());
        return a;
    }

    /** Ключ секрета в защищённом хранилище. */
    public String secretKey() {
        return "token." + id;
    }

    public Account copy() {
        return fromMap(toMap());
    }
}
