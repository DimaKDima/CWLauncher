package ru.cw.launcher.accounts;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.security.Secrets;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Вход Ely.by по официальному OAuth 2.0.
 * Лаунчер открывает браузер и не видит пароль.
 * Обмен authorization code и refresh выполняет сервер CW: client secret в приложение не вшивается.
 * Документация: https://docs.ely.by/ru/oauth.html и https://docs.ely.by/ru/minecraft-auth.html
 */
public final class ElyAuthManager {

    public static final String AUTHORIZE = "https://account.ely.by/oauth2/v1";
    public static final String ACCOUNT_INFO = "https://account.ely.by/api/account/v1/info";
    public static final String SCOPE = "account_info minecraft_server_session offline_access";
    public static final String SCHEME = "cwlauncher";

    private static final long LOGIN_WAIT_MS = 180_000;
    private static final long SKEW_MS = 60_000;
    private static final Object LOCK = new Object();
    private static String expectedState;
    private static String callbackUri;
    private static boolean waiting;

    public enum Phase {
        OPENING, AUTHORIZING, PROFILE, DONE
    }

    public interface Progress {
        void phase(Phase phase, String text);
    }

    public record Result(boolean ok, String username, String uuid, String error) {
    }

    public record SessionStatus(boolean ok, String message) {
    }

    private record Tokens(String access, String refresh, long expiresIn) {
    }

    private record Callback(String code, String state, String error, String errorMessage) {
    }

    private ElyAuthManager() {
    }

    /** Ссылка авторизации. Параметры кодируются. Секрет в ссылку не входит. */
    public static String authorizeUrl(String clientId, String redirectUri, String state) {
        return AUTHORIZE
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state);
    }

    public static boolean sameState(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** Ely.by регистрирует только сайт. Локальный адрес не подходит. */
    public static boolean redirectAllowed(String uri) {
        if (uri == null) return false;
        String s = uri.trim();
        if (s.isEmpty() || s.contains(" ") || s.contains("\n") || s.contains("\r")) return false;
        URI u;
        try {
            u = URI.create(s);
        } catch (Exception e) {
            return false;
        }
        String scheme = u.getScheme();
        if (scheme == null) return false;
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) return false;
        if (u.getUserInfo() != null) return false;
        String host = u.getHost();
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return !h.equals("localhost") && !h.equals("127.0.0.1") && !h.equals("::1")
                && !h.equals("[::1]") && !h.endsWith(".localhost");
    }

    public static boolean backendAllowed(String uri) {
        return redirectAllowed(uri) && uri.trim().toLowerCase(Locale.ROOT).startsWith("https://");
    }

    public static String explainOauthError(String error, String description) {
        String e = error == null ? "" : error.trim().toLowerCase(Locale.ROOT);
        if (e.equals("access_denied")) return "Вход отменён";
        if (e.equals("invalid_request")) return "Сессия входа истекла. Войдите снова через Ely.by";
        if (e.equals("invalid_client")) return "Приложение Ely.by не подтверждено. Проверьте client id и redirect URI.";
        if (e.equals("invalid_scope")) return "Ely.by отклонил запрошенные права.";
        if (e.equals("server_error") || e.equals("temporarily_unavailable")) return "Ely.by недоступен";
        if (description != null && description.toLowerCase(Locale.ROOT).contains("denied")) return "Вход отменён";
        return "Ely.by отклонил вход";
    }

    /** Пусто, если client id, redirect URI и адрес сервера CW заданы верно. */
    public static String configError(Settings cfg) {
        String id = cfg == null || cfg.elyClientId == null ? "" : cfg.elyClientId.trim();
        String redirect = cfg == null || cfg.elyRedirectUri == null ? "" : cfg.elyRedirectUri.trim();
        String backend = cfg == null || cfg.elyBackendUrl == null ? "" : cfg.elyBackendUrl.trim();
        if (id.isEmpty() || redirect.isEmpty() || backend.isEmpty()) {
            return "Чтобы войти через Ely.by, укажите client id, точный redirect URI и адрес сервера CW. "
                    + "Пароль и client secret в лаунчер не вводятся. "
                    + "На Ely.by приложение типа «Веб-сайт», имя CWLauncher. "
                    + "Redirect URI в кабинете Ely.by и в лаунчере должен совпадать полностью.";
        }
        if (!redirectAllowed(redirect)) {
            return "Redirect URI должен быть адресом сайта, который зарегистрирован в приложении Ely.by. "
                    + "Локальный адрес 127.0.0.1 и случайный URL не подходят.";
        }
        if (!backendAllowed(backend)) {
            return "Адрес сервера CW должен начинаться с https://. "
                    + "На этом сервере хранится client secret и выполняется обмен кода на токен.";
        }
        return null;
    }

    /**
     * Второй экземпляр лаунчера (обработчик cwlauncher:) передаёт сюда URI возврата.
     * Код и токены в журнал не пишутся.
     */
    public static void deliver(String uri) {
        if (uri == null || uri.isBlank()) return;
        synchronized (LOCK) {
            if (!waiting || expectedState == null) {
                Log.info("Ely.by: возврат из браузера без активного входа");
                return;
            }
            callbackUri = uri.trim();
            LOCK.notifyAll();
        }
    }

    public static Result startLogin(Settings cfg, AccountManager accounts, Progress progress) {
        String problem = configError(cfg);
        if (problem != null) return new Result(false, "", "", problem);
        synchronized (LOCK) {
            if (waiting) return new Result(false, "", "", "Вход уже выполняется");
            waiting = true;
            expectedState = newState();
            callbackUri = null;
        }
        String state = expectedState;
        try {
            registerProtocol();
            report(progress, Phase.OPENING, "Открываем Ely.by…");
            String url = authorizeUrl(cfg.elyClientId.trim(), cfg.elyRedirectUri.trim(), state);
            if (!Utils.openUrl(url)) {
                return fail("Не удалось открыть браузер");
            }
            report(progress, Phase.AUTHORIZING, "Авторизация…");
            Log.info("Ely.by: открыт сайт входа");
            Tokens tokens = waitForTokens(cfg, state);
            report(progress, Phase.PROFILE, "Получение профиля…");
            Profile profile = accountInfo(tokens.access);
            if (Utils.isBlank(profile.username)) {
                return fail("У аккаунта нет профиля Minecraft");
            }
            Account account = accounts.addEly(profile.username, profile.username, profile.uuid);
            store(account, tokens.access, tokens.refresh, tokens.expiresIn);
            accounts.setActive(account.id);
            report(progress, Phase.DONE, "Готово");
            Log.info("Ely.by: вход выполнен как " + profile.username);
            return new Result(true, profile.username, profile.uuid, null);
        } catch (AuthException e) {
            return fail(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("Вход прерван");
        } catch (IOException e) {
            return fail(networkMessage(e, true));
        } finally {
            clearPending();
        }
    }

    public static void logout(Account account) {
        if (account == null) return;
        Secrets.remove(account.secretKey());
        Secrets.remove(refreshKey(account.id));
        Secrets.remove(expiryKey(account.id));
        if (!Utils.isBlank(account.clientToken)) Secrets.remove("ely.access." + account.clientToken);
        Log.info("Ely.by: локальная сессия удалена");
    }

    /** Проверяет сохранённый токен. При истечении обновляет его через сервер CW. */
    public static SessionStatus ensureSession(Settings cfg, Account account, boolean probe) {
        if (account == null || account.type != Account.Type.ELY) return new SessionStatus(true, null);
        String access = Secrets.get(account.secretKey());
        String refresh = Secrets.get(refreshKey(account.id));
        boolean fresh = !Utils.isBlank(access) && !expired(account.id);
        if (fresh && !probe) return new SessionStatus(true, null);
        if (fresh) {
            try {
                Profile profile = accountInfo(access);
                if (!Utils.isBlank(profile.username)) {
                    account.username = profile.username;
                    if (!Utils.isBlank(profile.uuid)) account.uuid = profile.uuid;
                    return new SessionStatus(true, null);
                }
            } catch (AuthException e) {
                if (!e.rejected) return new SessionStatus(true, null);
            } catch (IOException e) {
                return new SessionStatus(true, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SessionStatus(true, null);
            }
        }
        if (Utils.isBlank(refresh)) {
            logout(account);
            return new SessionStatus(false, "Сессия истекла. Войдите снова через Ely.by");
        }
        if (configError(cfg) != null) {
            return new SessionStatus(false, "Сессия истекла. Войдите снова через Ely.by");
        }
        try {
            Tokens tokens = refresh(cfg, refresh);
            if (!Utils.isBlank(tokens.access)) {
                store(account, tokens.access, Utils.isBlank(tokens.refresh) ? refresh : tokens.refresh, tokens.expiresIn);
                return new SessionStatus(true, null);
            }
        } catch (AuthException e) {
            logout(account);
            return new SessionStatus(false, e.getMessage() == null
                    ? "Сессия истекла. Войдите снова через Ely.by" : e.getMessage());
        } catch (IOException e) {
            if (!Utils.isBlank(access) && !expired(account.id)) return new SessionStatus(true, null);
            return new SessionStatus(false, networkMessage(e, false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SessionStatus(false, "Вход прерван");
        }
        logout(account);
        return new SessionStatus(false, "Сессия истекла. Войдите снова через Ely.by");
    }

    /** Регистрирует схему cwlauncher: для текущего пользователя Windows. */
    public static void registerProtocol() {
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase(Locale.ROOT).contains("win")) return;
        java.nio.file.Path exe = Paths.launcherExecutable();
        if (exe == null) return;
        String command = "\"" + exe + "\" \"%1\"";
        reg("HKCU\\Software\\Classes\\" + SCHEME, "/ve", "/d", "URL:CWLauncher", "/f");
        reg("HKCU\\Software\\Classes\\" + SCHEME, "/v", "URL Protocol", "/d", "", "/f");
        reg("HKCU\\Software\\Classes\\" + SCHEME + "\\shell\\open\\command", "/ve", "/d", command, "/f");
    }

    private static Tokens waitForTokens(Settings cfg, String state) throws IOException, InterruptedException, AuthException {
        long end = System.currentTimeMillis() + LOGIN_WAIT_MS;
        int netFails = 0;
        while (System.currentTimeMillis() < end) {
            String uri;
            synchronized (LOCK) {
                uri = callbackUri;
                callbackUri = null;
            }
            if (uri != null) {
                Callback callback = parseCallback(uri);
                if (!sameState(state, callback.state)) {
                    throw new AuthException("Вход отклонён: ответ Ely.by не совпал с этим запуском.", true);
                }
                if (callback.error != null) {
                    throw new AuthException(explainOauthError(callback.error, callback.errorMessage), true);
                }
                if (!Utils.isBlank(callback.code)) {
                    return exchange(cfg, callback.code, state);
                }
            }
            try {
                Tokens polled = poll(cfg, state);
                if (polled != null) return polled;
                netFails = 0;
            } catch (AuthException e) {
                throw e;
            } catch (IOException e) {
                netFails++;
                if (netFails >= 8) throw new IOException("Сервер авторизации CW недоступен", e);
            }
            synchronized (LOCK) {
                LOCK.wait(1500);
            }
        }
        throw new AuthException("Время входа истекло. Сайт Ely.by не вернул в лаунчер.", true);
    }

    private static Tokens exchange(Settings cfg, String code, String state) throws IOException, InterruptedException, AuthException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("redirect_uri", cfg.elyRedirectUri.trim());
        body.put("state", state);
        HttpResponse<String> resp = post(base(cfg) + "/exchange", Json.writeCompact(body));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw tokenFailure(resp.statusCode(), resp.body());
        }
        Tokens tokens = readTokens(resp.body());
        if (tokens == null || Utils.isBlank(tokens.access)) {
            throw new AuthException("Сессия входа истекла. Войдите снова через Ely.by", true);
        }
        return tokens;
    }

    private static Tokens poll(Settings cfg, String state) throws IOException, InterruptedException, AuthException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base(cfg) + "/session?state=" + enc(state)))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "CWLauncher/" + Log.VERSION)
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code == 204 || code == 202 || code == 404) return null;
        if (code < 200 || code >= 300) throw tokenFailure(code, resp.body());
        return readTokens(resp.body());
    }

    private static Tokens refresh(Settings cfg, String refresh) throws IOException, InterruptedException, AuthException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refresh_token", refresh);
        HttpResponse<String> resp = post(base(cfg) + "/refresh", Json.writeCompact(body));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) throw tokenFailure(resp.statusCode(), resp.body());
        Tokens tokens = readTokens(resp.body());
        if (tokens == null || Utils.isBlank(tokens.access)) {
            throw new AuthException("Сессия истекла. Войдите снова через Ely.by", true);
        }
        return tokens;
    }

    private static Profile accountInfo(String access) throws IOException, InterruptedException, AuthException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(ACCOUNT_INFO))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + access)
                .header("Accept", "application/json")
                .header("User-Agent", "CWLauncher/" + Log.VERSION)
                .GET()
                .build();
        HttpResponse<String> resp;
        try {
            resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw e;
        }
        int code = resp.statusCode();
        if (code == 401) throw new AuthException("Сессия истекла. Войдите снова через Ely.by", true);
        if (code == 403) throw new AuthException("Сессия истекла. Войдите снова через Ely.by", true);
        if (code < 200 || code >= 300) {
            throw new IOException(code >= 500 ? "Ely.by недоступен" : "Не удалось подключиться к Ely.by");
        }
        Map<String, Object> info = Json.parseObject(resp.body());
        String name = Json.str(info, "username", "");
        String uuid = normalizeUuid(Json.str(info, "uuid", ""));
        return new Profile(name, uuid);
    }

    private static Tokens readTokens(String body) throws AuthException {
        if (body == null || body.isBlank()) return null;
        Map<String, Object> m;
        try {
            m = Json.parseObject(body);
        } catch (RuntimeException e) {
            throw new AuthException("Ely.by вернул непонятный ответ", false);
        }
        String status = Json.str(m, "status", "");
        if ("pending".equalsIgnoreCase(status)) return null;
        if ("error".equalsIgnoreCase(status)) {
            throw new AuthException(explainOauthError(Json.str(m, "error", ""),
                    Json.str(m, "message", Json.str(m, "error_description", ""))), true);
        }
        String access = Json.str(m, "access_token", "");
        if (access.isBlank()) {
            String err = Json.str(m, "error", "");
            if (!err.isBlank()) {
                throw new AuthException(explainOauthError(err, Json.str(m, "error_description", "")), true);
            }
            return null;
        }
        long expires = Json.lng(m, "expires_in", 86400);
        if (expires <= 0) expires = 86400;
        return new Tokens(access, Json.str(m, "refresh_token", ""), expires);
    }

    private static AuthException tokenFailure(int status, String body) {
        String error = "";
        String description = "";
        try {
            if (body != null && body.trim().startsWith("{")) {
                Map<String, Object> m = Json.parseObject(body);
                error = Json.str(m, "error", "");
                description = Json.str(m, "error_description", Json.str(m, "message", ""));
            }
        } catch (Exception ignored) {
        }
        if (!error.isBlank()) return new AuthException(explainOauthError(error, description), true);
        if (status == 401 || status == 400) {
            return new AuthException("Сессия истекла. Войдите снова через Ely.by", true);
        }
        if (status >= 500) return new AuthException("Ely.by недоступен", false);
        return new AuthException("Не удалось подключиться к Ely.by", false);
    }

    private static void store(Account account, String access, String refresh, long expiresIn) {
        Secrets.put(account.secretKey(), access);
        if (!Utils.isBlank(refresh)) Secrets.put(refreshKey(account.id), refresh);
        long until = System.currentTimeMillis() + Math.max(60, expiresIn) * 1000L;
        Secrets.put(expiryKey(account.id), Long.toString(until));
    }

    private static boolean expired(String accountId) {
        String raw = Secrets.get(expiryKey(accountId));
        if (raw == null || raw.isBlank()) return false;
        try {
            return System.currentTimeMillis() + SKEW_MS >= Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static Callback parseCallback(String raw) throws AuthException {
        String text = raw.trim();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 2) {
            text = text.substring(1, text.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (Exception e) {
            throw new AuthException("Вход отклонён: ответ Ely.by не совпал с этим запуском.", true);
        }
        if (uri.getScheme() == null || !SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new AuthException("Вход отклонён: ответ Ely.by не совпал с этим запуском.", true);
        }
        Map<String, String> q = parseQuery(uri.getRawQuery());
        String error = q.get("error");
        return new Callback(q.get("code"), q.get("state"),
                error == null || error.isBlank() ? null : error,
                q.getOrDefault("error_message", q.get("error_description")));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        if (query == null) return m;
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            m.put(urlDecode(part.substring(0, i)), urlDecode(part.substring(i + 1)));
        }
        return m;
    }

    private static String newState() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static void clearPending() {
        synchronized (LOCK) {
            waiting = false;
            expectedState = null;
            callbackUri = null;
        }
    }

    private static Result fail(String message) {
        clearPending();
        Log.warn("Ely.by: " + message);
        return new Result(false, "", "", message);
    }

    private static void report(Progress progress, Phase phase, String text) {
        if (progress != null) progress.phase(phase, text);
    }

    private static String base(Settings cfg) {
        String b = cfg.elyBackendUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private static HttpResponse<String> post(String url, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "CWLauncher/" + Log.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return client().send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    private static String networkMessage(IOException e, boolean login) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("Сервер авторизации CW")) return m;
        if (m.contains("Ely.by недоступен")) return m;
        Log.warn("Ely.by: сеть: " + e.getClass().getSimpleName());
        if (m.toLowerCase(Locale.ROOT).contains("unknownhost") || m.contains("timed out") || m.contains("Timeout")) {
            return login ? "Не удалось подключиться к Ely.by" : "Ely.by недоступен";
        }
        return "Не удалось подключиться к Ely.by";
    }

    private static void reg(String key, String... rest) {
        try {
            String[] cmd = new String[3 + rest.length];
            cmd[0] = "reg";
            cmd[1] = "add";
            cmd[2] = key;
            System.arraycopy(rest, 0, cmd, 3, rest.length);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
        } catch (Exception e) {
            Log.warn("Схема cwlauncher не зарегистрирована: " + Log.reason(e));
        }
    }

    private static String refreshKey(String id) {
        return "ely.refresh." + id;
    }

    private static String expiryKey(String id) {
        return "ely.exp." + id;
    }

    static String normalizeUuid(String uuid) {
        if (uuid == null) return "";
        String s = uuid.trim();
        if (s.length() == 32 && s.indexOf('-') < 0) {
            return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                    + "-" + s.substring(16, 20) + "-" + s.substring(20);
        }
        return s;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private record Profile(String username, String uuid) {
    }

    private static final class AuthException extends Exception {
        final boolean rejected;

        AuthException(String message, boolean rejected) {
            super(message);
            this.rejected = rejected;
        }
    }
}
