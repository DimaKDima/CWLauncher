package ru.cw.launcher.net;

import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Utils;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * HTTP клиент: таймауты, повторные попытки, gzip, условный кэш (ETag / Last-Modified),
 * докачка прерванных файлов, прогресс, отмена.
 */
public final class Http {

    public interface Progress {
        /** total <= 0 — неизвестный размер. */
        void onProgress(long done, long total);
    }

    /** Отмена операции: вернуть true, чтобы остановить загрузку. */
    public interface Cancel {
        boolean cancelled();
    }

    public record Cached(String body, boolean fromCache) {
    }

    private static volatile Path cacheDir;
    private static volatile HttpClient client;
    private static volatile String userAgent = "CWLauncher/1.0";

    private Http() {
    }

    public static void init(Path cache, int connectTimeoutMs, int readTimeoutMs) {
        cacheDir = cache;
        userAgent = "CWLauncher/" + Log.VERSION + " (Windows; Java "
                + System.getProperty("java.version", "?") + ")";
        reconfigure();
        try {
            if (cache != null) Files.createDirectories(cache);
        } catch (IOException e) {
            Log.warn("Каталог кэша недоступен: " + e.getMessage());
        }
    }

    public static void reconfigure() {
        if (NetPolicy.proxy()) System.setProperty("java.net.useSystemProxies", "true");
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(NetPolicy.leader() ? 8 : 15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (NetPolicy.proxy()) b.proxy(ProxySelector.getDefault());
        client = b.build();
    }

    private static HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            synchronized (Http.class) {
            if (client == null) client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
                c = client;
            }
        }
        return c;
    }

    /**
     * GET текста с повторными попытками.
     *
     * @param cacheKey ключ для условного кэша (null — без кэша)
     */
    public static Cached get(String url, String cacheKey) throws IOException, InterruptedException {
        Path entry = cacheKey == null ? null : cacheEntry(cacheKey);
        String etag = null;
        String lastMod = null;
        String stale = null;
        if (entry != null && Files.exists(entry)) {
            try {
                String raw = Files.readString(entry, StandardCharsets.UTF_8);
                int sep = raw.indexOf("\n\n");
                if (sep > 0) {
                    String head = raw.substring(0, sep);
                    stale = raw.substring(sep + 2);
                    for (String h : head.split("\n")) {
                        int i = h.indexOf(':');
                        if (i <= 0) continue;
                        String k = h.substring(0, i).trim().toLowerCase(Locale.ROOT);
                        String v = h.substring(i + 1).trim();
                        if (k.equals("etag")) etag = v;
                        if (k.equals("last-modified")) lastMod = v;
                    }
                } else {
                    stale = raw;
                }
            } catch (IOException e) {
                stale = null;
            }
        }

        IOException last = null;
        int attempts = NetPolicy.attempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Encoding", "gzip");
                if (etag != null) b.header("If-None-Match", etag);
                else if (lastMod != null) b.header("If-Modified-Since", lastMod);
                HttpResponse<InputStream> resp = client().send(b.GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                int code = resp.statusCode();
                if (code == 304 && stale != null) {
                    Log.debug("HTTP 304 (кэш) " + url);
                    return new Cached(stale, true);
                }
                if (code >= 200 && code < 300) {
                    String body = readBody(resp);
                    if (entry != null) saveCache(entry, resp, body);
                    return new Cached(body, false);
                }
                if (code == 404) throw new IOException("404 Not Found: " + url);
                throw new IOException("HTTP " + code + " для " + url);
            } catch (IOException e) {
                last = e;
                if (attempt < attempts) {
                    Log.warn("Попытка " + attempt + "/" + attempts + " не удалась (" + e.getMessage()
                            + "), повтор через " + (attempt * 800) + " мс: " + shortUrl(url));
                    Thread.sleep(attempt * 800L);
                }
            }
        }
        if (stale != null) {
            Log.warn("Сеть недоступна, использован устаревший кэш: " + shortUrl(url));
            return new Cached(stale, true);
        }
        throw last != null ? last : new IOException("Не удалось выполнить запрос: " + url);
    }

    public static String getBody(String url) throws IOException, InterruptedException {
        return get(url, null).body();
    }

    /**
     * Скачивание файла в tmp с проверкой контрольной суммы и атомарной заменой.
     * Поддерживает докачку, если сервер принимает Range.
     */
    public static void download(String url, Path dest, String sha1, long expectedSize,
                               Progress progress, Cancel cancel) throws IOException, InterruptedException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".download");
        long start = 0;
        if (Files.exists(tmp)) {
            start = Files.size(tmp);
            if (expectedSize > 0 && start >= expectedSize) start = 0; // перечитать
        } else {
            start = 0;
        }
        IOException last = null;
        int attempts = NetPolicy.attempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (cancel != null && cancel.cancelled()) throw new IOException("Отменено пользователем");
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(NetPolicy.readTimeoutSec()))
                        .header("User-Agent", userAgent)
                        .header("Accept-Encoding", "identity");
                if (start > 0) b.header("Range", "bytes=" + start + "-");
                HttpResponse<InputStream> resp = client().send(b.build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                int code = resp.statusCode();
                boolean append = code == 206 && start > 0;
                if (code != 200 && code != 206) {
                    throw new IOException("HTTP " + code + " для " + url);
                }
                if (code == 200 && start > 0) {
                    // сервер не поддержал Range — начинаем заново
                    start = 0;
                }
                long total = expectedSize;
                String cl = resp.headers().firstValue("content-length").orElse(null);
                if (cl != null) total = start + Utils.parseLong(cl, 0);
                try (InputStream in = resp.body();
                     var out = Files.newOutputStream(tmp, openOptions(append))) {
                    byte[] buf = new byte[NetPolicy.bufferSize()];
                    long done = append ? start : 0;
                    int r;
                    long lastReport = 0;
                    while ((r = in.read(buf)) > 0) {
                        if (cancel != null && cancel.cancelled()) throw new IOException("Отменено пользователем");
                        NetPolicy.acquire(r);
                        out.write(buf, 0, r);
                        done += r;
                        if (progress != null && System.currentTimeMillis() - lastReport > 120) {
                            lastReport = System.currentTimeMillis();
                            progress.onProgress(done, total);
                        }
                    }
                    if (progress != null) progress.onProgress(done, total > done ? total : done);
                }
                long size = Files.size(tmp);
                if (expectedSize > 0 && size != expectedSize) {
                    throw new IOException("Размер файла не совпадает: ожидалось " + expectedSize + ", получено "
                            + size);
                }
                if (sha1 != null && !sha1.isBlank()) {
                    String actual = Utils.sha1(tmp);
                    if (!actual.equalsIgnoreCase(sha1)) {
                        Files.deleteIfExists(tmp);
                        throw new IOException("Контрольная сумма не совпала (" + dest.getFileName() + ")");
                    }
                }
                try {
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                start = Files.exists(tmp) ? Files.size(tmp) : 0;
                if (attempt < attempts) {
                    Log.warn("Загрузка " + shortUrl(url) + ": попытка " + attempt + " сорвана (" + e.getMessage()
                            + "), продолжение с " + Utils.humanSize(start));
                    Thread.sleep(attempt * 1000L);
                }
            }
        }
        throw last != null ? last : new IOException("Не удалось скачать " + url);
    }

    private static java.nio.file.OpenOption[] openOptions(boolean append) {
        if (append) {
            return new java.nio.file.OpenOption[]{
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND
            };
        }
        return new java.nio.file.OpenOption[]{
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
        };
    }

    private static String readBody(HttpResponse<InputStream> resp) throws IOException {
        InputStream raw = resp.body();
        String enc = resp.headers().firstValue("content-encoding").orElse("");
        boolean gzip = enc.toLowerCase(Locale.ROOT).contains("gzip");
        try (raw) {
            InputStream in = gzip ? new GZIPInputStream(raw) : raw;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path cacheEntry(String key) {
        Path base = cacheDir;
        if (base == null) return null;
        String h = Utils.sha1Hex(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return base.resolve(h.substring(0, 2)).resolve(h + ".http");
    }

    private static void saveCache(Path entry, HttpResponse<InputStream> resp, String body) {
        if (entry == null) return;
        try {
            StringBuilder head = new StringBuilder();
            resp.headers().firstValue("etag")
                    .ifPresent(v -> head.append("etag:").append(v).append('\n'));
            resp.headers().firstValue("last-modified")
                    .ifPresent(v -> head.append("last-modified:").append(v).append('\n'));
            Files.createDirectories(entry.getParent());
            Files.writeString(entry, head + "\n\n" + body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.debug("Не удалось сохранить кэш: " + e.getMessage());
        }
    }

    private static String shortUrl(String url) {
        if (url == null) return "";
        return url.length() > 90 ? url.substring(0, 87) + "..." : url;
    }

    public static boolean head(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", userAgent)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> r = client().send(req, HttpResponse.BodyHandlers.discarding());
            return r.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /** POST JSON (для API авторизации). Возвращает тело ответа. */
    public static String postJson(String url, String jsonBody, String authorization)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", userAgent)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Accept", "application/json");
                if (authorization != null && !authorization.isBlank()) {
                    b.header("Authorization", authorization);
                }
                HttpResponse<InputStream> resp = client().send(
                        b.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                int code = resp.statusCode();
                String body = readBody(resp);
                if (code >= 200 && code < 300) return body;
                throw new IOException("HTTP " + code + (body.isBlank() ? "" : ": " + Utils.shorten(body, 200)));
            } catch (IOException e) {
                last = e;
                if (attempt < 2) Thread.sleep(700L);
            }
        }
        throw last != null ? last : new IOException("POST не выполнен: " + url);
    }

    /** Скачивание текста (UTF-8) без кэша. */
    public static String getText(String url) throws IOException, InterruptedException {
        return get(url, null).body();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean macos() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    public static String osName() {
        return VersionJsonOs.name();
    }

    public static String archJava() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
        return "x64";
    }

    /** Небольшая вспомогательная структура имени ОС. */
    static final class VersionJsonOs {
        static String name() {
            if (isWindows()) return "windows";
            if (macos()) return "osx";
            return "linux";
        }
    }
}
