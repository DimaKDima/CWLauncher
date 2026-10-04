package ru.cw.launcher.fabric;

import ru.cw.launcher.core.Operation;
import ru.cw.launcher.model.ProgressListener;
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
 * FabricManager: определение требуемой версии Fabric, проверка наличия, установка,
 * привязка к выбранной версии Minecraft.
 */
public final class FabricManager {

    public static final String META = "https://meta.fabricmc.net/v2/versions";

    /** Профиль игры называется так же, как версия (1.20.1); профиль Fabric — "fabric-loader-X-MC". */
    public static String profileId(String loaderVersion, String mcVersion) {
        return "fabric-loader-" + loaderVersion + "-" + mcVersion;
    }

    public List<String> loaders(String mcVersion) throws IOException, InterruptedException {
        String body = Http.get(META + "/loader/" + mcVersion, "fabric-loaders-" + mcVersion).body();
        List<String> out = new ArrayList<>();
        for (Object o : Json.parseArray(body)) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> loader = Json.obj((Map<String, Object>) m, "loader");
            if (loader == null) continue;
            String v = Json.str(loader, "version", "");
            if (!v.isBlank()) out.add(v);
        }
        return out;
    }

    public String latestLoader(String mcVersion) {
        try {
            List<String> list = loaders(mcVersion);
            if (list.isEmpty()) return null;
            return list.get(0);
        } catch (Exception e) {
            Log.warn("Fabric meta недоступен: " + Log.reason(e));
            return null;
        }
    }

    /** Требуемая версия Fabric: из настроек (конфигурируемая) или последняя стабильная. */
    public String requiredLoader(String mcVersion, String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        return latestLoader(mcVersion);
    }

    public boolean isInstalled(String loaderVersion, String mcVersion) {
        return Files.exists(Paths.versionJsonPath(profileId(loaderVersion, mcVersion)));
    }

    /** Установленная версия Fabric для версии Minecraft (по наличию профиля). */
    public String installedLoader(String mcVersion) {
        for (String id : new MinecraftVersionScanner().localProfiles()) {
            if (id.startsWith("fabric-loader-") && id.endsWith("-" + mcVersion)) {
                return id.substring("fabric-loader-".length(), id.length() - ("-".length() + mcVersion.length()));
            }
        }
        return null;
    }

    public List<Operation.Task> installTasks(String mcVersion, String loaderVersion, ProgressListener pl) {
        List<Operation.Task> tasks = new ArrayList<>();
        tasks.add(new Operation.Task("Получение профиля Fabric " + loaderVersion, 1, () -> {
            String url = META + "/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
            String body = Http.get(url, "fabric-profile-" + mcVersion + "-" + loaderVersion).body();
            Map<String, Object> profile = Json.parseObject(body);
            if (Json.str(profile, "mainClass", "").isBlank()) {
                throw new IOException("Fabric вернул неполный профиль для " + mcVersion);
            }
            String id = profileId(loaderVersion, mcVersion);
            Utils.writeString(Paths.versionJsonPath(id), Json.write(profile));
            pl.progress(1, 1, "Профиль Fabric записан");
        }));
        tasks.add(new Operation.Task("Загрузка библиотек Fabric", 4, () -> {
            String id = profileId(loaderVersion, mcVersion);
            Map<String, Object> profile = Json.parseObject(Utils.readString(Paths.versionJsonPath(id)));
            List<Object> libs = Json.arr(profile, "libraries");
            if (libs == null) throw new IOException("В профиле Fabric нет библиотек");
            int i = 0;
            int failed = 0;
            for (Object o : libs) {
                if (!(o instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> lib = (Map<String, Object>) mm;
                pl.checkCancel();
                String name = Json.str(lib, "name", "");
                String mavenUrl = Json.str(lib, "url", "https://maven.fabricmc.net/");
                String path = mavenPath(name);
                if (path == null) continue;
                Path target = Paths.libs().resolve(path);
                if (Files.exists(target) && Files.size(target) > 0) {
                    i++;
                    continue;
                }
                Utils.ensureDirectories(target.getParent());
                String url = trim(mavenUrl) + "/" + path;
                try {
                    Http.download(url, target, null, 0, null, null);
                } catch (IOException e) {
                    failed++;
                    Log.warn("Библиотека Fabric " + name + " не скачана: " + Log.reason(e));
                }
                i++;
                pl.progress(i, libs.size(), "Fabric: " + Utils.shorten(name, 40));
            }
            if (failed > 0 && failed == i) {
                throw new IOException("Не удалось скачать библиотеки Fabric. Проверьте интернет.");
            }
        }));
        return tasks;
    }

    private static String trim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String mavenPath(String coords) {
        if (coords == null) return null;
        String[] p = coords.split(":");
        if (p.length < 3) return null;
        String classifier = p.length > 3 ? "-" + p[3] : "";
        return p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + classifier + ".jar";
    }

    /** Краткое состояние для центра состояния. */
    public Map<String, Object> status(String mcVersion, String requiredLoader) {
        Map<String, Object> m = new LinkedHashMap<>();
        String installed = installedLoader(mcVersion);
        m.put("minecraft", mcVersion);
        m.put("required", requiredLoader);
        m.put("installed", installed);
        m.put("ok", installed != null && requiredLoader != null && installed.equals(requiredLoader));
        return m;
    }

    /** Вспомогательный сканер локальных профилей (без циклической зависимости от MinecraftManager). */
    public static final class MinecraftVersionScanner {
        public List<String> localProfiles() {
            List<String> out = new ArrayList<>();
            Path dir = Paths.versions();
            if (!Files.isDirectory(dir)) return out;
            try (var s = Files.list(dir)) {
                for (Path p : s.filter(Files::isDirectory).toList()) {
                    if (Files.exists(p.resolve(p.getFileName() + ".json"))) out.add(p.getFileName().toString());
                }
            } catch (IOException e) {
                Log.warn("Список профилей недоступен: " + Log.reason(e));
            }
            return out;
        }
    }
}
