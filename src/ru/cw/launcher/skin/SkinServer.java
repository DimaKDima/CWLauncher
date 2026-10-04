package ru.cw.launcher.skin;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.accounts.ElyAuth;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Отдаёт локальный скин игре: файл PNG и профиль с текстурой.
 * Нужен, чтобы Minecraft принял картинку из папки skin.
 */
public final class SkinServer {

    private static volatile Path png;
    private static volatile byte[] skinBytes = new byte[0];
    private static volatile boolean slim;
    private static volatile String name = "Player";
    private static volatile String uuid = "00000000000000000000000000000000";
    private static volatile int port;
    private static volatile String metaJson;
    private static ServerSocket socket;

    private SkinServer() {
    }

    /** Адрес API для authlib-injector. Сервер остаётся жить, пока открыт лаунчер. */
    public static synchronized String apiRoot(Path skinFile, Account account) throws IOException {
        png = skinFile;
        byte[] packed = PlayerSkin.gamePng(skinFile);
        skinBytes = packed != null ? packed : Files.readAllBytes(skinFile);
        slim = PlayerSkin.slim(skinFile);
        if (account != null) {
            if (account.username != null && !account.username.isBlank()) name = account.username;
            if (account.shortUuid() != null && !account.shortUuid().isBlank()) {
                uuid = account.shortUuid().replace("-", "").toLowerCase(Locale.ROOT);
            }
        }
        if (metaJson == null) metaJson = loadMeta();
        if (socket == null || socket.isClosed()) {
            socket = new ServerSocket(0, 20, java.net.InetAddress.getByName("127.0.0.1"));
            port = socket.getLocalPort();
            Thread t = new Thread(SkinServer::loop, "cw-skin");
            t.setDaemon(true);
            t.start();
            Log.info("Скин доступен игре: http://127.0.0.1:" + port + "/skin.png");
        }
        return "http://127.0.0.1:" + port + "/";
    }

    private static void loop() {
        while (socket != null && !socket.isClosed()) {
            try (Socket client = socket.accept()) {
                client.setSoTimeout(25000);
                serve(client);
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) Log.debug("Скин-сервер: " + Log.reason(e));
            }
        }
    }

    private static void serve(Socket client) throws IOException {
        InputStream in = new BufferedInputStream(client.getInputStream());
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            head.write(b);
            if (head.size() >= 4) {
                byte[] a = head.toByteArray();
                int n = a.length;
                if (a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') break;
            }
            if (head.size() > 8192) break;
        }
        String req = head.toString(StandardCharsets.ISO_8859_1);
        String method = req.startsWith("POST") ? "POST" : req.startsWith("PUT") ? "PUT" : "GET";
        String path = "/";
        int sp = req.indexOf(' ');
        int sp2 = sp < 0 ? -1 : req.indexOf(' ', sp + 1);
        if (sp > 0 && sp2 > sp) path = req.substring(sp + 1, sp2);
        int q = path.indexOf('?');
        String query = q >= 0 ? path.substring(q) : "";
        if (q >= 0) path = path.substring(0, q);
        int contentLength = headerInt(req, "content-length");
        byte[] reqBody = contentLength > 0 ? in.readNBytes(Math.min(contentLength, 2_000_000)) : new byte[0];
        byte[] body;
        String type;
        if (path.startsWith("/skin")) {
            body = skinBytes == null ? new byte[0] : skinBytes;
            type = "image/png";
        } else if ("/".equals(path)) {
            body = metadata().getBytes(StandardCharsets.UTF_8);
            type = "application/json; charset=utf-8";
        } else if (ourProfile(path)) {
            body = profile().getBytes(StandardCharsets.UTF_8);
            type = "application/json; charset=utf-8";
            Log.info("Игре отдан скин " + name);
        } else {
            writeProxy(client, method, path + query, headerValue(req, "content-type"),
                    headerValue(req, "authorization"), reqBody);
            return;
        }
        BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream());
        String hdr = "HTTP/1.1 200 OK\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static boolean ourProfile(String path) {
        if (path == null || uuid == null || uuid.isBlank()) return false;
        String flat = path.replace("-", "").toLowerCase(Locale.ROOT);
        String id = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return flat.contains("/profile/") && flat.contains(id);
    }

    /**
     * Метаданные Ely, но без ключа подписи. Свой PNG не подписан ключом Ely,
     * и с этим ключом Minecraft выбрасывает текстуру. Домены Ely остаются,
     * чужие скины по-прежнему качаются.
     */
    private static String loadMeta() {
        String local = "{\"meta\":{\"serverName\":\"CW\",\"implementationName\":\"CWLauncher\"},"
                + "\"skinDomains\":[\"127.0.0.1\",\"localhost\",\"ely.by\"]}";
        try {
            String raw = Http.get(ElyAuth.INJECTOR_API, "ely-authlib-meta").body();
            if (raw != null && raw.contains("skinDomains")) {
                Map<String, Object> root = Json.parseObject(raw);
                List<Object> domains = Json.arr(root, "skinDomains");
                if (domains == null) domains = new ArrayList<>();
                if (!domains.contains("127.0.0.1")) domains.add(0, "127.0.0.1");
                if (!domains.contains("localhost")) domains.add(1, "localhost");
                root.put("skinDomains", domains);
                return Json.writeCompact(root);
            }
        } catch (Exception e) {
            Log.warn("Метаданные Ely для скина не прочитаны: " + Log.reason(e));
        }
        return local;
    }

    private static String metadata() {
        String m = metaJson;
        return m == null || m.isBlank() ? loadMeta() : m;
    }

    private static void writeProxy(Socket client, String method, String path, String contentType,
                                   String authorization, byte[] reqBody) throws IOException {
        String url = ElyAuth.INJECTOR_API + (path.startsWith("/") ? path : "/" + path);
        try {
            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(20));
            if (contentType != null) b.header("Content-Type", contentType);
            if (authorization != null) b.header("Authorization", authorization);
            b.header("Accept", "application/json");
            java.net.http.HttpRequest.BodyPublisher body = reqBody.length == 0
                    ? java.net.http.HttpRequest.BodyPublishers.noBody()
                    : java.net.http.HttpRequest.BodyPublishers.ofByteArray(reqBody);
            b.method(method, body);
            java.net.http.HttpResponse<byte[]> resp = java.net.http.HttpClient.newHttpClient()
                    .send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            byte[] data = resp.body() == null ? new byte[0] : resp.body();
            String type = resp.headers().firstValue("content-type").orElse("application/json; charset=utf-8");
            writeHttp(client, resp.statusCode(), type, data);
        } catch (Exception e) {
            byte[] err = Log.reason(e).getBytes(StandardCharsets.UTF_8);
            writeHttp(client, 502, "text/plain; charset=utf-8", err);
        }
    }

    private static int headerInt(String req, String name) {
        String v = headerValue(req, name);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String headerValue(String req, String name) {
        String prefix = name.toLowerCase(java.util.Locale.ROOT) + ":";
        for (String line : req.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private static void writeHttp(Socket client, int code, String type, byte[] body) throws IOException {
        BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream());
        String reason = code == 200 ? "OK" : "Result";
        String hdr = "HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String profile() {
        String url = "http://127.0.0.1:" + port + "/skin.png";
        String model = slim ? ",\"metadata\":{\"model\":\"slim\"}" : "";
        String textures = "{\"timestamp\":" + System.currentTimeMillis()
                + ",\"profileId\":\"" + uuid + "\",\"profileName\":\"" + json(name)
                + "\",\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"" + model + "}}}";
        String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
        // authlib-injector принимает эту подпись и не выбрасывает текстуру.
        return "{\"id\":\"" + uuid + "\",\"name\":\"" + json(name) + "\",\"properties\":[{"
                + "\"name\":\"textures\",\"value\":\"" + value
                + "\",\"signature\":\"authlib-injector-dummy-verify\"}]}";
    }

    /**
     * Кладёт PNG в аккаунт Ely. Тогда скин виден и в одиночной игре, и на сервере:
     * сервер спрашивает Ely, а не файл на этом компьютере.
     */
    public static boolean publishToEly(Account account, byte[] png, boolean slimModel) {
        if (account == null || account.type != Account.Type.ELY || png == null || png.length == 0) return false;
        String token = ElyAuth.token(account);
        String id = account.shortUuid() == null ? "" : account.shortUuid().replace("-", "").toLowerCase(Locale.ROOT);
        if (token == null || token.isBlank() || id.isBlank()) return false;
        String model = slimModel ? "slim" : "default";
        String[] urls = {
                ElyAuth.AUTH_HOST + "/api/user/profile/" + id + "/skin",
                ElyAuth.INJECTOR_API + "/api/user/profile/" + id + "/skin"
        };
        int last = 0;
        for (String url : urls) {
            last = uploadSkin(url, token, png, model);
            if (last >= 200 && last < 300) {
                Log.info("Скин записан в Ely. Другие игроки увидят его после входа на сервер.");
                return true;
            }
            if (last == 400 && !slimModel) {
                last = uploadSkin(url, token, png, "");
                if (last >= 200 && last < 300) {
                    Log.info("Скин записан в Ely. Другие игроки увидят его после входа на сервер.");
                    return true;
                }
            }
            if (last == 401 || last == 403) break;
        }
        Log.warn("Ely не сохранил скин (HTTP " + last
                + "). У себя он виден, другие игроки увидят его, когда Ely примет файл.");
        return false;
    }

    /** 2xx — скин лежит на Ely и его скачает сервер. Иначе код ответа. */
    private static int uploadSkin(String url, String token, byte[] png, String model) {
        String boundary = "cwskin" + System.nanoTime();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try {
            writePart(raw, boundary, "model", model);
            writeFile(raw, boundary, png);
            raw.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(raw.toByteArray()))
                    .build();
            java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                    .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            String body = resp.body() == null ? "" : resp.body().replaceAll("\\s+", " ").trim();
            if (body.length() > 160) body = body.substring(0, 160);
            if (code >= 200 && code < 300 && body.toLowerCase(Locale.ROOT).contains("<html")) {
                Log.warn("Ely вернул страницу вместо сохранения скина, HTTP " + code);
                return 0;
            }
            Log.info("Ely skin HTTP " + code + " " + url.substring(url.indexOf("/api/"))
                    + (body.isBlank() ? "" : " " + body));
            return code;
        } catch (Exception e) {
            Log.warn("Скин в аккаунт Ely не записан: " + Log.reason(e));
            return 0;
        }
    }

    private static void writePart(ByteArrayOutputStream raw, String boundary, String name, String value) {
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        raw.writeBytes(head.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(ByteArrayOutputStream raw, String boundary, byte[] png) {
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        raw.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        raw.writeBytes(png);
        raw.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "");
    }
}
