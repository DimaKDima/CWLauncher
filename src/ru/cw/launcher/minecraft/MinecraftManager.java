package ru.cw.launcher.minecraft;

import ru.cw.launcher.core.Operation;
import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.net.Downloads;
import ru.cw.launcher.net.Http;
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
 * MinecraftVersionManager: список версий, получение profile.json / client.jar /
 * библиотек / assets из официальных источников Mojang, проверка целостности (SHA-1)
 * и подготовка к запуску. CWLauncher не содержит файлы Minecraft — всё скачивается
 * заново на машине пользователя.
 */
public final class MinecraftManager {

    public static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public record VersionEntry(String id, String type, String url, String releaseTime, boolean installed) {
        public String label() {
            String t = switch (type) {
                case "release" -> "релиз";
                case "snapshot" -> "снапшот";
                case "old_beta" -> "beta";
                case "old_alpha" -> "alpha";
                default -> type;
            };
            return id + " (" + t + ")";
        }
    }

    private List<VersionEntry> remoteCache = new ArrayList<>();

    /** Официальный список версий. При недоступности сети — последний кэш или локальные профили. */
    public List<VersionEntry> versionList() throws IOException {
        try {
            String body = Http.get(MANIFEST_URL, "mojang-manifest").body();
            Map<String, Object> root = Json.parseObject(body);
            List<Object> versions = Json.arr(root, "versions");
            List<VersionEntry> out = new ArrayList<>();
            if (versions != null) {
                for (Object o : versions) {
                    if (!(o instanceof Map<?, ?> mm)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) mm;
                    String id = Json.str(m, "id", "");
                    if (id.isEmpty()) continue;
                    out.add(new VersionEntry(id, Json.str(m, "type", "release"),
                            Json.str(m, "url", ""), Json.str(m, "releaseTime", ""),
                            Files.exists(Paths.versionJsonPath(id))));
                }
            }
            if (out.isEmpty()) throw new IOException("Пустой список версий Mojang");
            remoteCache = out;
            return out;
        } catch (IOException e) {
            if (!remoteCache.isEmpty()) {
                Log.warn("Mojang недоступен — использован кэш списка версий");
                return remoteCache;
            }
            List<VersionEntry> local = new ArrayList<>();
            for (String id : localProfiles()) {
                local.add(new VersionEntry(id, "release", "", "", true));
            }
            if (local.isEmpty()) throw e;
            return local;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Проверка версий прервана");
        }
    }

    public List<VersionEntry> releases() throws IOException {
        List<VersionEntry> out = new ArrayList<>();
        for (VersionEntry v : versionList()) if ("release".equals(v.type())) out.add(v);
        return out;
    }

    public List<VersionEntry> snapshots() throws IOException {
        List<VersionEntry> out = new ArrayList<>();
        for (VersionEntry v : versionList()) if ("snapshot".equals(v.type())) out.add(v);
        return out;
    }

    /** Локально установленные vanilla-версии Minecraft. */
    public List<String> installedVersions() {
        List<String> out = new ArrayList<>();
        for (VersionEntry v : remoteCache) {
            if (v.installed() && isVanillaProfile(v.id()) && !out.contains(v.id())) out.add(v.id());
        }
        for (String id : localProfiles()) {
            if (isVanillaProfile(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** Все локальные профили (vanilla + Fabric/Quilt). */
    public List<String> localProfiles() {
        List<String> out = new ArrayList<>();
        Path dir = Paths.versions();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            for (Path p : s.filter(Files::isDirectory).toList()) {
                String id = p.getFileName().toString();
                if (Files.exists(Paths.versionJsonPath(id))) out.add(id);
            }
        } catch (IOException e) {
            Log.warn("Список версий не прочитан: " + Log.reason(e));
        }
        out.sort((a, b) -> b.compareTo(a));
        return out;
    }

    public boolean isVanillaProfile(String id) {
        return !id.startsWith("fabric-loader-") && !id.startsWith("quilt-loader-");
    }

    public boolean isInstalled(String version) {
        return versionReady(version);
    }

    /**
     * Jar клиента. У Fabric своего jar нет: он лежит у родительской версии (inheritsFrom).
     */
    public Path clientJar(String version) {
        return clientJar(version, 0);
    }

    private Path clientJar(String version, int depth) {
        Path own = Paths.versionJar(version);
        if (Files.exists(own) || depth > 4) return own;
        try {
            VersionJson v = load(version);
            if (!Utils.isBlank(v.inheritsFrom) && !v.inheritsFrom.equals(version)) {
                return clientJar(v.inheritsFrom, depth + 1);
            }
        } catch (Exception ignored) {
        }
        return own;
    }

    /** Версия полностью готова (profile + client + libraries + assets). */
    public boolean versionReady(String version) {
        try {
            if (!Files.exists(Paths.versionJsonPath(version))) return false;
            if (!Files.exists(clientJar(version))) return false;
            VersionJson v = resolved(version);
            if (Utils.isBlank(v.mainClass)) return false;
            for (VersionJson.Lib lib : v.libraries) {
                if (lib.download && lib.path != null && !Files.exists(Paths.libs().resolve(lib.path))) {
                    return false;
                }
            }
            if (!Files.exists(Paths.assets().resolve("indexes").resolve(v.assets + ".json"))) return false;
            return missingAssets(version) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public VersionJson load(String id) throws IOException {
        return VersionJson.load(Paths.versionJsonPath(id));
    }

    /** Профиль с учётом inheritsFrom (для Fabric-профилей). */
    public VersionJson resolved(String id) throws IOException {
        VersionJson v = load(id);
        String parent = v.inheritsFrom;
        int guard = 0;
        while (!Utils.isBlank(parent) && !parent.equals(id) && guard++ < 4) {
            VersionJson pv = load(parent);
            v = VersionJson.merge(v, pv);
            parent = pv.inheritsFrom;
        }
        return v;
    }

    public String clientUrl(String version) throws IOException {
        for (VersionEntry e : versionList()) {
            if (e.id().equals(version)) return e.url();
        }
        throw new IOException("Версия " + version + " не найдена в списке Mojang");
    }

    /** Реальные задачи установки версии Minecraft. */
    public List<Operation.Task> installTasks(String version, ProgressListener pl) {
        List<Operation.Task> tasks = new ArrayList<>();
        Path json = Paths.versionJsonPath(version);
        tasks.add(new Operation.Task("Получение профиля версии " + version, 1, () -> {
            String url = clientUrl(version);
            String body = Http.get(url, "version-json-" + version).body();
            Utils.ensureDirectories(json.getParent());
            Utils.writeString(json, body);
            VersionJson parsed = VersionJson.load(json);
            if (Utils.isBlank(parsed.mainClass)) {
                throw new IOException("Профиль версии " + version + " повреждён");
            }
            pl.progress(1, 1, "Профиль версии сохранён");
        }));
        tasks.add(new Operation.Task("Загрузка client.jar", 6, () -> {
            VersionJson v = load(version);
            Path client = Paths.versionJar(version);
            Utils.ensureDirectories(client.getParent());
            if (sameFile(client, v.clientSize, v.clientSha1)) {
                pl.progress(1, 1, "Клиент уже загружен");
                return;
            }
            Http.download(v.clientUrl, client, v.clientSha1, v.clientSize,
                    (done, total) -> pl.progress(done, total, "Клиент " + Utils.humanSize(done)),
                    pl::isCancelled);
        }));
        tasks.add(new Operation.Task("Загрузка библиотек", 8, () -> {
            VersionJson v = load(version);
            List<VersionJson.Lib> libs = new ArrayList<>();
            for (VersionJson.Lib l : v.libraries) {
                if (l.download && l.path != null) libs.add(l);
            }
            List<Downloads.Item> batch = new ArrayList<>();
            for (VersionJson.Lib lib : libs) {
                Path target = Paths.libs().resolve(lib.path);
                if (sameFile(target, lib.size, lib.sha1)) continue;
                batch.add(new Downloads.Item(lib.fullUrl(), target, lib.sha1, lib.size));
            }
            pl.progress(0, Math.max(1, batch.size()), "Библиотек к загрузке: " + batch.size());
            int failed = Downloads.fetch(batch, pl, "Библиотеки");
            if (failed > 0) {
                throw new IOException("Не удалось загрузить библиотеки Minecraft: " + failed
                        + ". Проверьте интернет и повторите установку.");
            }
        }));
        tasks.add(new Operation.Task("Загрузка индекса ресурсов", 2, () -> {
            VersionJson v = load(version);
            if (Utils.isBlank(v.assetIndexUrl)) {
                pl.progress(1, 1, "Индекс ресурсов не требуется");
                return;
            }
            Path index = Paths.assets().resolve("indexes").resolve(v.assets + ".json");
            Utils.ensureDirectories(index.getParent());
            if (sameFile(index, v.assetIndexSize, v.assetIndexSha1)) {
                pl.progress(1, 1, "Индекс ресурсов уже есть");
                return;
            }
            Http.download(v.assetIndexUrl, index, v.assetIndexSha1, v.assetIndexSize, null, pl::isCancelled);
        }));
        tasks.add(new Operation.Task("Загрузка ресурсов (звук, текстуры, шрифты)", 14, () -> {
            VersionJson v = load(version);
            Path index = Paths.assets().resolve("indexes").resolve(v.assets + ".json");
            if (!Files.exists(index)) {
                pl.progress(1, 1, "Ресурсы пропущены");
                return;
            }
            Map<String, Object> root = Json.parseObject(Utils.readString(index));
            Map<String, Object> objects = Json.obj(root, "objects");
            if (objects == null || objects.isEmpty()) throw new IOException("Индекс ресурсов пуст");
            int total = objects.size();
            int toDownload = 0;
            for (Object o : objects.values()) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mm;
                String hash = Json.str(m, "hash", "");
                long size = (long) Json.num(m, "size", 0);
                if (hash.length() < 5) continue;
                Path target = Paths.assets().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                if (Files.exists(target) && Files.size(target) == size) continue;
                toDownload++;
            }
            pl.progress(0, Math.max(1, toDownload), "Ресурсов к загрузке: " + toDownload + " из " + total);
            List<Downloads.Item> batch = new ArrayList<>();
            for (Object o : objects.values()) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mm;
                String hash = Json.str(m, "hash", "");
                long size = (long) Json.num(m, "size", 0);
                if (hash.length() < 5) continue;
                Path target = Paths.assets().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                if (Files.exists(target) && Files.size(target) == size) continue;
                batch.add(new Downloads.Item(
                        "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                        target, hash, size));
            }
            int failed = Downloads.fetch(batch, pl, "Ресурсы");
            assetCache.clear();
            if (failed > 0) {
                throw new IOException("Не удалось загрузить ресурсы Minecraft: " + failed
                        + ". Без них нет текстур, звуков и языков.");
            }
            if (missingAssets(version) > 0) {
                throw new IOException("Ресурсы скачаны не полностью: нет части текстур, звуков или языков.");
            }
        }));
        tasks.add(new Operation.Task("Проверка установленных файлов", 1, () -> {
            List<String> problems = verify(version);
            if (!problems.isEmpty()) {
                throw new IOException("Проверка не пройдена: " + String.join("; ", problems));
            }
            pl.progress(1, 1, "Файлы проверены");
        }));
        return tasks;
    }

    /**
     * Локальный profile.json выбранной версии отличается от того, что сейчас отдаёт Mojang.
     * Сети нет — считается, что отличий не видно.
     */
    public boolean remoteJsonDiffers(String version) {
        Path local = Paths.versionJsonPath(version);
        if (!Files.isRegularFile(local)) return false;
        try {
            String body = Http.get(MANIFEST_URL, "mojang-manifest").body();
            Map<String, Object> root = Json.parseObject(body);
            List<Object> versions = Json.arr(root, "versions");
            if (versions == null) return false;
            for (Object o : versions) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mm;
                if (!version.equals(Json.str(m, "id", ""))) continue;
                String sha1 = Json.str(m, "sha1", "");
                if (sha1 == null || sha1.isBlank()) return false;
                return !Utils.sha1(local).equalsIgnoreCase(sha1);
            }
            return false;
        } catch (Exception e) {
            Log.warn("Список Mojang не сверен: " + Log.reason(e));
            return false;
        }
    }

    private static boolean sameFile(Path target, long size, String sha1) {
        try {
            if (!Files.isRegularFile(target) || Files.size(target) <= 0) return false;
            if (size > 0 && Files.size(target) != size) return false;
            if (sha1 != null && !sha1.isBlank() && !Utils.sha1(target).equalsIgnoreCase(sha1)) return false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Проверка целостности установленной версии. */
    public List<String> verify(String version) {
        List<String> problems = new ArrayList<>();
        try {
            Path json = Paths.versionJsonPath(version);
            if (!Files.exists(json)) {
                problems.add("нет profile.json");
                return problems;
            }
            VersionJson v = resolved(version);
            Path client = clientJar(version);
            if (!Files.exists(client)) {
                problems.add("нет client.jar");
            } else if (v.clientSize > 0 && Files.size(client) != v.clientSize) {
                problems.add("размер client.jar не совпадает");
            } else if (!Utils.isBlank(v.clientSha1) && !Utils.sha1(client).equalsIgnoreCase(v.clientSha1)) {
                problems.add("контрольная сумма client.jar не совпадает");
            }
            if (Utils.isBlank(v.mainClass)) problems.add("не указан mainClass");
            int missing = 0;
            for (VersionJson.Lib lib : v.libraries) {
                if (!lib.download || lib.path == null) continue;
                if (!Files.exists(Paths.libs().resolve(lib.path))) missing++;
            }
            if (missing > 0) problems.add("не хватает библиотек: " + missing);
            Path index = Paths.assets().resolve("indexes").resolve(v.assets + ".json");
            if (!Files.exists(index)) problems.add("нет индекса ресурсов " + v.assets);
            else {
                int missingAssets = missingAssets(version);
                if (missingAssets > 0) {
                    problems.add("не хватает ресурсов: " + missingAssets + " (текстуры, звуки, языки)");
                }
            }
        } catch (Exception e) {
            problems.add("ошибка проверки: " + Log.reason(e));
        }
        return problems;
    }

    private final Map<String, Integer> assetCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Сколько файлов ресурсов ещё не лежит на диске. 0 — текстуры, звуки и языки на месте. */
    public int missingAssets(String version) {
        try {
            VersionJson v = resolved(version);
            Path index = Paths.assets().resolve("indexes").resolve(v.assets + ".json");
            if (!Files.isRegularFile(index)) return 1;
            String key = index.toAbsolutePath() + ":" + Files.size(index);
            Integer cached = assetCache.get(key);
            if (cached != null) return cached;
            Map<String, Object> root = Json.parseObject(Utils.readString(index));
            Map<String, Object> objects = Json.obj(root, "objects");
            if (objects == null || objects.isEmpty()) return 1;
            int missing = 0;
            for (Object o : objects.values()) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) mm;
                String hash = Json.str(m, "hash", "");
                long size = (long) Json.num(m, "size", 0);
                if (hash.length() < 5) continue;
                Path target = Paths.assets().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                if (!Files.isRegularFile(target) || (size > 0 && Files.size(target) != size)) missing++;
            }
            assetCache.put(key, missing);
            return missing;
        } catch (Exception e) {
            return 1;
        }
    }

    /** Версии для селектора. Уже скачанные показываются и без интернета. */
    public List<Map<String, Object>> selectableVersions() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (String id : Paths.localGameIds()) {
            if (!isVanillaProfile(id)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("type", "release");
            m.put("installed", true);
            m.put("label", "Minecraft " + id);
            byId.put(id, m);
        }
        try {
            for (VersionEntry e : releases()) {
                Map<String, Object> m = byId.get(e.id());
                boolean installed = Paths.localGamePresent(e.id()) || (m != null && Boolean.TRUE.equals(m.get("installed")));
                if (m == null) {
                    m = new LinkedHashMap<>();
                    m.put("id", e.id());
                    m.put("type", e.type());
                    m.put("label", "Minecraft " + e.id());
                    byId.put(e.id(), m);
                }
                m.put("installed", installed);
            }
        } catch (Exception e) {
            Log.warn("Список версий Mojang недоступен, показаны скачанные: " + Log.reason(e));
        }
        List<Map<String, Object>> list = new ArrayList<>(byId.values());
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if ("1.20.1".equals(String.valueOf(list.get(i).get("id")))) {
                index = i;
                break;
            }
        }
        if (index > 0) {
            list.add(0, list.remove(index));
        } else if (index < 0) {
            Map<String, Object> common = new LinkedHashMap<>();
            common.put("id", "1.20.1");
            common.put("type", "release");
            common.put("installed", Paths.localGamePresent("1.20.1"));
            common.put("label", "Minecraft 1.20.1");
            list.add(0, common);
        }
        return list;
    }

    public Map<String, Object> describe(String version) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            m.putAll(resolved(version).describeMap());
        } catch (Exception e) {
            m.put("error", Log.reason(e));
        }
        return m;
    }
}
