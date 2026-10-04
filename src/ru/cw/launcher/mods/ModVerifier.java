package ru.cw.launcher.mods;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * ModVerifier: реальная автопроверка модов перед запуском.
 *  - отсутствующие обязательные моды (по build-manifest.json установленной сборки);
 *  - лишние моды, если сборка это предусматривает;
 *  - повреждённые jar (нечитаемый архив / нет fabric.mod.json);
 *  - несовпадение версий модов с манифестом;
 *  - наличие конфигурационных файлов;
 *  - соответствие версии сборки.
 */
public final class ModVerifier {

    public record ModInfo(String file, String id, String version, boolean readable, long size) {
    }

    public record Report(List<ModInfo> mods, List<String> missing, List<String> mismatched,
                         List<String> corrupted, List<String> extra, boolean configsPresent,
                         String buildVersion, String expectedVersion, long freeSpaceMb) {

        public List<String> problems() {
            List<String> p = new ArrayList<>();
            if (!missing.isEmpty()) {
                p.add("Отсутствуют моды сборки: " + Utils.shorten(String.join(", ", missing), 160));
            }
            if (!corrupted.isEmpty()) {
                p.add("Повреждённые файлы: " + Utils.shorten(String.join(", ", corrupted), 160));
            }
            if (!mismatched.isEmpty()) {
                p.add("Несовпадение версий: " + Utils.shorten(String.join(", ", mismatched), 160));
            }
            if (expectedVersion != null && buildVersion != null
                    && !Utils.normalizeVersion(expectedVersion).equals(Utils.normalizeVersion(buildVersion))) {
                p.add("Версия сборки " + buildVersion + " не совпадает с актуальной " + expectedVersion);
            }
            if (freeSpaceMb >= 0 && freeSpaceMb < 1024) {
                p.add("Мало свободного места: " + freeSpaceMb + " МБ");
            }
            return p;
        }

        public boolean ok() {
            return problems().isEmpty();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mods", mods.size());
            m.put("missing", missing);
            m.put("corrupted", corrupted);
            m.put("mismatched", mismatched);
            m.put("extra", extra);
            m.put("configsPresent", configsPresent);
            m.put("buildVersion", buildVersion);
            m.put("expectedVersion", expectedVersion);
            m.put("freeSpaceMb", freeSpaceMb);
            return m;
        }
    }

    /** Пишет/читает манифест установленной сборки (список файлов и версии модов). */
    public static Map<String, Object> readManifest(String instanceId) {
        Path file = Paths.buildManifest(instanceId);
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            return Json.parseObject(Utils.readString(file));
        } catch (IOException e) {
            Log.warn("build-manifest.json повреждён: " + Log.reason(e));
            return new LinkedHashMap<>();
        }
    }

    public static void writeManifest(String instanceId, Path modsDir) {
        try {
            Map<String, Object> files = new LinkedHashMap<>();
            for (Path p : ModManager.listFiles(modsDir)) {
                String n = p.getFileName().toString();
                if (!n.toLowerCase().endsWith(".jar")) continue;
                ModInfo info = inspect(p);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("size", Utils.size(p));
                entry.put("id", info == null ? "" : info.id);
                entry.put("version", info == null ? "" : info.version);
                files.put(n, entry);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", System.currentTimeMillis());
            root.put("count", files.size());
            root.put("files", files);
            Utils.writeString(Paths.buildManifest(instanceId), Json.write(root));
            Log.info("Манифест сборки сохранён: " + files.size() + " модов");
        } catch (IOException e) {
            Log.error("Не удалось создать манифест сборки: " + Log.reason(e), e);
        }
    }

    /** Чтение fabric.mod.json из JAR — реальные id/version. */
    @SuppressWarnings("unchecked")
    public static ModInfo inspect(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                return new ModInfo(jar.getFileName().toString(), "", "", false, Utils.size(jar));
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Map<String, Object> m = Json.parseObject(new String(in.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8));
                String id = Json.str(m, "id", "");
                String version = Json.str(m, "version", "");
                if (id.isBlank()) {
                    Object mods = m.get("mods");
                    if (mods instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> mm) {
                        id = Json.str((Map<String, Object>) mm, "id", "");
                        if (version.isBlank()) version = Json.str((Map<String, Object>) mm, "version", "");
                    }
                }
                return new ModInfo(jar.getFileName().toString(), id, version, true, Utils.size(jar));
            }
        } catch (Exception e) {
            return new ModInfo(jar.getFileName().toString(), "", "", false, Utils.size(jar));
        }
    }

    public Report verify(Settings cfg) {
        String instanceId = cfg.instanceId();
        Path mods = Paths.mods(instanceId);
        List<ModInfo> infos = new ArrayList<>();
        List<String> corrupted = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            for (Path p : ModManager.listFiles(mods)) {
                String n = p.getFileName().toString();
                if (n.toLowerCase().endsWith(".jar.disabled") || n.startsWith(".")) continue;
                if (!n.toLowerCase().endsWith(".jar")) continue;
                ModInfo info = inspect(p);
                infos.add(info);
                names.add(n);
                if (!info.readable() || info.size() <= 0) corrupted.add(n);
            }
        } catch (IOException e) {
            Log.warn("Каталог модов недоступен: " + Log.reason(e));
        }
        Map<String, Object> manifest = readManifest(instanceId);
        Map<String, Object> files = Json.obj(manifest, "files");
        List<String> missing = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (String expected : files.keySet()) {
                if (!names.contains(expected)) {
                    missing.add(expected);
                } else {
                    Map<String, Object> e = Json.obj(files, expected);
                    String wantVer = e == null ? "" : Json.str(e, "version", "");
                    ModInfo got = infos.stream().filter(i -> i.file().equals(expected)).findFirst()
                            .orElse(null);
                    if (got != null && !wantVer.isBlank() && !wantVer.equals(got.version())) {
                        mismatched.add(expected + " (ждём " + wantVer + ", имеем " + got.version() + ")");
                    }
                }
            }
            if (Boolean.getBoolean("cw.strict.extra.mods")) {
                for (String n : names) if (!files.containsKey(n)) extra.add(n);
            }
        }
        boolean configs = Files.isDirectory(Paths.configs(instanceId))
                && hasChildren(Paths.configs(instanceId));
        String buildVersion = cfg.buildVersion;
        ModManager.BuildState st = new ModManager(null).readState(instanceId);
        if (st != null) buildVersion = st.installedVersion();
        return new Report(infos, missing, mismatched, corrupted, extra, configs, buildVersion,
                cfg.remoteBuildVersion, cfg.checkDiskSpace ? Paths.freeSpaceMb(Paths.root()) : -1);
    }

    private static boolean hasChildren(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (var s = Files.list(dir)) {
            return s.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }
}
