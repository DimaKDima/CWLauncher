package ru.cw.launcher.accounts;

import ru.cw.launcher.net.Http;
import ru.cw.launcher.security.Secrets;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сессия Minecraft через Ely.by.
 * Вход пользователя делает ElyAuthManager (OAuth 2.0, без пароля в лаунчере).
 * Для игры подключается authlib-injector. Адрес метаданных Ely.by:
 * https://authserver.ely.by/api/authlib-injector
 */
public final class ElyAuth {

    public static final String AUTH_HOST = "https://authserver.ely.by";
    /** Заголовок X-Authlib-Injector-Api-Location на https://authserver.ely.by */
    public static final String INJECTOR_API = AUTH_HOST + "/api/authlib-injector";
    private static final String INJECTOR_LATEST =
            "https://authlib-injector.yushi.moe/artifact/latest.json,"
                    + "https://bmclapi2.bangbang93.com/mirrors/authlib-injector/artifact/latest.json";
    private static final String INJECTOR_FALLBACK =
            "https://authlib-injector.yushi.moe/artifact/56/authlib-injector-1.2.8.jar,"
                    + "https://bmclapi2.bangbang93.com/mirrors/authlib-injector/artifact/56/authlib-injector-1.2.8.jar";

    public record Result(boolean ok, String username, String uuid, String accessToken, String error) {
    }

    private ElyAuth() {
    }

    /** Логин по нику/e-mail и паролю. Пароль используется только в этом запросе. */
    public static Result login(String login, String password, String clientToken) {
        if (Utils.isBlank(login) || Utils.isBlank(password)) {
            return new Result(false, "", "", "", "Укажите логин и пароль");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", login);
        body.put("password", password);
        body.put("clientToken", clientToken);
        body.put("requestUser", true);
        try {
            String resp = Http.postJson(AUTH_HOST + "/auth/authenticate", Json.writeCompact(body), null);
            Map<String, Object> m = Json.parseObject(resp);
            String token = Json.str(m, "accessToken", "");
            Map<String, Object> profile = Json.obj(m, "selectedProfile");
            String name = profile == null ? login : Json.str(profile, "name", login);
            String uuid = profile == null ? "" : Json.str(profile, "id", "");
            if (token.isBlank()) {
                return new Result(false, name, uuid, "", "Сервер не вернул токен доступа");
            }
            Secrets.put("ely.access." + clientToken, token);
            Log.info("Ely.by: вход выполнен как " + name);
            return new Result(true, name, uuid, token, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, login, "", "", "Вход прерван");
        } catch (IOException e) {
            String msg = e.getMessage() == null ? Log.reason(e) : e.getMessage();
            Log.warn("Ely.by: ошибка входа: " + msg);
            if (msg.contains("two factor")) {
                return new Result(false, login, "", "",
                        "Аккаунт защищён двухфакторной авторизацией. Введите пароль и код: пароль:код");
            }
            if (msg.contains("Invalid credentials")) {
                return new Result(false, login, "", "", "Неверный логин или пароль");
            }
            return new Result(false, login, "", "", "Сервер авторизации недоступен. "
                    + "Проверьте интернет и повторите попытку.");
        }
    }

    /** Обновление сохранённого токена (пароль не нужен). */
    public static Result refresh(String clientToken) {
        String token = Secrets.get("ely.access." + clientToken);
        if (Utils.isBlank(token)) return new Result(false, "", "", "", "Нет сохранённого токена");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", token);
        body.put("clientToken", clientToken);
        body.put("requestUser", true);
        try {
            Map<String, Object> m = Json.parseObject(Http.postJson(AUTH_HOST + "/auth/refresh",
                    Json.writeCompact(body), null));
            String fresh = Json.str(m, "accessToken", "");
            Map<String, Object> profile = Json.obj(m, "selectedProfile");
            if (fresh.isBlank()) return new Result(false, "", "", "", "Сервер не продлил токен");
            Secrets.put("ely.access." + clientToken, fresh);
            return new Result(true, profile == null ? "" : Json.str(profile, "name", ""),
                    profile == null ? "" : Json.str(profile, "id", ""), fresh, null);
        } catch (Exception e) {
            Log.warn("Ely.by: не удалось обновить токен: " + Log.reason(e));
            return new Result(false, "", "", "", "Сессия истекла — войдите заново");
        }
    }

    public static boolean validate(String clientToken) {
        String token = Secrets.get("ely.access." + clientToken);
        if (Utils.isBlank(token)) return false;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", token);
        body.put("clientToken", clientToken);
        try {
            Http.postJson(AUTH_HOST + "/auth/validate", Json.writeCompact(body), null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void logout(String clientToken) {
        String token = Secrets.get("ely.access." + clientToken);
        if (Utils.isBlank(token)) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", token);
        body.put("clientToken", clientToken);
        try {
            Http.postJson(AUTH_HOST + "/auth/invalidate", Json.writeCompact(body), null);
        } catch (Exception e) {
            Log.debug("Ely.by invalidate: " + Log.reason(e));
        }
        Secrets.remove("ely.access." + clientToken);
    }

    public static String token(Account a) {
        return Secrets.get(a.secretKey()) != null ? Secrets.get(a.secretKey())
                : Secrets.get("ely.access." + a.clientToken);
    }

    // ------------------------------------------------------------------ authlib-injector

    public static Path injectorDir() {
        return Paths.root().resolve("injector");
    }

    public static Path injectorJar() {
        return injectorDir().resolve("authlib-injector.jar");
    }

    /** Скачивает authlib-injector с официального адреса (с запасным зеркалом). */
    public static void ensureInjector() throws IOException {
        Path jar = injectorJar();
        if (Files.isRegularFile(jar) && Utils.size(jar) > 10_000 && Utils.looksLikeZip(jar)) return;
        Utils.ensureDirectories(jar.getParent());
        IOException last = null;
        for (String url : injectorUrls()) {
            try {
                Http.download(url.trim(), jar, null, 0, null, null);
                if (!Utils.looksLikeZip(jar)) throw new IOException("Загружен не JAR");
                Log.info("authlib-injector получен: " + jar);
                return;
            } catch (Exception e) {
                last = e instanceof IOException ? (IOException) e : new IOException(e);
                Utils.deleteQuietly(jar);
                Log.warn("authlib-injector: " + Log.reason(e));
            }
        }
        throw last != null ? last : new IOException("Не удалось получить authlib-injector");
    }

    /** Сначала актуальный адрес из latest.json, затем известный jar 1.2.8. Версии 1.6.4 не существует. */
    private static List<String> injectorUrls() {
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        for (String meta : INJECTOR_LATEST.split(",")) {
            try {
                String body = Http.get(meta.trim(), "authlib-latest").body();
                String url = Json.str(Json.parseObject(body), "download_url", "");
                if (url != null && url.startsWith("https://") && !urls.contains(url)) urls.add(url);
            } catch (Exception ignored) {
            }
        }
        for (String url : INJECTOR_FALLBACK.split(",")) {
            if (!urls.contains(url)) urls.add(url);
        }
        return urls;
    }

    /** JVM-аргумент подключения Ely.by к игре. */
    public static String agentArgument() throws IOException {
        return agentArgument(INJECTOR_API);
    }

    /** Тот же authlib-injector, но с другим корнем API. Нужен локальному скину. */
    public static String agentArgument(String apiRoot) throws IOException {
        ensureInjector();
        String root = apiRoot == null || apiRoot.isBlank() ? INJECTOR_API : apiRoot;
        return "-javaagent:" + injectorJar() + "=" + root;
    }
}
