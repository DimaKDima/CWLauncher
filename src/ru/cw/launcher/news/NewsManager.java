package ru.cw.launcher.news;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.network.GoogleDrive;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Новости Common World: источник настраивается (news.json), есть кэш со сроком действия.
 * При недоступности сети используется кэш, лаунчер не ломается.
 */
public final class NewsManager {

    public record Item(String title, String date, String text, String image, String url, long fetchedAt) {
        public boolean hasImage() {
            return image != null && !image.isBlank();
        }
    }

    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;   // 30 минут

    private volatile List<Item> items = new ArrayList<>();
    private volatile boolean fromCache;
    private volatile boolean failed;
    private volatile long checkedAt;

    public List<Item> items() {
        return List.copyOf(items);
    }

    public boolean fromCache() {
        return fromCache;
    }

    public boolean failed() {
        return failed;
    }

    public long checkedAt() {
        return checkedAt;
    }

    /** Загрузка новостей (в worker-потоке). */
    public List<Item> load(Settings cfg, boolean force) {
        Path cache = Paths.cache().resolve("news.json");
        long now = System.currentTimeMillis();
        if (!force && Files.exists(cache) && now - cache.toFile().lastModified() < CACHE_TTL_MS) {
            List<Item> parsed = parse(Utils.size(cache) > 0 ? readCache(cache) : "");
            if (!parsed.isEmpty()) {
                items = parsed;
                fromCache = true;
                failed = false;
                checkedAt = cache.toFile().lastModified();
                return items();
            }
        }
        try {
            String body;
            if (cfg.newsUrl != null && cfg.newsUrl.contains("drive.google.com")) {
                body = GoogleDrive.readText(cfg.newsUrl);
            } else {
                body = Http.get(cfg.newsUrl, null).body();
            }
            List<Item> parsed = parse(body);
            if (parsed.isEmpty()) {
                throw new IOException("Источник новостей не вернул записей");
            }
            items = parsed;
            fromCache = false;
            failed = false;
            checkedAt = System.currentTimeMillis();
            Utils.ensureDirectories(cache.getParent());
            Utils.writeString(cache, body);
            Log.info("Новости загружены: " + parsed.size());
        } catch (Exception e) {
            failed = true;
            String cached = readCache(cache);
            List<Item> parsed = parse(cached);
            if (!parsed.isEmpty()) {
                items = parsed;
                fromCache = true;
                checkedAt = cache.toFile().lastModified();
                Log.warn("Новости недоступны, показан кэш: " + Log.reason(e));
            } else {
                Log.warn("Новости недоступны и кэша нет: " + Log.reason(e));
            }
        }
        return items();
    }

    private String readCache(Path cache) {
        try {
            return Files.exists(cache) ? Utils.readString(cache) : "";
        } catch (IOException e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Item> parse(String body) {
        List<Item> out = new ArrayList<>();
        if (body == null || body.isBlank()) return out;
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return plain(trimmed);
        }
        try {
            Object root = Json.parse(body);
            List<Object> arr = null;
            if (root instanceof List<?> l) arr = new ArrayList<>(l);
            else if (root instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                arr = Json.arr(mm, "news");
                if (arr == null) arr = Json.arr(mm, "items");
                if (arr == null) arr = Json.arr(mm, "updates");
            }
            if (arr == null) return out;
            long now = System.currentTimeMillis();
            for (Object o : arr) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                Map<String, Object> m = (Map<String, Object>) mm;
                String title = Utils.firstNonEmpty(Json.str(m, "title", ""), Json.str(m, "name", ""));
                String text = Utils.firstNonEmpty(Json.str(m, "text", ""), Json.str(m, "content", ""),
                        Json.str(m, "excerpt", ""));
                if (title.isBlank() && text.isBlank()) continue;
                out.add(new Item(Utils.shorten(title, 120), Json.str(m, "date", ""),
                        Utils.shorten(text, 600), Json.str(m, "image", ""),
                        Json.str(m, "link", Json.str(m, "url", "")), now));
                if (out.size() >= 30) break;
            }
        } catch (Exception e) {
            Log.warn("Не удалось разобрать news.json: " + Log.reason(e));
        }
        return out;
    }

    /** News.txt — обычный текст. Первая строка коротко показывается как заголовок, остальное — текст. */
    private List<Item> plain(String body) {
        String[] lines = body.replace("\r\n", "\n").split("\n");
        String title = "";
        String date = "";
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (title.isEmpty()) {
                title = t;
                continue;
            }
            if (date.isEmpty() && t.matches("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}")) {
                date = t;
                continue;
            }
            if (text.length() > 0) text.append('\n');
            text.append(t);
        }
        if (title.isEmpty() && text.length() == 0) return List.of();
        if (title.isEmpty()) title = "Новости";
        return List.of(new Item(title, date, text.toString(), "", "", System.currentTimeMillis()));
    }

    /** История обновлений сборки: updates/<версия>.txt в каталоге сборки. */
    public static List<Map<String, String>> history(String instanceId) {
        List<Map<String, String>> out = new ArrayList<>();
        Path dir = Paths.updatesHistory(instanceId);
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            List<Path> files = s.filter(p -> p.getFileName().toString().endsWith(".txt")).toList();
            files.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
            for (Path p : files) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("version", p.getFileName().toString().replace(".txt", ""));
                m.put("date", new java.text.SimpleDateFormat("dd.MM.yyyy").format(
                        new java.util.Date(p.toFile().lastModified())));
                m.put("text", Utils.readString(p));
                out.add(m);
            }
        } catch (IOException e) {
            Log.warn("История обновлений не прочитана: " + Log.reason(e));
        }
        return out;
    }

    /** update.txt — последние изменения установленной сборки. */
    public static String updateText(String instanceId) {
        Path f = Paths.updateTxt(instanceId);
        try {
            return Files.exists(f) ? Utils.readString(f) : null;
        } catch (IOException e) {
            Log.warn("update.txt не читается: " + Log.reason(e));
            return null;
        }
    }
}
