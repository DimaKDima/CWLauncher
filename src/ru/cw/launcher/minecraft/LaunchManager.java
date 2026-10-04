package ru.cw.launcher.minecraft;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.accounts.ElyAuth;
import ru.cw.launcher.accounts.ElyAuthManager;
import ru.cw.launcher.core.Operation;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.core.State;
import ru.cw.launcher.model.GameState;
import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.net.Downloads;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.profiles.LauncherProfile;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LaunchManager: формирование РЕАЛЬНОЙ команды запуска Minecraft — классpath, natives,
 * assets, аргументы version.json, Java, аккаунт, RAM, дополнительные JVM-аргументы,
 * каталог экземпляра и ведение игрового лога.
 */
public final class LaunchManager {

    private final State state;
    private final MinecraftManager mc;
    private final JavaManager javaMgr;
    private volatile Process process;
    private volatile Path lastNatives;
    private final Map<String, List<Process>> running = new ConcurrentHashMap<>();
    private final Map<String, Path> gameLogs = new ConcurrentHashMap<>();
    private final Set<String> launching = ConcurrentHashMap.newKeySet();
    private final Set<String> booted = ConcurrentHashMap.newKeySet();

    public LaunchManager(State state, MinecraftManager mc, JavaManager javaMgr) {
        this.state = state;
        this.mc = mc;
        this.javaMgr = javaMgr;
    }

    public boolean running() {
        if (process != null && process.isAlive()) return true;
        for (List<Process> list : running.values()) {
            for (Process p : list) {
                if (p != null && p.isAlive()) return true;
            }
        }
        return false;
    }

    public java.util.List<Long> runningPids() {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        for (List<Process> list : running.values()) {
            for (Process p : list) {
                if (p != null && p.isAlive()) ids.add(p.pid());
            }
        }
        if (process != null && process.isAlive() && !ids.contains(process.pid())) ids.add(process.pid());
        return ids;
    }

    public int aliveCount(String version) {
        if (version == null) return 0;
        List<Process> list = running.get(version);
        if (list == null) return 0;
        list.removeIf(p -> p == null || !p.isAlive());
        return list.size();
    }

    private List<Process> sessions(String version) {
        return running.computeIfAbsent(version, k -> new CopyOnWriteArrayList<>());
    }

    /** Обязательные файлы для запуска (раздел 24 ТЗ). */
    public List<String> missingRequired(Settings cfg, String profileId) {
        List<String> missing = new ArrayList<>();
        try {
            VersionJson v = mc.resolved(profileId);
            if (!Files.exists(mc.clientJar(profileId))) {
                missing.add("клиент " + mc.clientJar(profileId).getFileName());
            }
            if (v.mainClass == null || v.mainClass.isBlank()) missing.add("mainClass в profile.json");
            if (v.assetIndexUrl == null || v.assetIndexUrl.isBlank()) missing.add("индекс ресурсов");
            for (VersionJson.Lib lib : v.libraries) {
                if (!lib.download || lib.path == null) continue;
                if (!Files.exists(Paths.libs().resolve(lib.path))) {
                    missing.add("библиотека " + lib.name);
                }
            }
        } catch (IOException e) {
            missing.add("profile.json (" + Log.reason(e) + ")");
        }
        return missing;
    }

    /** Подготовка assets: скачивает отсутствующие объекты, если нужно. */
    public Operation assetsOperation(String profileId, ProgressListener pl) {
        Operation op = new Operation("Подготовка ресурсов", state).runningAs(GameState.INSTALLING)
                .finishAs(GameState.READY);
        op.add("Загрузка индекса ресурсов", 1, () -> {
            VersionJson v = mc.resolved(profileId);
            Path index = Paths.assets().resolve("indexes").resolve(v.assets + ".json");
            if (!Files.exists(index) || Files.size(index) == 0) {
                Utils.ensureDirectories(index.getParent());
                Http.download(v.assetIndexUrl, index, v.assetIndexSha1, v.assetIndexSize, null,
                        op.cancel()::isCancelled);
            }
        });
        op.add("Загрузка объектов ресурсов", 8, () -> {
            VersionJson v = mc.resolved(profileId);
            Path index = Paths.assets().resolve("indexes").resolve(v.assets + ".json");
            Map<String, Object> root = Json.parseObject(Utils.readString(index));
            Map<String, Object> objects = Json.obj(root, "objects");
            if (objects == null) throw new IOException("Индекс ресурсов пуст");
            int total = objects.size();
            pl.progress(0, Math.max(1, total), "Объектов для проверки: " + total);
            List<Downloads.Item> batch = new ArrayList<>();
            for (Map.Entry<String, Object> e : objects.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> o = (Map<String, Object>) mm;
                String hash = Json.str(o, "hash", "");
                long size = (long) Json.num(o, "size", 0);
                if (hash.length() < 5) continue;
                Path target = Paths.assets().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                if (Files.exists(target) && Files.size(target) == size) continue;
                batch.add(new Downloads.Item(
                        "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                        target, hash, size));
            }
            int failed = Downloads.fetch(batch, pl, "Ресурсы Minecraft");
            if (failed > 0) throw new IOException("Не удалось загрузить ресурсы Minecraft: " + failed);
        });
        return op;
    }

    /** Извлечение natives-библиотек (.dll) в каталог экземпляра. */
    public Path extractNatives(String profileId, VersionJson v, Path playDir) throws IOException {
        Path root = playDir == null ? Paths.instance(profileId) : playDir;
        Path nativesDir = root.resolve("natives").resolve(Long.toString(System.nanoTime()));
        lastNatives = nativesDir;
        Utils.ensureDirectories(nativesDir);
        int count = 0;
        for (VersionJson.Lib lib : v.libraries) {
            if (!lib.isNative() || lib.path == null) continue;
            Path jar = Paths.libs().resolve(lib.path);
            if (!Files.exists(jar)) continue;
            try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var en = entries.nextElement();
                    String name = en.getName();
                    if (en.isDirectory() || name.startsWith("META-INF")) continue;
                    String simple = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                    if (simple.endsWith(".dylib") || simple.endsWith(".so")) {
                        if (!Utils.isWindows()) continue;
                    }
                    Path out = nativesDir.resolve(simple).normalize();
                    if (!out.startsWith(nativesDir)) throw new IOException("ZIP Slip в natives: " + name);
                    Files.copy(zip.getInputStream(en), out,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }
        // lwjgl glfw/win32 файлы иногда лежат в корневых jar'ах — common case обработан выше
        Log.info("Извлечено natives-файлов: " + count + " → " + nativesDir);
        return nativesDir;
    }

    /** Полный классpath игры (client + библиотеки). */
    public String classpath(String profileId, VersionJson v) throws IOException {
        Set<Path> cp = new LinkedHashSet<>();
        for (VersionJson.Lib lib : v.libraries) {
            if (!lib.download || lib.path == null) continue;
            Path p = Paths.libs().resolve(lib.path);
            if (Files.exists(p)) cp.add(p);
            else Log.warn("Отсутствует библиотека в classpath: " + lib.name);
        }
        cp.add(mc.clientJar(profileId));
        return String.join(java.io.File.pathSeparator, cp.stream().map(Path::toString).toList());
    }

    /**
     * Формирует команду запуска. Безопасные пользовательские аргументы добавляются
     * ПОСЛЕ базовых и не могут их испортить.
     */
    public List<String> buildCommand(Settings cfg, LauncherProfile profile, Account account, String profileId)
            throws IOException {
        return buildCommand(cfg, profile, account, profileId, Paths.instance(profileId));
    }

    public List<String> buildCommand(Settings cfg, LauncherProfile profile, Account account, String profileId,
                                     Path playDir) throws IOException {
        VersionJson v = mc.resolved(profileId);
        if (v.mainClass == null || v.mainClass.isBlank()) {
            throw new IOException("В профиле " + profileId + " не указан mainClass");
        }
        JavaManager.Runtime rt = javaMgr.select(profile.minecraftVersion, cfg.javaPath);
        if (rt == null) {
            throw new IOException("Не найдена Java " + JavaManager.requiredMajor(profile.minecraftVersion)
                    + ", необходимая для Minecraft " + profile.minecraftVersion);
        }
        Path gameDir = playDir == null ? Paths.instance(profileId) : playDir;
        Path natives = extractNatives(profileId, v, gameDir);
        String cp = classpath(profileId, v);
        Files.createDirectories(gameDir);
        String assetsRoot = Paths.assets().toString();

        int configured = profile.useGlobalSettings ? cfg.ramMb : profile.ramMb;
        int ram = ru.cw.launcher.settings.SettingsManager.gameRamMb(configured);

        List<String> cmd = new ArrayList<>();
        cmd.add(windowBinary(rt.path()));
        // Память ставится сразу после java, до -cp. Иначе -Xmx может не попасть в JVM,
        // а большой -Xms резервирует половину кучи сразу и подвешивает компьютер.
        int xms = Math.min(512, ram);
        cmd.add("-Xms" + xms + "M");
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-XX:MaxDirectMemorySize=" + (ram >= 6144 ? 2048 : 1024) + "M");
        cmd.addAll(gameMemoryFlags(rt.major()));
        // --- JVM args ---
        List<String> jvm = v.jvmArgs.isEmpty() ? defaultJvmArgs() : v.jvmArgs;
        for (int i = 0; i < jvm.size(); i++) {
            String a = jvm.get(i);
            if (a == null) continue;
            String player = account.username == null || account.username.isBlank() ? "Player" : account.username;
            String r = a.replace("${classpath}", cp == null ? "" : cp)
                    .replace("${natives_directory}", natives.toString())
                    .replace("${launcher_name}", "CWLauncher")
                    .replace("${launcher_version}", Log.VERSION)
                    .replace("${assets_root}", assetsRoot)
                    .replace("${game_dir}", gameDir.toString())
                    .replace("${auth_player_name}", player)
                    .replace("${user_properties}", "{}")
                    .replace("${clientid}", "")
                    .replace("${remoteserver}", "")
                    .replace("${version_name}", profileId == null ? "" : profileId);
            if (r.equals("${natives_directory}")) r = natives.toString();
            if (r.equals("-cp") || r.equals("-classpath")) {
                i++;
                continue;
            }
            if (r.startsWith("-Xmx") || r.startsWith("-Xms") || r.startsWith("-XX:MaxDirectMemorySize")) continue;
            cmd.add(r);
        }
        if (cfg.internetLeader) cmd.add("-Djava.net.preferIPv4Stack=true");
        cmd.add("-Dfile.encoding=UTF-8");
        if (cfg.useSystemProxy) cmd.add("-Djava.net.useSystemProxies=true");
        cmd.add("-Dorg.lwjgl.util.NoChecks=true");
        boolean temporary = account != null && account.id != null && account.id.startsWith("temp-");
        if (!temporary) {
            ru.cw.launcher.skin.PlayerSkin.refresh(account);
            Path skinFile = ru.cw.launcher.skin.PlayerSkin.file();
            if (skinFile != null) {
                byte[] packed = ru.cw.launcher.skin.PlayerSkin.gamePng(skinFile);
                boolean onEly = ru.cw.launcher.skin.SkinServer.publishToEly(
                        account, packed, ru.cw.launcher.skin.PlayerSkin.slim(skinFile));
                if (onEly) {
                    addElyAgent(cmd);
                } else {
                    try {
                        String api = ru.cw.launcher.skin.SkinServer.apiRoot(skinFile, account);
                        cmd.add(ElyAuth.agentArgument(api));
                        Log.info("В игру передан скин из папки skin");
                    } catch (IOException e) {
                        Log.warn("Локальный скин не подключён к игре: " + Log.reason(e));
                        if (account.type == Account.Type.ELY) addElyAgent(cmd);
                    }
                }
            } else if (account.type == Account.Type.ELY) {
                addElyAgent(cmd);
            }
        }
        if (!Utils.isBlank(cfg.extraJvmArgs)) {
            for (String a : cfg.extraJvmArgs.trim().split("\\s+")) {
                if (a.startsWith("-Xm")) continue;             // не ломаем базовые параметры памяти
                if (a.equals("-cp") || a.equals("-classpath")) continue;
                if (a.startsWith("-Dcw.")) cmd.add(a);          // разрешаем безопасные -Dcw.*
                else if (a.startsWith("-D") || a.startsWith("-XX") || a.startsWith("--add-")) cmd.add(a);
                else Log.warn("Пропущен небезопасный пользовательский JVM-аргумент: " + a);
            }
        }
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(v.mainClass);
        // --- game args ---
        // Флаги Mojang (--assetsDir, --assetIndex, --gameDir) идут отдельными
        // элементами списка, без подстановки внутри самого флага. Их нельзя отбрасывать:
        // без --assetIndex игра не читает индекс ресурсов и остаётся без текстур,
        // панорамы, звуков и языков.
        Map<String, String> vars = gameVars(cfg, profile, account, profileId, v, gameDir);
        List<String> game = new ArrayList<>();
        for (String a : v.gameArgs) {
            if (a == null) continue;
            String r = a;
            for (Map.Entry<String, String> e : vars.entrySet()) {
                if (e.getKey() == null) continue;
                String value = e.getValue() == null ? "" : e.getValue();
                r = r.replace(e.getKey(), value);
            }
            if (r.contains("${")) continue;
            game.add(r);
        }
        String indexName = v.assets == null ? "" : v.assets.trim();
        putFlag(game, "--gameDir", gameDir.toString());
        boolean wantsIndex = false;
        for (String a : v.gameArgs) {
            if (a != null && (a.contains("assets_index") || "--assetIndex".equals(a))) wantsIndex = true;
        }
        if (wantsIndex) {
            putFlag(game, "--assetsDir", assetsRoot);
            if (!indexName.isEmpty()) putFlag(game, "--assetIndex", indexName);
        } else if (!hasFlag(game, "--assetsDir")) {
            putFlag(game, "--assetsDir", vars.getOrDefault("${game_assets}", assetsRoot));
        }
        cmd.addAll(game);
        return cmd;
    }

    /** Добавляет пару «флаг значение», если такого флага ещё нет. */
    private static void putFlag(List<String> args, String flag, String value) {
        if (value == null || value.isBlank()) return;
        for (int i = 0; i < args.size(); i++) {
            if (!flag.equals(args.get(i))) continue;
            if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) return;
            args.add(i + 1, value);
            return;
        }
        args.add(flag);
        args.add(value);
    }

    private static boolean hasFlag(List<String> args, String flag) {
        for (String a : args) if (flag.equals(a)) return true;
        return false;
    }

    private static void addElyAgent(List<String> cmd) {
        try {
            cmd.add(ElyAuth.agentArgument());
        } catch (IOException e) {
            Log.warn("authlib-injector недоступен — игра запустится без Ely-сессий: " + Log.reason(e));
        }
    }

    /** Флаги сборщика мусора. На Java 8 только то, что она понимает: 1.0–1.16 иначе не стартуют. */
    private static List<String> gameMemoryFlags(int javaMajor) {
        if (javaMajor <= 8) {
            return List.of(
                    "-XX:+UseG1GC",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:MaxGCPauseMillis=50");
        }
        List<String> flags = new java.util.ArrayList<>(List.of(
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=200",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:+PerfDisableSharedMem",
                "-XX:MaxTenuringThreshold=1"));
        if (javaMajor >= 11) flags.add("-XX:+UseStringDeduplication");
        return flags;
    }

    private List<String> defaultJvmArgs() {
        // используется, если Mojang/Fabric не прислали свой список (older/legacy profiles)
        return new ArrayList<>(List.of("-Djava.library.path=${natives_directory}",
                "-Dminecraft.launcher.brand=${launcher_name}",
                "-Dminecraft.launcher.version=${launcher_version}",
                "-cp ${classpath}"));
    }

    private Map<String, String> gameVars(Settings cfg, LauncherProfile profile, Account account,
                                         String profileId, VersionJson v, Path gameDir) {
        Map<String, String> m = new java.util.HashMap<>();
        String player = account.username == null || account.username.isBlank() ? "Player" : account.username;
        String token = ElyAuth.token(account);
        m.put("${auth_player_name}", player);
        m.put("${version_name}", profileId == null ? "" : profileId);
        m.put("${game_directory}", gameDir.toString());
        m.put("${assets_root}", Paths.assets().toString());
        m.put("${game_assets}", legacyAssets(v).toString());
        m.put("${assets_index_name}", v.assets == null ? "" : v.assets);
        m.put("${auth_session}", token == null || token.isBlank() ? "0" : token);
        m.put("${auth_uuid}", account.shortUuid() == null ? "" : account.shortUuid());
        m.put("${auth_access_token}", token == null || token.isBlank() ? "0" : token);
        m.put("${user_type}", account.type == Account.Type.OFFLINE ? "legacy" : "mojang");
        m.put("${version_type}", profileId != null && profileId.startsWith("fabric-loader") ? "Fabric" : "release");
        m.put("${launcher_name}", "CWLauncher");
        m.put("${launcher_version}", Log.VERSION);
        m.put("${natives_directory}", gameDir.resolve("natives").toString());
        m.put("${user_properties}", "{}");
        m.put("${resolution_width}", String.valueOf(Math.max(0, cfg.screenWidth)));
        m.put("${resolution_height}", String.valueOf(Math.max(0, cfg.screenHeight)));
        m.put("${classpath}", "");
        m.put("${clientid}", "");
        m.put("${remoteserver}", "");
        m.put("${quickPlayPath}", "");
        m.put("${primaryServer}", cfg.serverAddress == null ? "" : cfg.serverAddress);
        return m;
    }

    /**
     * Версии до 1.6 ищут звуки и текстуры в обычной папке, а не в хранилище по хешам.
     * Собираем её из уже скачанного индекса, если он помечен как virtual.
     */
    private static Path legacyAssets(VersionJson v) {
        String id = v.assets == null || v.assets.isBlank() ? "legacy" : v.assets;
        Path index = Paths.assets().resolve("indexes").resolve(id + ".json");
        Path modern = Paths.assets();
        if (!Files.isRegularFile(index)) return modern;
        try {
            Map<String, Object> root = Json.parseObject(Utils.readString(index));
            boolean virtual = Json.bool(root, "virtual", false);
            if (!virtual) return modern;
            Map<String, Object> objects = Json.obj(root, "objects");
            if (objects == null || objects.isEmpty()) return modern;
            Path dest = modern.resolve("virtual").resolve(id);
            Path mark = dest.resolve(".cw-ready");
            String expect = String.valueOf(objects.size());
            if (Files.isRegularFile(mark) && expect.equals(Files.readString(mark).trim())) return dest;
            int copied = 0;
            for (Map.Entry<String, Object> e : objects.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> mm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> o = (Map<String, Object>) mm;
                String hash = Json.str(o, "hash", "");
                if (hash.length() < 5) continue;
                Path src = modern.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                if (!Files.isRegularFile(src)) continue;
                Path out = dest.resolve(e.getKey()).normalize();
                if (!out.startsWith(dest)) continue;
                if (!Files.isRegularFile(out) || Files.size(out) != Files.size(src)) {
                    Files.createDirectories(out.getParent());
                    Files.copy(src, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
            if (copied > 0) {
                Files.createDirectories(dest);
                Files.writeString(mark, expect);
                Log.info("Собраны старые ресурсы " + id + ": " + copied);
            }
            return dest;
        } catch (Exception e) {
            Log.warn("Старые ресурсы не собраны: " + Log.reason(e));
            return modern;
        }
    }

    /** Язык игры. Без этого Minecraft открывается на английском, даже если файлы языков скачаны. */
    public void applyLanguage(Settings cfg, String profileId) {
        applyLanguage(cfg, Paths.instance(profileId));
    }

    public void applyLanguage(Settings cfg, Path gameDir) {
        Path options = gameDir.resolve("options.txt");
        String lang = cfg.resolvedLanguage();
        Map<String, String> values = new java.util.LinkedHashMap<>();
        try {
            List<String> lines = Files.exists(options) ? new ArrayList<>(Files.readAllLines(options,
                    StandardCharsets.UTF_8)) : new ArrayList<>();
            for (String line : lines) {
                int i = line.indexOf(':');
                if (i > 0) values.put(line.substring(0, i), line.substring(i + 1));
            }
            values.put("lang", lang);
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, String> e : values.entrySet()) out.add(e.getKey() + ":" + e.getValue());
            Files.createDirectories(options.getParent());
            Files.writeString(options, String.join("\n", out) + "\n", StandardCharsets.UTF_8);
            Log.info("Язык Minecraft: " + lang);
        } catch (IOException e) {
            Log.warn("Не удалось записать язык: " + Log.reason(e));
        }
    }

    /** Настройки графики — запись в options.txt экземпляра (обратимо, без правок игры). */
    public void applyGraphics(LauncherProfile profile, String profileId) {
        applyGraphics(profile, Paths.instance(profileId));
    }

    public void applyGraphics(LauncherProfile profile, Path gameDir) {
        if (Utils.isBlank(profile.resolution) && !profile.fullscreen && Utils.isBlank(profile.renderDistance)
                && Utils.isBlank(profile.guiScale) && profile.fpsLimit <= 0) {
            return;
        }
        Path options = gameDir.resolve("options.txt");
        Map<String, String> values = new java.util.LinkedHashMap<>();
        try {
            List<String> lines = Files.exists(options) ? new ArrayList<>(Files.readAllLines(options,
                    StandardCharsets.UTF_8)) : new ArrayList<>();
            for (String line : lines) {
                int i = line.indexOf(':');
                if (i > 0) values.put(line.substring(0, i), line.substring(i + 1));
            }
            if (profile.fullscreen) values.put("fullscreen", "true");
            values.put("enableVsync", String.valueOf(profile.vsync));
            if (profile.fpsLimit > 0) values.put("fpsLimit", String.valueOf(
                    Math.min(260, Math.max(10, profile.fpsLimit))));
            if (!Utils.isBlank(profile.renderDistance)) {
                int d = (int) Utils.parseInt(profile.renderDistance, -1);
                if (d >= 2 && d <= 32) values.put("renderDistance", String.valueOf(d));
            }
            if (!Utils.isBlank(profile.guiScale)) {
                int g = (int) Utils.parseInt(profile.guiScale, -1);
                if (g >= 1 && g <= 4) values.put("guiScale", String.valueOf(g));
            }
            if (!Utils.isBlank(profile.resolution)) {
                String[] parts = profile.resolution.toLowerCase().split("x");
                if (parts.length == 2) {
                    int w = (int) Utils.parseInt(parts[0], 0);
                    int h = (int) Utils.parseInt(parts[1], 0);
                    if (w >= 640 && h >= 360 && w <= 7680 && h <= 4320) {
                        values.put("width", String.valueOf(w));
                        values.put("height", String.valueOf(h));
                    }
                }
            }
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, String> e : values.entrySet()) out.add(e.getKey() + ":" + e.getValue());
            Files.createDirectories(options.getParent());
            Files.writeString(options, String.join("\n", out), StandardCharsets.UTF_8);
            Log.info("Настройки графики применены к " + profile.name);
        } catch (IOException e) {
            Log.warn("Не удалось применить настройки графики: " + Log.reason(e));
        }
    }

    /** Реальный запуск игры. */
    public void markStarting(String version) {
        if (version == null || version.isBlank()) return;
        launching.add(version);
    }

    /** Неудачный запуск. Уже открытые окна этой версии не закрываются. */
    public void failLaunch(String version, Process started) {
        if (version == null) return;
        launching.remove(version);
        if (started != null) {
            List<Process> list = running.get(version);
            if (list != null) list.remove(started);
            if (started.isAlive()) {
                killTree(started);
                ru.cw.launcher.net.NetPolicy.gameClosed(started.pid());
            }
            if (process == started) process = null;
        }
        if (aliveCount(version) == 0) booted.remove(version);
    }

    public void clear(String version) {
        failLaunch(version, null);
    }

    public boolean isActive(String version) {
        if (version == null || version.isBlank()) return false;
        if (aliveCount(version) > 0) return true;
        return launching.contains(version);
    }

    public boolean isBooted(String version) {
        return version != null && booted.contains(version) && aliveCount(version) > 0;
    }

    public Operation launchOperation(Settings cfg, LauncherProfile profile, Account account, String profileId) {
        return launchOperation(cfg, profile, account, profileId, null, new Process[1], new Path[1], new Path[1]);
    }

    public Operation launchOperation(Settings cfg, LauncherProfile profile, Account account, String profileId,
                                     Path playDir, Process[] startedOut, Path[] logOut, Path[] nativesOut) {
        Path play = playDir == null ? Paths.instance(profileId) : playDir;
        Operation op = new Operation("Запуск Minecraft", state).runningAs(GameState.STARTING)
                .finishAs(GameState.STARTING);
        op.add("Проверка обязательных файлов", 1, () -> {
            try {
                VersionJson ready = mc.resolved(profileId);
                List<Downloads.Item> batch = new ArrayList<>();
                for (VersionJson.Lib lib : ready.libraries) {
                    if (!lib.download || lib.path == null) continue;
                    Path target = Paths.libs().resolve(lib.path);
                    if (Files.exists(target) && Files.size(target) > 0) continue;
                    batch.add(new Downloads.Item(lib.fullUrl(), target, lib.sha1, lib.size));
                }
                if (!batch.isEmpty()) Downloads.fetch(batch, ru.cw.launcher.model.ProgressListener.NOOP, "Библиотеки");
            } catch (Exception e) {
                Log.warn("Библиотеки перед запуском: " + Log.reason(e));
            }
            List<String> missing = missingRequired(cfg, profileId);
            if (!missing.isEmpty()) {
                throw new IOException("Не хватает файлов для запуска: "
                        + Utils.shorten(String.join(", ", missing), 200));
            }
        });
        op.add("Сессия Ely.by", 1, () -> {
            if (account.type != Account.Type.ELY) return;
            ElyAuthManager.SessionStatus session = ElyAuthManager.ensureSession(cfg, account, false);
            if (!session.ok()) {
                throw new IOException(session.message() == null
                        ? "Сессия истекла. Войдите снова через Ely.by" : session.message());
            }
        });
        op.add("Java для этой версии", 2, () -> {
            if (javaMgr.select(profile.minecraftVersion, cfg.javaPath) != null) return;
            int major = JavaManager.requiredMajor(profile.minecraftVersion);
            Log.info("Для Minecraft " + profile.minecraftVersion + " ставится Java " + major);
            for (Operation.Task task : javaMgr.installTasks(major, ru.cw.launcher.model.ProgressListener.NOOP)) {
                task.body.run();
            }
            javaMgr.detect();
            if (javaMgr.select(profile.minecraftVersion, cfg.javaPath) == null) {
                throw new IOException("Не удалось поставить Java " + major
                        + " для Minecraft " + profile.minecraftVersion);
            }
        });
        op.add("Формирование команды запуска", 1, () -> {
            Files.createDirectories(play);
            applyLanguage(cfg, play);
            applyGraphics(profile, play);
            GameTuning.applyDisplay(cfg, play.resolve("options.txt"));
            List<String> cmd = buildCommand(cfg, profile, account, profileId, play);
            if (nativesOut != null && nativesOut.length > 0) nativesOut[0] = lastNatives;
            String stamp = Long.toString(System.currentTimeMillis());
            Path log = Paths.logs().resolve("game-" + Paths.sanitize(cfg.minecraftVersion) + "-" + stamp + ".log");
            Utils.ensureDirectories(log.getParent());
            Files.writeString(log, "", StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(play.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            Process started = pb.start();
            process = started;
            if (startedOut != null && startedOut.length > 0) startedOut[0] = started;
            if (logOut != null && logOut.length > 0) logOut[0] = log;
            sessions(cfg.minecraftVersion).add(started);
            GameTuning.tune(cfg, Path.of(cmd.get(0)), started);
            ru.cw.launcher.net.NetPolicy.focusGame(started.pid());
            gameLogs.put(cfg.minecraftVersion, log);
            String safe = String.join(" ", cmd.subList(0, Math.min(cmd.size(), 6))) + " ...";
            Log.info("Команда запуска (без секретов): " + Utils.shorten(safe, 260));
            Log.info("Папка игры: " + play);
            Log.info("Minecraft запускается, лог игры: " + log);
        });
        return op;
    }

    /** Ждёт, пока окно игры появится. В обычном режиме второе окно не запускается. */
    public void watch(String version, Runnable onExit) {
        List<Process> list = version == null ? null : running.get(version);
        Process game = list == null || list.isEmpty() ? process : list.get(list.size() - 1);
        watch(version, game, gameLogs.get(version), null, onExit);
    }

    public void watch(String version, Process game, Path log, Path natives, Runnable onExit) {
        if (game == null) {
            launching.remove(version);
            if (onExit != null) onExit.run();
            return;
        }
        Path gameLog = log == null ? gameLogs.getOrDefault(version, Paths.gameLog(version)) : log;
        long pid = game.pid();
        new Thread(() -> {
            boolean[] announced = {false};
            long lastWindowCheck = 0;
            int code = 0;
            boolean gui = game.info().command().orElse("").toLowerCase(java.util.Locale.ROOT).contains("javaw");
            try {
                while (game.isAlive()) {
                    boolean ready = windowReady(gameLog);
                    long now = System.currentTimeMillis();
                    if (!ready && gui && now - lastWindowCheck > 1500) {
                        lastWindowCheck = now;
                        ready = hasMainWindow(game.pid());
                    }
                    if (!announced[0] && ready) {
                        announced[0] = true;
                        booted.add(version);
                        javax.swing.SwingUtilities.invokeLater(() ->
                                state.set(GameState.RUNNING, "Minecraft запущен"));
                    }
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (!game.isAlive()) {
                    code = game.exitValue();
                    Log.info("Minecraft завершился, код выхода " + code);
                }
            } catch (Exception e) {
                Log.warn("Наблюдение за Minecraft: " + Log.reason(e));
            } finally {
                List<Process> left = running.get(version);
                if (left != null) left.remove(game);
                deleteTree(natives);
                ru.cw.launcher.net.NetPolicy.gameClosed(pid);
                int exit = code;
                boolean showed = announced[0];
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (aliveCount(version) > 0) {
                        state.set(GameState.RUNNING, "Minecraft запущен");
                    } else {
                        launching.remove(version);
                        booted.remove(version);
                        if (!showed && exit != 0) {
                            state.set(GameState.ERROR, "Minecraft закрылся при запуске");
                        } else {
                            state.set(GameState.READY, "Minecraft закрыт");
                        }
                    }
                    if (onExit != null) onExit.run();
                });
            }
        }, "cw-game-watcher").start();
    }

    private static void deleteTree(Path dir) {
        if (dir == null) return;
        String name = dir.toString().replace('\\', '/');
        if (!name.contains("/natives/")) return;
        try {
            if (!Files.isDirectory(dir)) return;
            try (var walk = Files.walk(dir)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** java.exe открывает консоль сразу. javaw.exe держит окно только у самой игры. */
    private static String windowBinary(String javaPath) {
        if (javaPath == null) return javaPath;
        Path exe = Path.of(javaPath);
        if (exe.getFileName() != null && exe.getFileName().toString().equalsIgnoreCase("java.exe")) {
            Path javaw = exe.resolveSibling("javaw.exe");
            if (Files.isRegularFile(javaw)) return javaw.toString();
        }
        return javaPath;
    }

    /** Окно процесса уже создано. Пока его нет, кнопка остаётся «Запускается». */
    private static boolean hasMainWindow(long pid) {
        if (pid <= 0 || !Utils.isWindows()) return false;
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                    "$p = Get-Process -Id " + pid + " -ErrorAction SilentlyContinue; if ($null -eq $p) { 0 } else { $p.MainWindowHandle }")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            String digits = new String(p.getInputStream().readAllBytes()).replaceAll("[^0-9]", "");
            return !digits.isEmpty() && !digits.equals("0");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean windowReady(Path log) {
        if (log == null || !Files.isRegularFile(log)) return false;
        try {
            String text = Files.readString(log);
            return text.contains("Sound engine started")
                    || text.contains("LWJGL Version:");
        } catch (IOException e) {
            return false;
        }
    }

    public void stopGame() {
        java.util.ArrayList<Process> procs = new java.util.ArrayList<>();
        for (List<Process> list : running.values()) procs.addAll(list);
        if (process != null) procs.add(process);
        for (Process p : procs) killTree(p);
        launching.clear();
        booted.clear();
        running.clear();
        gameLogs.clear();
        process = null;
        ru.cw.launcher.net.NetPolicy.focusLauncher();
        Log.info("Minecraft закрыт");
    }

    /** Закрывает все окна одной версии. */
    public void stopVersion(String version) {
        if (version == null || version.isBlank()) {
            stopGame();
            return;
        }
        List<Process> list = running.remove(version);
        launching.remove(version);
        booted.remove(version);
        gameLogs.remove(version);
        if (list != null) {
            for (Process p : list) {
                if (process == p) process = null;
                killTree(p);
            }
        }
        Log.info("Minecraft закрыт");
    }

    private static void killTree(Process p) {
        if (p == null) return;
        try {
            p.descendants().forEach(handle -> {
                try {
                    handle.destroy();
                } catch (Exception ignored) {
                }
            });
            if (p.isAlive()) p.destroy();
            if (p.isAlive() && !p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                p.descendants().forEach(handle -> {
                    try {
                        handle.destroyForcibly();
                    } catch (Exception ignored) {
                    }
                });
                p.destroyForcibly();
                p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    /** Отображаемая команда запуска без секретов (Developer mode). */
    public String describeCommandSafe(Settings cfg, LauncherProfile profile, Account account, String profileId) {
        try {
            List<String> cmd = buildCommand(cfg, profile, account, profileId);
            List<String> safe = new ArrayList<>();
            boolean maskNext = false;
            boolean maskToken = false;
            for (String s : cmd) {
                if (maskToken) {
                    safe.add("<token>");
                    maskToken = false;
                    continue;
                }
                if (maskNext) {
                    safe.add("<classpath>");
                    maskNext = false;
                    continue;
                }
                if ("--accessToken".equals(s)) {
                    safe.add(s);
                    maskToken = true;
                    continue;
                }
                if (s.equals("-cp") || s.equals("-classpath")) {
                    safe.add(s);
                    maskNext = true;
                    continue;
                }
                if (s.startsWith("-Xmx") || s.startsWith("-Xms")) safe.add(s);
                else if (s.startsWith("-javaagent")) safe.add("<authlib-injector>");
                else safe.add(s);
            }
            return String.join(" ", safe);
        } catch (Exception e) {
            return "Команда недоступна: " + Log.reason(e);
        }
    }
}
