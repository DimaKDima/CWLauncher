package ru.cw.launcher.diagnostics;

import ru.cw.launcher.accounts.AccountManager;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.fabric.FabricManager;
import ru.cw.launcher.minecraft.JavaManager;
import ru.cw.launcher.minecraft.MinecraftManager;
import ru.cw.launcher.mods.ModManager;
import ru.cw.launcher.mods.ModVerifier;
import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.network.ServerStatus;
import ru.cw.launcher.security.Secrets;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Диагностика сборки и окружения (доп. функция 13). Каждая проверка даёт реальное
 * значение и понятное описание проблемы, а где возможно — способ исправления.
 */
public final class DiagnosticsManager {

    public enum Level {
        OK("В порядке"), INFO("Информация"), WARN("Предупреждение"), ERROR("Ошибка");
        public final String label;

        Level(String label) {
            this.label = label;
        }
    }

    public record Check(String code, String title, Level level, String value, String hint,
                        String fixAction) {
        public boolean fixable() {
            return fixAction != null && !fixAction.isBlank();
        }
    }

    private final List<Check> checks = new ArrayList<>();

    public List<Check> checks() {
        return List.copyOf(checks);
    }

    public List<Check> run(Settings cfg, MinecraftManager mc, FabricManager fabric, JavaManager java,
                           ModManager mods, AccountManager accounts, ProgressListener pl) {
        checks.clear();
        pl.stage("Диагностика окружения");
        checkSystem();
        checkJava(cfg, java, mc);
        pl.progress(1, 6, "Проверка Minecraft");
        checkMinecraft(cfg, mc);
        pl.progress(2, 6, "Проверка Fabric");
        checkFabric(cfg, mc, fabric);
        pl.progress(3, 6, "Проверка модов");
        checkMods(cfg, mods);
        pl.progress(4, 6, "Проверка хранилища и доступа");
        checkStorage();
        pl.progress(5, 6, "Проверка источников обновлений");
        checkSources(cfg);
        pl.progress(6, 6, "Проверка сервера");
        checkServer(cfg);
        checkAccounts(accounts);
        return checks();
    }

    private void add(String code, String title, Level level, String value, String hint, String fix) {
        checks.add(new Check(code, title, level, value, hint, fix));
        Log.info("[diagnostic] " + level + " " + title + ": " + value);
    }

    // ------------------------------------------------------------------ проверки

    private void checkSystem() {
        add("os", "Операционная система", Level.INFO, Utils.osCaption(), null, null);
        add("java-runtime", "Java лаунчера", Level.INFO,
                System.getProperty("java.version", "?") + " (" + System.getProperty("java.vendor", "?") + ")",
                null, null);
        long total = Utils.physicalMemoryMb();
        Level lvl = total <= 0 ? Level.INFO : (total < 4096 ? Level.WARN : Level.OK);
        add("ram", "Оперативная память", lvl, total > 0 ? (total / 1024) + " ГБ" : "не определено",
                total > 0 && total < 4096 ? "Менее 4 ГБ — Minecraft будет работать медленно." : null, null);
        long free = Paths.freeSpaceMb(Paths.root());
        add("disk", "Свободное место", free < 2048 ? Level.WARN : Level.OK,
                Utils.humanSize(free * 1024 * 1024),
                free < 2048 ? "Нужно не менее 2 ГБ для сборки и кэша." : null, "Очистить кэш");
    }

    private void checkJava(Settings cfg, JavaManager java, MinecraftManager mc) {
        JavaManager.Runtime r = java.selected();
        if (r == null) r = java.select(cfg.minecraftVersion, cfg.javaPath);
        int need = JavaManager.requiredMajor(cfg.minecraftVersion);
        if (r == null) {
            add("java", "Java для игры", Level.ERROR, "не найдена Java " + need,
                    "Minecraft " + cfg.minecraftVersion + " требует Java " + need + ".",
                    "Установить Java " + need);
        } else if (r.major() < need) {
            add("java", "Java для игры", Level.ERROR, "Java " + r.major() + " (нужна " + need + ")",
                    "Версия Java ниже требуемой — игра не запустится.", "Установить Java " + need);
        } else {
            add("java", "Java для игры", Level.OK, java.describe(), null, null);
        }
    }

    private void checkMinecraft(Settings cfg, MinecraftManager mc) {
        String v = cfg.minecraftVersion;
        List<String> problems = mc.verify(v);
        if (problems.isEmpty() && mc.versionReady(v)) {
            add("minecraft", "Minecraft " + v, Level.OK, "установлен и проверен", null, null);
        } else if (!mc.localProfiles().contains(v)) {
            add("minecraft", "Minecraft " + v, Level.ERROR, "не установлен",
                    "Версия отсутствует локально.", "Скачать Minecraft " + v);
        } else {
            add("minecraft", "Minecraft " + v, Level.ERROR, String.join("; ", problems),
                    "Файлы версии повреждены или не до конца загружены.", "Переустановить версию");
        }
    }

    private void checkFabric(Settings cfg, MinecraftManager mc, FabricManager fabric) {
        String required = fabric.requiredLoader(cfg.minecraftVersion, cfg.fabricLoaderVersion);
        if (required == null) {
            add("fabric", "Fabric", Level.WARN, "версия не определена (нет доступа к meta.fabricmc.net)",
                    "Проверьте интернет — версия Fabric берётся с официального сайта Fabric.", null);
            return;
        }
        String installed = fabric.installedLoader(cfg.minecraftVersion);
        String profileId = FabricManager.profileId(required, cfg.minecraftVersion);
        if (installed == null) {
            add("fabric", "Fabric " + required, Level.ERROR, "не установлен",
                    "Нужен профиль " + profileId + ".", "Установить Fabric " + required);
        } else if (!installed.equals(required)) {
            add("fabric", "Fabric", Level.WARN, "установлен " + installed + ", требуется " + required,
                    "Обновите профиль Fabric.", "Установить Fabric " + required);
        } else {
            List<String> problems = mc.verify(profileId);
            if (problems.isEmpty()) {
                add("fabric", "Fabric " + required, Level.OK, "установлен", null, null);
            } else {
                add("fabric", "Fabric " + required, Level.ERROR, String.join("; ", problems),
                        "Профиль Fabric повреждён.", "Переустановить Fabric");
            }
        }
    }

    private void checkMods(Settings cfg, ModManager mods) {
        String instanceId = cfg.instanceId();
        ModVerifier.Report rep = new ModVerifier().verify(cfg);
        int count = mods.modCount(instanceId);
        String installed = mods.installedBuildVersion(instanceId);
        if (count == 0) {
            add("mods", "Моды Common World", Level.ERROR, "не установлены",
                    "Сборка модов отсутствует.", "Установить сборку");
            return;
        }
        List<String> problems = rep.problems();
        if (problems.isEmpty()) {
            add("mods", "Моды Common World", Level.OK, count + " модов, версия сборки "
                    + (installed == null ? "?" : installed), null, null);
        } else {
            add("mods", "Моды Common World", Level.ERROR, Utils.shorten(String.join("; ", problems), 200),
                    count + " файлов в папке модов.", "Исправить автоматически");
        }
        if (rep.configsPresent()) {
            add("modconfigs", "Конфигурация модов", Level.INFO, "каталог config присутствует", null, null);
        } else {
            add("modconfigs", "Конфигурация модов", Level.INFO,
                    "каталог config ещё не создан (появится после первого запуска)", null, null);
        }
    }

    private void checkStorage() {
        try {
            Path probe = Paths.tmp().resolve("probe-" + System.nanoTime() + ".tmp");
            Utils.ensureDirectories(probe.getParent());
            Utils.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            add("write", "Доступ на запись", Level.OK, Paths.root().toString(), null, null);
        } catch (Exception e) {
            add("write", "Доступ на запись", Level.ERROR, "запись невозможна",
                    "Проверьте права на " + Paths.root() + ": " + Log.reason(e), null);
        }
    }

    private void checkSources(Settings cfg) {
        checkUrl("src-cw", "cwVersion.txt", cfg.cwVersionUrl);
        checkUrl("src-l", "lVersion.txt", cfg.lVersionUrl);
        checkUrl("src-mods", "ZIP сборки модов", cfg.modpackUrl);
        checkUrl("src-mc", "piston-meta (Mojang)", MinecraftManager.MANIFEST_URL);
        checkUrl("src-fabric", "meta.fabricmc.net", FabricManager.META + "/loader/" + cfg.minecraftVersion);
    }

    private void checkUrl(String code, String title, String url) {
        boolean ok = false;
        String err = "";
        try {
            var r = ru.cw.launcher.net.Http.get(url, null);
            ok = r.body() != null && !r.body().isBlank();
            if (!ok) err = "пустой ответ";
        } catch (Exception e) {
            err = Log.reason(e);
        }
        add(code, title, ok ? Level.OK : Level.WARN, ok ? "доступен" : "недоступен: " + err,
                ok ? null : "Источник: " + Utils.shorten(url, 90), null);
    }

    private void checkServer(Settings cfg) {
        ServerStatus.Info info = ServerStatus.check(cfg.serverIp, 5000);
        if (!info.available()) {
            add("server", "Сервер Common World", Level.WARN, "Статус сервера недоступен",
                    info.error(), null);
        } else if (!info.online()) {
            add("server", "Сервер Common World", Level.ERROR, "сервер не отвечает",
                    info.error(), null);
        } else {
            add("server", "Сервер Common World", Level.OK,
                    "онлайн " + info.players() + "/" + info.maxPlayers() + ", " + info.pingMs() + " мс"
                            + (info.version() == null || info.version().isBlank()
                            ? "" : ", версия " + info.version()), null, null);
        }
    }

    private void checkAccounts(AccountManager accounts) {
        int n = accounts.count();
        var active = accounts.active();
        add("accounts", "Аккаунты", active == null ? Level.WARN : Level.INFO,
                n + " акк.", active == null ? "Аккаунт не выбран" : "Выбран: " + active.display(),
                active == null ? "Добавить аккаунт" : null);
        if (active != null && active.type == ru.cw.launcher.accounts.Account.Type.ELY) {
            String token = accounts.tokenOf(active);
            add("account-token", "Токен Ely.by", token.isBlank() ? Level.ERROR : Level.OK,
                    token.isBlank() ? "токен не сохранён" : "сохранён в защищённом хранилище",
                    token.isBlank() ? "Войдите заново" : null,
                    token.isBlank() ? "Войти через Ely.by" : null);
        }
        add("secrets", "Защита секретов", Secrets.isDpapiAvailable() ? Level.OK : Level.WARN,
                Secrets.isDpapiAvailable() ? "DPAPI доступен" : "токены хранятся в открытом виде",
                null, null);
    }

    // ------------------------------------------------------------------ отчёт

    /** Технический отчёт (секреты не включаются). */
    public Map<String, Object> report(Settings cfg, MinecraftManager mc, FabricManager fabric,
                                      JavaManager java, ModManager mods) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("launcherVersion", Log.VERSION);
        root.put("generatedAt", System.currentTimeMillis());
        root.put("os", Utils.osCaption());
        root.put("javaRuntime", System.getProperty("java.version", "?"));
        root.put("javaGame", java.describe());
        root.put("physicalMemoryMb", Utils.physicalMemoryMb());
        root.put("maxHeapMb", Utils.maxMemoryMb());
        root.put("root", Paths.root().toString());
        root.put("gameDir", Paths.gameDir().toString());
        root.put("freeSpaceMb", Paths.freeSpaceMb(Paths.root()));
        root.put("config", cfg.toMap());
        root.put("settingsFile", Paths.config().toString());
        root.put("settingsCorrupted", SettingsManagerHolder.corrupted);
        root.put("minecraft", mc.describe(cfg.minecraftVersion));
        root.put("fabric", fabric.status(cfg.minecraftVersion,
                fabric.requiredLoader(cfg.minecraftVersion, cfg.fabricLoaderVersion)));
        root.put("buildVersion", mods.installedBuildVersion(cfg.instanceId()));
        root.put("modCount", mods.modCount(cfg.instanceId()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Check c : checks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.code());
            m.put("title", c.title());
            m.put("level", c.level().name());
            m.put("value", c.value());
            m.put("hint", c.hint());
            m.put("fix", c.fixAction());
            list.add(m);
        }
        root.put("checks", list);
        root.put("logTail", Log.tail(120));
        return root;
    }

    /** ZIP-отчёт: логи + diagnostics.json. Секреты и токены туда не попадают. */
    public Path writeReportZip(Settings cfg, MinecraftManager mc, FabricManager fabric, JavaManager java,
                               ModManager mods) throws IOException {
        Path out = Paths.updates().resolve("cw-diagnostics-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".zip");
        Utils.ensureDirectories(out.getParent());
        Map<String, Object> report = report(cfg, mc, fabric, java, mods);
        String json = Secrets.redact(Json.write(report));
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(out))) {
            putEntry(zos, "diagnostics.json", json);
            putEntry(zos, "launcher.log", String.join("\n", Log.tail(5000)));
            Path gameLog = Paths.gameLog(cfg.instanceId());
            if (Files.exists(gameLog)) {
                putEntry(zos, "minecraft-latest.log", Secrets.redact(Utils.readString(gameLog)));
            }
            putEntry(zos, "environment.txt", environmentText());
        }
        Log.info("Диагностический отчёт создан: " + out);
        return out;
    }

    private void putEntry(java.util.zip.ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new java.util.zip.ZipEntry(name));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    public String environmentText() {
        StringBuilder sb = new StringBuilder();
        sb.append("CWLauncher ").append(Log.VERSION).append('\n');
        sb.append("OS: ").append(Utils.osCaption()).append('\n');
        sb.append("Java: ").append(System.getProperty("java.version")).append('\n');
        sb.append("User: ").append(System.getProperty("user.name")).append('\n');
        sb.append("Root: ").append(Paths.root()).append('\n');
        sb.append("Free: ").append(Paths.freeSpaceMb(Paths.root())).append(" MB\n");
        sb.append("Memory: ").append(Utils.physicalMemoryMb()).append(" MB\n");
        sb.append("Cores: ").append(Utils.cpuCores()).append('\n');
        return sb.toString();
    }

    public int errorCount() {
        return (int) checks.stream().filter(c -> c.level() == Level.ERROR).count();
    }

    public int warnCount() {
        return (int) checks.stream().filter(c -> c.level() == Level.WARN).count();
    }

    /** Помечает, был ли config восстановлен из-за повреждения (ставит LauncherApp). */
    public static final class SettingsManagerHolder {
        public static volatile boolean corrupted;
    }
}
