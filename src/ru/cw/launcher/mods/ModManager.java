package ru.cw.launcher.mods;

import ru.cw.launcher.core.Operation;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.core.State;
import ru.cw.launcher.model.GameState;
import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.network.GoogleDrive;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * ModManager: загрузка сборки Common World (ZIP), проверка, что это действительно ZIP,
 * распаковка во временный каталог, проверка modsVersion.txt против cwVersion.txt,
 * и только после успеха — замена рабочей сборки.
 */
public final class ModManager {

    public static final String ZIP_NAME = "modsCWL.zip";
    public static final String MODS_VERSION_FILE = "modsVersion.txt";
    public static final String BUILD_VERSION_FILE = "cwVersion.txt";
    public static final String LAUNCHER_VERSION_FILE = "lVersion.txt";

    public record BuildState(String installedVersion, String manifestHash, long installedAt, int modCount) {
    }

    private final State state;
    private String modCountKey = "";
    private int modCountValue = -1;
    private static final Pattern MOD_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    public ModManager(State state) {
        this.state = state;
    }

    public Path modsDir(String instanceId) {
        return Paths.mods(instanceId);
    }

    /**
     * Скачивает modsCWL.zip в кэш и читает Version.txt внутри.
     * Эта версия важнее отдельного cwVersion.txt: файл на Диске может отставать от архива.
     */
    public String downloadRemoteModsVersion(Settings cfg) throws IOException, InterruptedException {
        Path zip = Paths.cache().resolve(ZIP_NAME);
        GoogleDrive.download(cfg.modpackUrl, zip, Utils::looksLikeZip, null, null);
        String version = readModsVersionZip(zip);
        if (version == null) throw new IOException("В modsCWL.zip нет Version.txt");
        return version;
    }

    /** Версия установленной сборки: из build-state.json, иначе из version.txt в папке модов. */
    public String installedBuildVersion(String instanceId) {
        BuildState s = readState(instanceId);
        if (s != null && s.installedVersion() != null && !s.installedVersion().isBlank()) {
            return s.installedVersion();
        }
        return readModsVersion(Paths.mods(instanceId));
    }

    public BuildState readState(String instanceId) {
        Path file = Paths.buildState(instanceId);
        if (!Files.exists(file)) return null;
        try {
            Map<String, Object> m = Json.parseObject(Utils.readString(file));
            return new BuildState(Json.str(m, "version", ""), Json.str(m, "manifestHash", ""),
                    (long) Json.num(m, "installedAt", 0), (int) Json.num(m, "modCount", 0));
        } catch (IOException e) {
            Log.warn("build-state.json повреждён: " + Log.reason(e));
            return null;
        }
    }

    public void writeState(String instanceId, String version, String manifestHash, int modCount) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("version", version);
            m.put("manifestHash", manifestHash);
            m.put("installedAt", System.currentTimeMillis());
            m.put("modCount", modCount);
            m.put("zipName", ZIP_NAME);
            Utils.writeString(Paths.buildState(instanceId), Json.write(m));
        } catch (IOException e) {
            Log.error("Не удалось записать build-state.json: " + Log.reason(e), e);
        }
    }

    // ------------------------------------------------------------------ установка

    /**
     * Полный цикл: скачать → проверить ZIP → распаковать во временный каталог →
     * сравнить modsVersion.txt с cwVersion.txt (до N попыток) → заменить рабочую сборку.
     */
    public Operation installOperation(Settings cfg, String remoteVersion, ProgressListener outer) {
        String instanceId = cfg.instanceId();
        Operation op = new Operation("Загрузка сборки Common World", state)
                .runningAs(remoteVersion == null || remoteVersion.isBlank() || installedBuildVersion(instanceId) == null
                        ? GameState.DOWNLOADING : GameState.UPDATING)
                .finishAs(GameState.READY);
        int attempts = Math.max(1, cfg.maxAttempts);
        op.add("Подготовка временного каталога", 0.5, () -> {
            Path tmp = Paths.tmp().resolve("build");
            Utils.clearDir(tmp);
            Files.createDirectories(tmp);
        });
        op.add("Скачиваю моды Common World", 6, () -> {
            Path zip = Paths.tmp().resolve(ZIP_NAME);
            Path cached = Paths.cache().resolve(ZIP_NAME);
            Utils.deleteQuietly(zip);
            boolean reuse = false;
            if (Files.isRegularFile(cached) && Utils.looksLikeZip(cached)) {
                String cachedVer = readModsVersionZip(cached);
                if (cachedVer != null && (remoteVersion == null || remoteVersion.isBlank()
                        || Utils.compareVersions(cachedVer, remoteVersion) == 0)) {
                    Files.copy(cached, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    reuse = Utils.looksLikeZip(zip);
                    if (reuse) Log.info("Беру уже проверенный архив модов " + cachedVer);
                }
            }
            if (!reuse) {
                GoogleDrive.download(cfg.modpackUrl, zip, Utils::looksLikeZip,
                        (done, total) -> outer.progress(done, total, "Скачиваю моды Common World"), op.cancel()::isCancelled);
            }
            if (!Utils.looksLikeZip(zip)) {
                Utils.deleteQuietly(zip);
                throw new IOException("Не удалось загрузить сборку модов. Проверьте интернет и повторите попытку.");
            }
        });
        String[] accepted = {null};
        op.add("Проверка версии модов", 2, () -> {
            String remote = remoteVersion;
            if (remote == null || remote.isBlank()) {
                var cw = ru.cw.launcher.updater.UpdateChecker.cwVersion(cfg);
                if (cw.ok()) remote = cw.value();
            }
            IOException last = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                Path zip = Paths.tmp().resolve(ZIP_NAME);
                Path stage = Paths.tmp().resolve("staging");
                Utils.clearDir(stage);
                Files.createDirectories(stage);
                int extracted = Utils.extractZip(zip, stage);
                if (extracted == 0) throw new IOException("Архив пуст или повреждён");
                String zipVersion = readModsVersion(stage);
                if (zipVersion == null) {
                    last = new IOException("В архиве нет version.txt с версией модов");
                    Log.warn(last.getMessage() + ", попытка " + attempt);
                    Utils.deleteQuietly(zip);
                } else {
                    if (remote != null && !remote.isBlank()
                            && Utils.compareVersions(zipVersion, remote) != 0) {
                        Log.warn("cwVersion.txt = " + remote + ", в архиве модов " + zipVersion
                                + ". Ставлю версию из архива.");
                    } else {
                        Log.info("Версия модов в архиве " + zipVersion
                                + (remote == null || remote.isBlank()
                                ? " (cwVersion.txt не прочитан, архив принимается)"
                                : " совпадает с ожидаемой " + remote));
                    }
                    accepted[0] = zipVersion;
                    return;
                }
                if (attempt < attempts) {
                    GoogleDrive.download(cfg.modpackUrl, zip, Utils::looksLikeZip,
                            (done, total) -> outer.progress(done, total, "Скачиваю моды Common World"), op.cancel()::isCancelled);
                    if (!Utils.looksLikeZip(zip)) {
                        throw new IOException("Google Drive отдал не ZIP-файл");
                    }
                }
            }
            throw last == null ? new IOException("Не удалось проверить версию модов") : last;
        });
        op.add("Установка модов", 3, () -> {
            Path stage = Paths.tmp().resolve("staging");
            Path working = Paths.mods(instanceId);
            Path retired = retireModsFolder(working);
            int moved = placePack(stage, Paths.instance(instanceId), working);
            String installed = accepted[0] != null ? accepted[0] : readModsVersion(stage);
            if (installed == null) installed = readModsVersion(working);
            if (installed != null) {
                Utils.writeString(working.resolve("Version.txt"), installed);
            }
            writeState(instanceId, installed == null ? remoteVersion : installed,
                    manifestHash(working), moved);
            if (retired != null) deleteRetired(retired);
            Log.info("Папка модов очищена, установлено файлов: " + moved);
            Utils.deleteQuietly(Paths.tmp().resolve(ZIP_NAME));
        });
        op.add("Проверка установленной сборки", 1, () -> {
            List<String> problems = new ModVerifier().verify(cfg).problems();
            if (!problems.isEmpty()) {
                Log.warn("После установки найдены проблемы: " + String.join("; ", problems));
            }
        });
        return op;
    }

    /**
     * Убирает текущую папку mods с пути, не копируя каждый jar на другой диск.
     * Перенос отдельного файла падает, если его держит игра или антивирус.
     * Папка целиком на том же диске переименовывается, а mods создаётся пустой.
     */
    private static Path retireModsFolder(Path modsDir) throws IOException {
        Path parent = modsDir.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            try (var siblings = Files.list(parent)) {
                for (Path old : siblings.toList()) {
                    String name = old.getFileName().toString();
                    if (name.startsWith("mods-old-")) Utils.deleteTree(old);
                }
            }
        }
        Utils.ensureDirectories(modsDir);
        boolean empty;
        try (var children = Files.list(modsDir)) {
            empty = children.findAny().isEmpty();
        }
        if (empty) {
            Log.info("Папка модов уже пустая: " + modsDir);
            return null;
        }
        if (parent == null) {
            wipeModsFolder(modsDir);
            return null;
        }
        Path retired = parent.resolve("mods-old-" + System.currentTimeMillis());
        Files.move(modsDir, retired);
        Files.createDirectories(modsDir);
        Log.info("Старые моды убраны, папка mods пустая: " + retired.getFileName());
        return retired;
    }

    /** Старая папка удаляется после установки. Если файл ещё занят, обновление уже не срывается. */
    private static void deleteRetired(Path retired) {
        Utils.deleteTree(retired);
        if (Files.exists(retired)) {
            Log.warn("Старые моды пока заняты другим процессом и будут удалены при следующем обновлении: "
                    + retired.getFileName());
        } else {
            Log.info("Старые моды удалены");
        }
    }

    /** Удаляет всё внутри папки mods и не продолжает установку, если что-то осталось. */
    private static void wipeModsFolder(Path modsDir) throws IOException {
        Utils.ensureDirectories(modsDir);
        List<Path> all;
        try (var walk = Files.walk(modsDir)) {
            all = new ArrayList<>(walk.filter(p -> !p.equals(modsDir)).toList());
        }
        all.sort(Comparator.comparingInt((Path p) -> p.getNameCount()).reversed());
        for (Path p : all) Files.deleteIfExists(p);
        try (var left = Files.list(modsDir)) {
            if (left.findAny().isPresent()) {
                throw new IOException("Папка модов не очистилась: " + modsDir);
            }
        }
        Log.info("Папка модов очищена перед установкой: " + modsDir);
    }

    public static List<Path> listFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    /**
     * Кладёт в папку модов каждый jar из архива, в том числе из вложенной папки mods.
     * Конфиги, ресурспаки и шейдеры копируются в папку этой версии.
     */
    private static int placePack(Path stage, Path gameDir, Path modsDir) throws IOException {
        int jars = 0;
        try (var walk = Files.walk(stage)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (isModsVersionName(name)) continue;
                String rel = stage.relativize(p).toString().replace('\\', '/');
                String lower = rel.toLowerCase(Locale.ROOT);
                Path extra = packFolder(gameDir, lower);
                if (extra != null) {
                    String tail = tailAfter(rel, extra.getFileName().toString());
                    if (tail != null && !tail.isBlank()) {
                        Path dest = extra.resolve(tail);
                        Files.createDirectories(dest.getParent());
                        Files.copy(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    continue;
                }
                if (!lower.endsWith(".jar")) continue;
                if (lower.contains("/libraries/") || lower.contains("/versions/") || lower.contains("/.fabric/")) {
                    continue;
                }
                Files.copy(p, modsDir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                jars++;
            }
        }
        return jars;
    }

    private static Path packFolder(Path gameDir, String lower) {
        if (lower.startsWith("config/") || lower.contains("/config/")) return gameDir.resolve("config");
        if (lower.startsWith("resourcepacks/") || lower.contains("/resourcepacks/")) {
            return gameDir.resolve("resourcepacks");
        }
        if (lower.startsWith("shaderpacks/") || lower.contains("/shaderpacks/")) {
            return gameDir.resolve("shaderpacks");
        }
        if (lower.startsWith("datapacks/") || lower.contains("/datapacks/")) return gameDir.resolve("datapacks");
        if (lower.startsWith("defaultconfigs/") || lower.contains("/defaultconfigs/")) {
            return gameDir.resolve("defaultconfigs");
        }
        return null;
    }

    private static String tailAfter(String rel, String folder) {
        String norm = rel.replace('\\', '/');
        String mark = folder + "/";
        int i = norm.toLowerCase(Locale.ROOT).indexOf(mark.toLowerCase(Locale.ROOT));
        if (i < 0) return null;
        return norm.substring(i + mark.length());
    }

    /** Version.txt, modsVersion.txt или cwVersion.txt прямо из zip, без распаковки. */
    public static String readModsVersionZip(Path zip) {
        if (zip == null || !Files.isRegularFile(zip)) return null;
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                int slash = name.lastIndexOf('/');
                String file = slash >= 0 ? name.substring(slash + 1) : name;
                if (!isModsVersionName(file)) continue;
                String text;
                try (InputStream in = archive.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                String token = versionToken(text);
                if (token != null) return token;
            }
        } catch (IOException e) {
            Log.debug("Версия в архиве модов не прочитана: " + Log.reason(e));
        }
        return null;
    }

    /**
     * Версия модов из распакованной папки. Ищется version.txt, modsVersion.txt и cwVersion.txt,
     * в том числе с двойным расширением Windows (modsVersion.txt.txt).
     */
    public static String readModsVersion(Path root) {
        if (root == null || !Files.exists(root)) return null;
        try (var walk = Files.walk(root, 6)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (!isModsVersionName(p.getFileName().toString())) continue;
                String token = versionToken(Utils.readString(p));
                if (token != null) return token;
            }
        } catch (IOException e) {
            Log.debug("Версия модов не найдена: " + Log.reason(e));
        }
        return null;
    }

    /** Чтение конкретного файла версии из распакованного архива (в т.ч. вложенно). */
    public static String readVersionFile(Path root, String name) {
        try (var walk = Files.walk(root, 6)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                if (p.getFileName().toString().equalsIgnoreCase(name)
                        || isModsVersionName(p.getFileName().toString())) {
                    String token = versionToken(Utils.readString(p));
                    if (token != null) return token;
                }
            }
        } catch (IOException e) {
            Log.debug("Версия не найдена: " + Log.reason(e));
        }
        return null;
    }

    /** modsVersion.txt, version.txt, cwVersion.txt и то же имя с лишним .txt от Windows. */
    public static boolean isModsVersionName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        while (n.endsWith(".txt.txt")) n = n.substring(0, n.length() - 4);
        return n.equals("modsversion.txt") || n.equals("version.txt") || n.equals("cwversion.txt");
    }

    /** Первая версия вида 3.1.1 из текста файла. Лишний текст и BOM отбрасываются. */
    public static String versionToken(String raw) {
        if (raw == null) return null;
        String s = raw.replace("\uFEFF", "").trim();
        if (s.isEmpty()) return null;
        String first = s.split("\\r?\\n", 2)[0].trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)+)").matcher(first);
        if (m.find()) return m.group(1);
        String norm = Utils.normalizeVersion(first);
        return "0".equals(norm) ? null : norm;
    }

    public static String manifestHash(Path modsDir) {
        List<String> names = new ArrayList<>();
        try (var s = Files.list(modsDir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                names.add(p.getFileName() + ":" + Utils.size(p));
            }
        } catch (IOException ignored) {
        }
        java.util.Collections.sort(names);
        return Utils.sha1Hex(String.join("|", names).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Открытие папки модов выбранной версии (создаётся при отсутствии). */
    public Path prepareModsDir(String instanceId) throws IOException {
        Path dir = Paths.mods(instanceId);
        Files.createDirectories(dir);
        return dir;
    }

    public boolean isInstalled(String instanceId) {
        BuildState s = readState(instanceId);
        if (s == null) return false;
        try {
            return listFiles(Paths.mods(instanceId)).stream().anyMatch(p -> p.toString().endsWith(".jar"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Число модов, которые видит игра: каждый fabric.mod.json, включая моды,
     * вложенные в другие jar (Fabric API и остальные). Не число файлов в папке.
     */
    public int modCount(String instanceId) {
        Path dir = Paths.mods(instanceId);
        String key = modStamp(dir);
        if (key.equals(modCountKey) && modCountValue >= 0) return modCountValue;
        int n = countLoadedMods(dir);
        modCountKey = key;
        modCountValue = n;
        return n;
    }

    private static String modStamp(Path dir) {
        if (!Files.isDirectory(dir)) return "0";
        long size = 0;
        int files = 0;
        try (var s = Files.list(dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                files++;
                size += Utils.size(p);
            }
        } catch (IOException e) {
            return "err";
        }
        return files + ":" + size;
    }

    private static int countLoadedMods(Path dir) {
        Set<String> ids = new HashSet<>();
        int plain = 0;
        try {
            for (Path jar : listFiles(dir)) {
                if (!jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                int before = ids.size();
                collectModIds(jar, ids, 0);
                if (ids.size() == before) plain++;
            }
        } catch (IOException e) {
            Log.warn("Не удалось посчитать моды: " + Log.reason(e));
        }
        return ids.size() + plain;
    }

    private static void collectModIds(Path jar, Set<String> ids, int depth) {
        if (depth > 2 || !Files.isRegularFile(jar)) return;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry meta = zip.getEntry("fabric.mod.json");
            if (meta != null) {
                String id = modId(zip.getInputStream(meta).readAllBytes());
                if (id != null) ids.add(id);
            }
            if (depth >= 2) return;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("META-INF/jars/")
                        || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                if (entry.getSize() > 24L * 1024 * 1024) continue;
                byte[] data;
                try (InputStream in = zip.getInputStream(entry)) {
                    data = in.readAllBytes();
                }
                collectNested(data, ids, depth + 1);
            }
        } catch (IOException e) {
            Log.debug("Мод не прочитан: " + jar.getFileName() + " — " + Log.reason(e));
        }
    }

    private static void collectNested(byte[] data, Set<String> ids, int depth) {
        if (depth > 2) return;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if ("fabric.mod.json".equals(name)) {
                    String id = modId(zin.readAllBytes());
                    if (id != null) ids.add(id);
                } else if (depth < 2 && name.startsWith("META-INF/jars/")
                        && name.toLowerCase(Locale.ROOT).endsWith(".jar")
                        && entry.getSize() <= 24L * 1024 * 1024) {
                    collectNested(zin.readAllBytes(), ids, depth + 1);
                }
            }
        } catch (IOException e) {
            Log.debug("Вложенный мод не прочитан: " + Log.reason(e));
        }
    }

    private static String modId(byte[] json) {
        if (json == null || json.length == 0) return null;
        String text = new String(json, StandardCharsets.UTF_8).replace("\uFEFF", "");
        Matcher m = MOD_ID.matcher(text);
        if (!m.find()) return null;
        String id = m.group(1).trim();
        return id.isEmpty() ? null : id;
    }
}
