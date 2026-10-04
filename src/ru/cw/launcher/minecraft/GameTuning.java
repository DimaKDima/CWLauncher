package ru.cw.launcher.minecraft;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Применяет игровые флаги Windows и разрешение из настроек к уже запущенному процессу. */
public final class GameTuning {

    private static final String LAYERS =
            "HKCU\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers";
    private static final String GPU =
            "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";

    private GameTuning() {
    }

    public static void tune(Settings cfg, Path javaExe, Process process) {
        if (cfg == null || process == null) return;
        if (cfg.highPriority || cfg.internetLeader) priority(process.pid(), "High", true);
        if (javaExe == null) return;
        String path = javaExe.toAbsolutePath().toString();
        if (cfg.disableFullscreenOpt) {
            reg("add", LAYERS, path, "~ DISABLEDXMAXIMIZEDWINDOWEDMODE");
        } else {
            regDelete(LAYERS, path);
        }
        if (cfg.disableRealtimeOpt) {
            reg("add", GPU, path, "GpuPreference=2;");
        } else {
            regDelete(GPU, path);
        }
    }

    public static void priority(long pid, String level, boolean wait) {
        if (pid <= 0 || !allowedLevel(level)) return;
        Runnable job = () -> applyPriority(pid, level);
        if (wait) {
            job.run();
            return;
        }
        Thread t = new Thread(job, "cw-priority");
        t.setDaemon(true);
        t.start();
    }

    private static boolean allowedLevel(String level) {
        return "Idle".equals(level) || "BelowNormal".equals(level) || "Normal".equals(level)
                || "AboveNormal".equals(level) || "High".equals(level);
    }

    private static void applyPriority(long pid, String level) {
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-Command",
                    "$p = Get-Process -Id " + pid + " -ErrorAction Stop; "
                            + "$p.PriorityClass = '" + level + "'; Write-Output $p.PriorityClass")
                    .redirectErrorStream(true)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                Log.warn("Приоритет процесса " + pid + " не успел примениться");
                return;
            }
            String text = new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (p.exitValue() == 0 && text.toLowerCase(java.util.Locale.ROOT).contains(level.toLowerCase(java.util.Locale.ROOT))) {
                Log.info("Приоритет процесса " + pid + ": " + text);
            } else {
                Log.warn("Приоритет процесса " + pid + " не изменён: " + text);
            }
        } catch (Exception e) {
            Log.warn("Приоритет процесса не изменён: " + Log.reason(e));
        }
    }

    /** Разрешение, полный экран, VSync и лимит FPS из настроек — в options.txt этой версии. */
    public static void applyDisplay(Settings cfg, Path options) {
        if (cfg == null || options == null) return;
        Map<String, String> values = new LinkedHashMap<>();
        try {
            List<String> lines = Files.exists(options)
                    ? new ArrayList<>(Files.readAllLines(options)) : new ArrayList<>();
            for (String line : lines) {
                int i = line.indexOf(':');
                if (i > 0) values.put(line.substring(0, i), line.substring(i + 1));
            }
            values.put("fullscreen", String.valueOf(cfg.screenFullscreen));
            values.put("enableVsync", String.valueOf(cfg.vsync));
            int fps = cfg.framerateLimit <= 0 ? 260 : Math.min(260, Math.max(10, cfg.framerateLimit));
            values.put("maxFps", String.valueOf(fps));
            if (cfg.screenWidth >= 854 && cfg.screenHeight >= 480) {
                values.put("overrideWidth", String.valueOf(cfg.screenWidth));
                values.put("overrideHeight", String.valueOf(cfg.screenHeight));
            }
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, String> e : values.entrySet()) out.add(e.getKey() + ":" + e.getValue());
            Files.createDirectories(options.getParent());
            Files.writeString(options, String.join("\n", out) + "\n");
            Log.info("Экран Minecraft: " + cfg.screenWidth + "x" + cfg.screenHeight
                    + (cfg.screenFullscreen ? " полный" : "") + ", FPS " + fps);
        } catch (Exception e) {
            Log.warn("Не удалось записать настройки экрана: " + Log.reason(e));
        }
    }

    private static void reg(String action, String key, String name, String data) {
        try {
            new ProcessBuilder("reg", action, key, "/v", name, "/t", "REG_SZ", "/d", data, "/f")
                    .redirectErrorStream(true).start();
        } catch (Exception e) {
            Log.warn("Параметр Windows не записан: " + Log.reason(e));
        }
    }

    private static void regDelete(String key, String name) {
        try {
            new ProcessBuilder("reg", "delete", key, "/v", name, "/f")
                    .redirectErrorStream(true).start();
        } catch (Exception ignored) {
        }
    }
}
