package ru.cw.launcher.settings;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Paths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Когда лаунчер сам проверяет обновление: при запуске, раз в сутки или только вручную. */
public final class UpdateSchedule {

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private UpdateSchedule() {
    }

    public static boolean launcherHandoff(Settings cfg, boolean startup) {
        if (cfg == null || !cfg.autoUpdateLauncher) return false;
        String every = cfg.launcherUpdateEvery == null ? "day" : cfg.launcherUpdateEvery;
        if ("manual".equals(every)) return false;
        if ("day".equals(every)) return due("launcher");
        return startup;
    }

    /** Кнопка «Обновить моды» только когда автообновление сборки включено и проверка не ручная. */
    public static boolean offerMods(Settings cfg) {
        if (cfg == null || !cfg.autoUpdateBuild) return false;
        return !"manual".equals(cfg.modsUpdateEvery);
    }

    public static boolean refetch(Settings cfg, boolean mods) {
        if (cfg == null) return false;
        if (mods) {
            if (!cfg.autoUpdateBuild) return false;
            String every = cfg.modsUpdateEvery == null ? "day" : cfg.modsUpdateEvery;
            if ("manual".equals(every)) return false;
            if ("day".equals(every)) return due("mods");
            return true;
        }
        if (!cfg.autoUpdateLauncher) return false;
        String every = cfg.launcherUpdateEvery == null ? "day" : cfg.launcherUpdateEvery;
        if ("manual".equals(every)) return false;
        if ("day".equals(every)) return due("launcher");
        return true;
    }

    public static boolean due(String key) {
        long last = stored(key);
        return last <= 0 || System.currentTimeMillis() - last >= DAY;
    }

    public static void mark(String key) {
        try {
            Path file = file();
            Map<String, Object> map = read();
            map.put(key, System.currentTimeMillis());
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(map));
        } catch (Exception ignored) {
        }
    }

    private static long stored(String key) {
        Object v = read().get(key);
        if (v instanceof Number n) return n.longValue();
        return 0;
    }

    private static Map<String, Object> read() {
        try {
            Path file = file();
            if (!Files.isRegularFile(file)) return new LinkedHashMap<>();
            Map<String, Object> map = Json.parseObject(Files.readString(file));
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static Path file() {
        return Paths.config().getParent().resolve("update-schedule.json");
    }
}
