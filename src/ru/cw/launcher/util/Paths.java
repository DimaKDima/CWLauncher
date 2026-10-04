package ru.cw.launcher.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Все пути приложения. Каталог пользователя определяется программно через
 * %APPDATA% (без жёстких "C:\...").
 *
 * <pre>
 * %APPDATA%\CWLauncher\
 *   config.json          настройки
 *   profiles.json        игровые профили
 *   accounts.json        аккаунты (без секретов)
 *   secrets.dpx          токены (Windows DPAPI)
 *   logs\launcher.log    логи
 *   cache\               сетевой кэш, новости, превью
 *   tmp\                 временные загрузки
 *   updates\             история и файлы обновлений лаунчера
 *   java\                портативные JRE
 *   minecraft\           versions, libraries, assets
 *   instances\<id>\      mods, config, saves, screenshots, update.txt, updates\
 * </pre>
 */
public final class Paths {

    /** Технические мосты: SettingsService устанавливает значения, чтобы util не зависел от core. */
    public static final class Holder {
        public static volatile String gameDir = "";
        /** Версия Minecraft, для которой сейчас считаются пути. */
        public static volatile String activeVersion = "1.20.1";
        /** Выбранный пользователем каталог конкретной версии. */
        public static final Map<String, String> versionDirs = new ConcurrentHashMap<>();
    }

    private Paths() {
    }

    public static Path versionDir(String id) {
        return versions().resolve(sanitize(id));
    }

    public static Path versionJsonPath(String id) {
        return versionDir(id).resolve(id + ".json");
    }

    public static Path versionJar(String id) {
        return versionDir(id).resolve(id + ".jar");
    }

    public static Path appData() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isBlank()) return java.nio.file.Paths.get(appdata);
        return home().resolve("AppData").resolve("Roaming");
    }

    public static Path home() {
        return java.nio.file.Paths.get(System.getProperty("user.home", "."));
    }

    public static Path root() {
        return appData().resolve("CWLauncher");
    }

    /**
     * Куда по умолчанию ставятся версии игры.
     * Не рядом с лаунчером: CWLauncher.exe часто лежит в Program Files, а туда писать нельзя.
     */
    public static Path gamesRoot() {
        return home().resolve("CWLauncher").resolve("games");
    }

    /** Папка лаунчера, Program Files и Windows — не место для файлов игры. */
    public static boolean forbiddenGameDir(Path path) {
        if (path == null) return true;
        Path p = path.toAbsolutePath().normalize();
        Path launcher = installRoot().toAbsolutePath().normalize();
        if (p.startsWith(launcher)) return true;
        String s = p.toString().toLowerCase(Locale.ROOT);
        return s.contains("\\program files") || s.contains("\\windows\\") || s.contains("/program files");
    }

    /**
     * Папка одной версии игры: versions, libraries, assets, mods, миры, шейдеры.
     * По умолчанию это папка пользователя CWLauncher\\games\\версия.
     */
    public static Path versionHome(String version) {
        String key = sanitize(version);
        String saved = Holder.versionDirs.get(key);
        if (saved == null) saved = Holder.versionDirs.get(version);
        if (saved != null && !saved.isBlank()) {
            Path chosen = java.nio.file.Paths.get(saved);
            if (!forbiddenGameDir(chosen)) return chosen;
        }
        return gamesRoot().resolve(key);
    }

    /** client.jar конкретной версии в её собственной папке, а не в папке другой версии. */
    public static Path vanillaJar(String version) {
        String key = sanitize(version);
        return versionHome(version).resolve("versions").resolve(key).resolve(key + ".jar");
    }

    public static boolean localGamePresent(String version) {
        return Files.isRegularFile(vanillaJar(version));
    }

    /** Уже скачанные версии: их видно и без интернета. */
    public static List<String> localGameIds() {
        List<String> out = new ArrayList<>();
        collectGames(gamesRoot(), out);
        for (String saved : Holder.versionDirs.values()) {
            if (saved == null || saved.isBlank()) continue;
            Path home = java.nio.file.Paths.get(saved);
            if (forbiddenGameDir(home) || !Files.isDirectory(home)) continue;
            String id = home.getFileName() == null ? "" : home.getFileName().toString();
            if (!id.isBlank() && localGamePresent(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    private static void collectGames(Path root, List<String> out) {
        if (!Files.isDirectory(root)) return;
        try (var list = Files.list(root)) {
            for (Path child : list.filter(Files::isDirectory).toList()) {
                String id = child.getFileName().toString();
                if (localGamePresent(id) && !out.contains(id)) out.add(id);
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Уносит уже скачанные версии из папки лаунчера в папку пользователя.
     * Если Windows не даёт перенести, старые файлы остаются, новые качаются в новое место.
     */
    public static void relocateGames() {
        Path old = installRoot().resolve("games");
        if (!Files.isDirectory(old)) return;
        try {
            Files.createDirectories(gamesRoot());
        } catch (IOException e) {
            Log.warn("Папка игр не создана: " + Log.reason(e));
            return;
        }
        try (var list = Files.list(old)) {
            for (Path child : list.filter(Files::isDirectory).toList()) {
                Path dest = gamesRoot().resolve(child.getFileName().toString());
                if (Files.exists(dest)) continue;
                try {
                    Files.move(child, dest);
                    Log.info("Игра перенесена: " + dest);
                } catch (IOException e) {
                    Log.warn("Не удалось перенести " + child.getFileName() + ": " + Log.reason(e));
                }
            }
        } catch (IOException e) {
            Log.warn("Старая папка игр не прочитана: " + Log.reason(e));
        }
    }

    /** Каталог Minecraft текущей версии (versions/libraries/assets и файлы мира). */
    public static Path gameDir() {
        String version = Holder.activeVersion;
        if (version != null && !version.isBlank()) return versionHome(version);
        return installRoot().resolve("games");
    }

    public static Path backgroundDir() {
        return installRoot().resolve("assets").resolve("background");
    }

    public static List<Path> backgroundImages() {
        List<Path> out = new ArrayList<>();
        Path dir = backgroundDir();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                        || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp");
            }).sorted().forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    public static Path versions() {
        return gameDir().resolve("versions");
    }

    public static Path libs() {
        return gameDir().resolve("libraries");
    }

    public static Path assets() {
        return gameDir().resolve("assets");
    }

    public static Path config() {
        return root().resolve("config.json");
    }

    public static Path profilesFile() {
        return root().resolve("profiles.json");
    }

    public static Path accountsFile() {
        return root().resolve("accounts.json");
    }

    /** Папка скина одного аккаунта: %APPDATA%\CWLauncher\skins\<id>. */
    public static Path accountSkinDir(String accountId) {
        String id = accountId == null ? "" : accountId.replaceAll("[^A-Za-z0-9\\-]", "");
        if (id.isBlank()) id = "none";
        return root().resolve("skins").resolve(id);
    }

    public static Path secretsFile() {
        return root().resolve("secrets.dpx");
    }

    public static Path logs() {
        return root().resolve("logs");
    }

    public static Path mainLog() {
        return logs().resolve("launcher.log");
    }

    public static Path gameLog() {
        return logs().resolve("latest.log");
    }

    /** Логигри конкретной версии/экземпляра. */
    public static Path gameLog(String instanceId) {
        return logs().resolve(sanitize(instanceId)).resolve("latest.log");
    }

    public static Path cache() {
        return root().resolve("cache");
    }

    public static Path tmp() {
        return root().resolve("tmp");
    }

    public static Path updates() {
        return root().resolve("updates");
    }

    public static Path javaDir() {
        return root().resolve("java");
    }

    public static Path instances() {
        return gameDir();
    }

    /** Своя папка игры: моды, миры, шейдеры и конфиги лежат прямо в ней. */
    public static Path instance(String id) {
        return gameDir();
    }

    /**
     * Дополнительные полные папки одной версии: моды, миры, конфиги, загрузки.
     * Библиотеки и ассеты остаются в общей папке версии.
     */
    public static Path playInstancesRoot() {
        String version = Holder.activeVersion;
        if (version == null || version.isBlank()) version = "1.20.1";
        return versionHome(version).resolve("instances");
    }

    public static List<String> playInstanceNames() {
        List<String> names = new ArrayList<>();
        Path root = playInstancesRoot();
        if (!Files.isDirectory(root)) return names;
        try (var list = Files.list(root)) {
            for (Path child : list.filter(Files::isDirectory).toList()) {
                String name = child.getFileName() == null ? "" : child.getFileName().toString();
                if (!name.isBlank()) names.add(name);
            }
        } catch (IOException ignored) {
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Имя папки. Пустая строка — имя не годится. */
    public static String playFolderName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replaceAll("[<>:\"/\\\\|?*\\u0000-\\u001F]", "");
        s = s.replaceAll("\\s+", " ");
        if (s.length() > 40) s = s.substring(0, 40).trim();
        if (s.isBlank() || s.equals(".") || s.equals("..")) return "";
        if (s.equalsIgnoreCase("instances") || s.equalsIgnoreCase("natives")) return "";
        return s;
    }

    public static Path playInstance(String name) {
        return playInstancesRoot().resolve(playFolderName(name)).normalize();
    }

    public static Path createPlayInstance(String raw) throws IOException {
        String safe = playFolderName(raw);
        if (safe.isEmpty()) throw new IOException("bad name");
        Path root = playInstancesRoot().toAbsolutePath().normalize();
        Path dir = root.resolve(safe).normalize();
        if (!dir.startsWith(root)) throw new IOException("bad name");
        for (String sub : new String[]{"mods", "saves", "config", "resourcepacks", "shaderpacks", "downloads"}) {
            Files.createDirectories(dir.resolve(sub));
        }
        return dir;
    }

    public static Path mods(String id) {
        return instance(id).resolve("mods");
    }

    public static Path configs(String id) {
        return instance(id).resolve("config");
    }

    public static Path saves(String id) {
        return instance(id).resolve("saves");
    }

    public static Path shaderpacks(String id) {
        return instance(id).resolve("shaderpacks");
    }

    public static Path resourcepacks(String id) {
        return instance(id).resolve("resourcepacks");
    }

    public static Path updateTxt(String id) {
        return instance(id).resolve("update.txt");
    }

    public static Path updatesHistory(String id) {
        return instance(id).resolve("updates");
    }

    public static Path buildManifest(String id) {
        return instance(id).resolve("build-manifest.json");
    }

    public static Path buildState(String id) {
        return instance(id).resolve("build-state.json");
    }

    /**
     * Место для картинки пользователя: assets/background/default_background.png
     * рядом с EXE. null, если файла ещё нет — тогда фон рисует BackgroundRenderer.
     */
    /**
     * Фон, который лаунчер берёт сам: положите сюда файл main.png.
     * Для собранного CWLauncher.exe это папка assets\\background рядом с exe.
     */
    public static Path mainBackground() {
        return installRoot().resolve("assets").resolve("background").resolve("main.png");
    }

    public static Path bundledDefaultBackground() {
        Path[] candidates = new Path[]{
                installRoot().resolve("assets").resolve("background").resolve("default_background.png"),
                java.nio.file.Paths.get("assets", "background", "default_background.png")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) return p.toAbsolutePath().normalize();
        }
        return null;
    }

    /** Фактический JAR или каталог классов, из которого загружен CWLauncher. */
    public static Path codeSource() {
        try {
            return java.nio.file.Paths.get(
                    Paths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Корень установки: каталог с CWLauncher.exe.
     * У jpackage классы лежат в app\CWLauncher.jar, поэтому поднимаемся на уровень выше app.
     */
    public static Path installRoot() {
        Path loc = codeSource();
        if (loc == null) return java.nio.file.Paths.get(".").toAbsolutePath().normalize();
        if (Files.isDirectory(loc)) return loc.toAbsolutePath().normalize();
        Path parent = loc.getParent();
        if (parent != null && parent.getFileName() != null
                && "app".equalsIgnoreCase(parent.getFileName().toString())
                && parent.getParent() != null) {
            return parent.getParent().toAbsolutePath().normalize();
        }
        return (parent == null ? loc : parent).toAbsolutePath().normalize();
    }

    /** Совместимое имя: корень установки, а не сам JAR. */
    public static Path jarLocation() {
        return installRoot();
    }

    /** JAR, который updater может заменить. Работающий EXE сам себя не подменяет. */
    public static Path updatableFile() {
        Path loc = codeSource();
        if (loc != null && Files.isRegularFile(loc)
                && loc.getFileName().toString().toLowerCase().endsWith(".jar")) {
            return loc.toAbsolutePath().normalize();
        }
        Path bundled = installRoot().resolve("app").resolve("CWLauncher.jar");
        if (Files.isRegularFile(bundled)) return bundled;
        Path local = installRoot().resolve("CWLauncher.jar");
        if (Files.isRegularFile(local)) return local;
        return loc;
    }

    /** CWLauncher.exe, если приложение собрано jpackage. */
    public static Path launcherExecutable() {
        String prop = System.getProperty("jpackage.app-path");
        if (prop != null && !prop.isBlank()) {
            Path p = java.nio.file.Paths.get(prop);
            if (Files.isRegularFile(p)) return p.toAbsolutePath().normalize();
        }
        Path exe = installRoot().resolve("CWLauncher.exe");
        if (Files.isRegularFile(exe)) return exe.toAbsolutePath().normalize();
        return null;
    }

    public static String sanitize(String name) {
        if (name == null || name.isBlank()) return "default";
        String s = name.replaceAll("[^A-Za-z0-9._+-]", "_");
        while (s.contains("..")) s = s.replace("..", "_");
        if (s.length() > 64) s = s.substring(0, 64);
        return s.isBlank() ? "default" : s;
    }

    public static void ensureDirs() {
        try {
            Files.createDirectories(root());
            Files.createDirectories(gameDir());
            Files.createDirectories(versions());
            Files.createDirectories(libs());
            Files.createDirectories(assets());
            Files.createDirectories(logs());
            Files.createDirectories(tmp());
            Files.createDirectories(cache());
            Files.createDirectories(updates());
            Files.createDirectories(javaDir());
            Files.createDirectories(instances());
            String id = Holder.activeVersion == null ? "1.20.1" : Holder.activeVersion;
            Files.createDirectories(mods(id));
            Files.createDirectories(saves(id));
            Files.createDirectories(configs(id));
            Files.createDirectories(shaderpacks(id));
            Files.createDirectories(resourcepacks(id));
        } catch (IOException e) {
            Log.error("Не удалось создать каталоги: " + Log.reason(e), e);
        }
    }

    public static long freeSpaceMb(Path p) {
        try {
            Path store = p;
            while (store != null && !Files.exists(store)) store = store.getParent();
            if (store == null) return -1;
            return Files.getFileStore(store).getUsableSpace() / (1024 * 1024);
        } catch (IOException e) {
            return -1;
        }
    }
}
