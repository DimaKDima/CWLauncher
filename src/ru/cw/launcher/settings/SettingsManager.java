package ru.cw.launcher.settings;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Загрузка/сохранение config.json (%APPDATA%\CWLauncher\config.json).
 * Повреждённый конфиг не роняет приложение: сохраняется backup, создаётся новый.
 */
public final class SettingsManager {

    private static volatile Settings current = new Settings();
    private static volatile boolean repaired;
    private static volatile String repairMessage = "";

    private SettingsManager() {
    }

    public static Settings get() {
        return current;
    }

    public static boolean wasRepaired() {
        return repaired;
    }

    public static String repairMessage() {
        return repairMessage;
    }

    public static synchronized void load() {
        Path file = Paths.config();
        try {
            if (!Files.exists(file)) {
                current = new Settings();
                Log.info("Создан новый config.json по умолчанию");
                save();
                return;
            }
            String raw = Files.readString(file);
            if (raw.isBlank()) throw new IOException("config.json пуст");
            Map<String, Object> map = Json.parseObject(raw);
            current = Settings.fromMap(map);
            Paths.Holder.gameDir = current.gameDir == null ? "" : current.gameDir;
            Log.info("config.json загружен (" + map.size() + " записей)");
        } catch (Exception e) {
            repaired = true;
            repairMessage = "Файл настроек был повреждён. Создана резервная копия, "
                    + "применены настройки по умолчанию.";
            Log.error("config.json повреждён: " + Log.reason(e), e);
            backup(file);
            current = new Settings();
            try {
                save();
            } catch (Exception ex) {
                Log.error("Не удалось пересоздать config.json: " + Log.reason(ex), ex);
            }
        }
        current.validate();
        applyRuntime(current);
    }

    private static void backup(Path file) {
        try {
            if (!Files.exists(file)) return;
            Path bak = file.resolveSibling("config.broken-" + System.currentTimeMillis() + ".json");
            Files.copy(file, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Log.info("Резервная копия повреждённого конфига: " + bak.getFileName());
        } catch (IOException e) {
            Log.warn("Не удалось сохранить резервную копию конфига: " + Log.reason(e));
        }
    }

    public static synchronized void save() {
        try {
            Path file = Paths.config();
            Utils.ensureDirectories(file.getParent());
            Path tmp = file.resolveSibling("config.json.tmp");
            Files.writeString(tmp, Json.write(current.toMap()));
            Utils.replaceFile(tmp, file);
            Paths.Holder.gameDir = current.gameDir == null ? "" : current.gameDir;
            applyRuntime(current);
        } catch (IOException e) {
            Log.error("Не удалось сохранить config.json: " + Log.reason(e), e);
        }
    }

    private static void applyRuntime(Settings cfg) {
        Log.setLevel(cfg.verboseLogging || cfg.developerMode ? Log.Level.DEBUG : Log.Level.INFO);
        ru.cw.launcher.ui.UiLook.apply(cfg);
        ru.cw.launcher.net.NetPolicy.apply(cfg);
    }

    /** Изменение настроек + сохранение. */
    public static synchronized void update(Consumer<Settings> mutator) {
        mutator.accept(current);
        current.validate();
        save();
    }

    /**
     * Максимум ползунка — вся оперативная память компьютера, округлённая до гигабайта вверх.
     * 16 ГБ → 16384, 32 ГБ → 32768, 64 ГБ → 65536.
     */
    public static int maxGameRamMb() {
        long phys = Utils.physicalMemoryMb();
        if (phys <= 0) return 8192;
        long gb = (phys + 1023) / 1024;
        long mb = gb * 1024L;
        if (mb > 1024L * 1024L) mb = 1024L * 1024L;
        return (int) mb;
    }

    /** Автоподбор: около половины памяти компьютера, но не выше потолка. */
    public static int defaultRamMb() {
        int cap = maxGameRamMb();
        long phys = Utils.physicalMemoryMb();
        int mb = phys > 0 ? (int) Math.min(phys / 2, cap) : Math.min(4096, cap);
        if (mb < 2048) mb = (int) Math.min(2048, Math.max(1024, cap));
        if (mb > cap) mb = cap;
        return mb;
    }

    /** Фактический -Xmx: явный выбор или автоподбор, но никогда выше потолка. */
    public static int gameRamMb(int configured) {
        int cap = maxGameRamMb();
        int ram = configured > 0 ? configured : defaultRamMb();
        if (ram > cap) ram = cap;
        if (ram < 1024) ram = Math.min(1024, cap);
        return ram;
    }

    public static long totalRamMb() {
        return Utils.physicalMemoryMb();
    }

    // ------------------------------------------------------------------ автозапуск Windows

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    public static boolean applyAutoStart(boolean enable) {
        try {
            if (!enable) {
                reg("reg", "delete", RUN_KEY, "/v", "CWLauncher", "/f");
                Log.info("Автозапуск отключён");
                return true;
            }
            String exe = executablePath();
            if (exe == null) {
                Log.warn("Путь к EXE не определён — автозапуск через реестр недоступен");
                return false;
            }
            reg("reg", "add", RUN_KEY, "/v", "CWLauncher", "/t", "REG_SZ", "/d", "\"" + exe + "\"", "/f");
            Log.info("Автозапуск включён: " + exe);
            return true;
        } catch (Exception e) {
            Log.error("Не удалось применить автозапуск: " + Log.reason(e), e);
            return false;
        }
    }

    public static boolean isAutoStartEnabled() {
        try {
            String out = regCapture("reg", "query", RUN_KEY, "/v", "CWLauncher");
            return out != null && out.contains("CWLauncher");
        } catch (Exception e) {
            return false;
        }
    }

    /** Путь к CWLauncher.exe для автозапуска. null, если это запуск из IDE без EXE. */
    public static String executablePath() {
        String prop = System.getProperty("cwlauncher.exe");
        if (prop != null && !prop.isBlank() && Files.isRegularFile(Path.of(prop))) return prop;
        Path exe = Paths.launcherExecutable();
        return exe == null ? null : exe.toString();
    }

    private static void reg(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("Команда не завершилась: " + cmd[0]);
        }
    }

    private static String regCapture(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        return out;
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("config", Paths.config().toString());
        m.put("repaired", repaired);
        m.put("minecraftVersion", current.minecraftVersion);
        m.put("loader", current.loader);
        m.put("theme", current.theme);
        m.put("ramMb", current.ramMb);
        m.put("autoUpdateBuild", current.autoUpdateBuild);
        m.put("autoUpdateLauncher", current.autoUpdateLauncher);
        m.put("internetLeader", current.internetLeader);
        return m;
    }
}
