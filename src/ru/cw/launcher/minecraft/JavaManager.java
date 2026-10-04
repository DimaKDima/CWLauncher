package ru.cw.launcher.minecraft;

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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic Java Manager: обнаружение Java, определение версии, проверка совместимости
 * с Minecraft, автоматический выбор и (при необходимости) установка из официального
 * источника Eclipse Adoptium (Temurin) с проверкой SHA-256.
 */
public final class JavaManager {

    public record Runtime(String path, int major, String vendor, boolean works) {
        public String label() {
            return "Java " + major + " — " + Utils.shorten(path, 60);
        }
    }

    private static final Pattern MAJOR = Pattern.compile("version \"?(\\d+)(?:\\.(\\d+))?");
    private final List<Runtime> found = new ArrayList<>();
    private volatile Runtime selected;

    public List<Runtime> discovered() {
        return List.copyOf(found);
    }

    /** Версия Java, на которой эта сборка Minecraft реально стартует. */
    public static int requiredMajor(String mcVersion) {
        String v = Utils.normalizeVersion(mcVersion);
        String[] p = v.split("\\.");
        int minor = p.length > 1 ? (int) Utils.parseLong(p[1], 0) : 0;
        int patch = p.length > 2 ? (int) Utils.parseLong(p[2], 0) : 0;
        if (minor > 20 || (minor == 20 && patch >= 5)) return 21;
        if (minor >= 18) return 17;
        if (minor == 17) return 16;
        return 8;
    }

    /** Java 17 не запускает 1.0–1.16. Для старых версий нужна именно Java 8. */
    public static boolean accepts(int have, int need) {
        if (have <= 0) return false;
        if (need <= 8) return have == 8;
        if (need == 16) return have == 16 || have == 17;
        if (need >= 21) return have >= 21;
        return have == need;
    }

    /** Поиск Java в системе и в каталоге лаунчера. */
    public List<Runtime> detect() {
        found.clear();
        Set<Path> candidates = new LinkedHashSet<>();
        addEnv(candidates, "JAVA_HOME");
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                try {
                    Path p = Path.of(entry, "java.exe");
                    if (Files.isRegularFile(p)) candidates.add(p);
                    Path p2 = Path.of(entry, "java");
                    if (Files.isRegularFile(p2)) candidates.add(p2);
                } catch (Exception ignored) {
                }
            }
        }
        for (String root : List.of(
                System.getenv("ProgramFiles") == null ? "C:\\Program Files" : System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)") == null ? "C:\\Program Files (x86)"
                        : System.getenv("ProgramFiles(x86)"),
                System.getenv("LOCALAPPDATA") == null ? "" : System.getenv("LOCALAPPDATA"))) {
            if (root == null || root.isBlank()) continue;
            Path base = Path.of(root);
            if (!Files.isDirectory(base)) continue;
            for (String dir : new String[]{"Java", "Eclipse Adoptium", "Microsoft", "Adoptium", "Zulu",
                    "Semeru", "BellSoft", "JavaSoft"}) {
                Path sub = base.resolve(dir);
                if (!Files.isDirectory(sub)) continue;
                try (var s = Files.list(sub)) {
                    for (Path p : s.filter(Files::isDirectory).toList()) {
                        Path exe = p.resolve("bin").resolve(Http.isWindows() ? "java.exe" : "java");
                        if (Files.isRegularFile(exe)) candidates.add(exe);
                    }
                } catch (IOException ignored) {
                }
            }
        }
        // Java, вшитая рядом с CWLauncher.exe: FullLauncherCW\jre\17
        Path shipped = Paths.installRoot().resolve("jre");
        if (Files.isDirectory(shipped)) {
            try (var s = Files.list(shipped)) {
                for (Path p : s.filter(Files::isDirectory).toList()) {
                    Path exe = findJavaExe(p);
                    if (exe != null) candidates.add(exe);
                }
            } catch (IOException ignored) {
            }
        }
        // портативные JRE, скачанные в профиль пользователя
        if (Files.isDirectory(Paths.javaDir())) {
            try (var s = Files.list(Paths.javaDir())) {
                for (Path p : s.filter(Files::isDirectory).toList()) {
                    Path exe = p.resolve("bin").resolve(Http.isWindows() ? "java.exe" : "java");
                    if (Files.isRegularFile(exe)) candidates.add(exe);
                }
            } catch (IOException ignored) {
            }
        }
        // runtime из официального Minecraft Launcher
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            Path mcRuntime = Path.of(appdata, ".minecraft", "runtime");
            collectRuntimeDirs(mcRuntime, candidates);
        }
        if (System.getenv("ProgramFiles") != null) {
            collectRuntimeDirs(Path.of(System.getenv("ProgramFiles"), "Common Files", "Oracle", "Java"),
                    candidates);
        }
        for (Path exe : candidates) {
            Runtime rt = probe(exe);
            if (rt != null && rt.works) found.add(rt);
        }
        found.sort(Comparator.comparingInt((Runtime r) -> r.major).reversed());
        for (Runtime r : found) Log.info("Найдена Java: " + r.label());
        if (found.isEmpty()) Log.warn("Java в системе не обнаружена");
        return discovered();
    }

    private void collectRuntimeDirs(Path dir, Set<Path> out) {
        if (!Files.isDirectory(dir)) return;
        try (var s = Files.walk(dir, 4)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (name.equals("java.exe") || name.equals("java")) out.add(p);
            }
        } catch (IOException ignored) {
        }
    }

    private void addEnv(Set<Path> out, String var) {
        String value = System.getenv(var);
        if (value == null || value.isBlank()) return;
        Path exe = Path.of(value, "bin", Http.isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(exe)) out.add(exe);
    }

    /** Реальный запуск `java -version` и разбор вывода. */
    public Runtime probe(Path javaExe) {
        try {
            Process p = new ProcessBuilder(javaExe.toString(), "-version")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Runtime(javaExe.toString(), -1, "?", false);
            }
            Matcher m = MAJOR.matcher(out);
            if (!m.find()) return new Runtime(javaExe.toString(), -1, "?", false);
            int major = Integer.parseInt(m.group(1));
            if (major == 1 && m.group(2) != null) major = Integer.parseInt(m.group(2)); // "1.8.0"
            String vendor = out.lines().findFirst().orElse("");
            return new Runtime(javaExe.toString(), major, vendor, p.exitValue() == 0);
        } catch (Exception e) {
            Log.debug("Java " + javaExe + " не проверена: " + Log.reason(e));
            return new Runtime(javaExe.toString(), -1, "?", false);
        }
    }

    /**
     * Выбор Java для версии Minecraft: точное совпадение мажорной версии предпочтительно,
     * иначе минимальная подходящая (>= требуемой).
     */
    public Runtime select(String mcVersion, String configuredPath) {
        int need = requiredMajor(mcVersion);
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path exe = Path.of(configuredPath);
            if (!Files.isRegularFile(exe)) {
                Path home = exe;
                exe = home.resolve("bin").resolve(Http.isWindows() ? "java.exe" : "java");
            }
            Runtime rt = probe(exe);
            if (rt != null && rt.works && accepts(rt.major, need)) {
                selected = rt;
                return rt;
            }
            Log.warn("Указанная Java не подходит для Minecraft " + mcVersion + ": " + configuredPath);
        }
        Runtime shipped = bundledRuntime(need);
        if (shipped != null) {
            selected = shipped;
            Log.info("Вшитая Java " + shipped.major + " для Minecraft " + mcVersion + ": " + shipped.path);
            return shipped;
        }
        if (found.isEmpty()) detect();
        Runtime best = null;
        for (Runtime r : found) {
            if (!accepts(r.major, need)) continue;
            if (best == null || betterJava(r, best, need)) best = r;
        }
        selected = best;
        if (best != null) {
            Log.info("Выбрана Java " + best.major + " для Minecraft " + mcVersion + " (требуется " + need + ")");
        } else {
            Log.warn("Подходящая Java не найдена: для Minecraft " + mcVersion + " нужна Java " + need);
        }
        return best;
    }

    /** JRE из папки jre рядом с лаунчером. Её хватает, даже если на компьютере Java нет. */
    private Runtime bundledRuntime(int need) {
        Path root = Paths.installRoot().resolve("jre");
        if (!Files.isDirectory(root)) return null;
        Runtime best = null;
        try (var s = Files.list(root)) {
            for (Path dir : s.filter(Files::isDirectory).toList()) {
                Path exe = findJavaExe(dir);
                if (exe == null) continue;
                Runtime rt = probe(exe);
                if (rt == null || !rt.works || !accepts(rt.major, need)) continue;
                if (best == null || betterJava(rt, best, need)) best = rt;
            }
        } catch (IOException ignored) {
        }
        return best;
    }

    private static boolean betterJava(Runtime candidate, Runtime current, int need) {
        if (candidate.major == need && current.major != need) return true;
        if (current.major == need) return false;
        return candidate.major < current.major;
    }

    public Runtime selected() {
        return selected;
    }

    public String describe() {
        Runtime r = selected;
        if (r == null) return "Java не выбрана";
        return "Java " + r.major + " (" + Utils.shorten(r.path, 70) + ")";
    }

    /** Пусто, если подходящей Java нет — UI тогда показывает кнопку установки. */
    public List<Operation.Task> installTasks(int major, ProgressListener pl) {
        List<Operation.Task> tasks = new ArrayList<>();
        Path dest = Paths.javaDir().resolve("temurin-" + major + "-jre");
        Adoptium[] found = new Adoptium[1];
        tasks.add(new Operation.Task("Поиск Java " + major + " (Eclipse Adoptium)", 1, () -> {
            found[0] = findAdoptium(major);
            pl.progress(1, 1, "Найдена сборка Java " + major);
        }));
        tasks.add(new Operation.Task("Загрузка Java " + major, 8, () -> {
            if (found[0] == null) found[0] = findAdoptium(major);
            String link = found[0].link;
            String sha = found[0].sha;
            Path zip = Paths.tmp().resolve("temurin-" + major + ".zip");
            Utils.ensureDirectories(zip.getParent());
            Http.download(link, zip, null, 0, (done, total) -> pl.progress(done, total,
                    "Загрузка Java " + Utils.humanSize(done)), null);
            verifySha256(zip, sha);
            pl.stage("Распаковка Java " + major);
            Utils.extractZip(zip, dest);
            Utils.deleteQuietly(zip);
            Path exe = findJavaExe(dest);
            if (exe == null) throw new IOException("После распаковки не найден java.exe");
            Runtime rt = probe(exe);
            if (rt == null || !rt.works) throw new IOException("Установленная Java не запускается");
            Log.info("Установлена Java " + rt.major + ": " + exe);
        }));
        return tasks;
    }

    /** vendor=eclipse. Старый vendor=eclipse_adoptium у Adoptium даёт 404, и Java 8 не скачивается. */
    private Adoptium findAdoptium(int major) throws Exception {
        for (String type : new String[]{"jre", "jdk"}) {
            String url = "https://api.adoptium.net/v3/assets/latest/" + major + "/hotspot"
                    + "?architecture=x64&image_type=" + type + "&os=windows&vendor=eclipse";
            try {
                String meta = Http.get(url, "adoptium-" + major + "-" + type).body();
                List<Object> arr = Json.parseArray(meta);
                if (arr.isEmpty()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> first = (Map<String, Object>) arr.get(0);
                Map<String, Object> pkg = Json.obj(Json.obj(first, "binary"), "package");
                String link = pkg == null ? "" : Json.str(pkg, "link", "");
                if (link.isBlank()) continue;
                Log.info("Java " + major + " " + type + ": " + Json.str(first, "release_name", link));
                return new Adoptium(link, Json.str(pkg, "checksum", ""));
            } catch (java.io.IOException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (!msg.contains("404")) throw e;
                Log.warn("Adoptium не отдал " + type + " Java " + major);
            }
        }
        String direct = "https://api.adoptium.net/v3/binary/latest/" + major
                + "/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk";
        Log.warn("Беру прямую ссылку Adoptium: " + direct);
        return new Adoptium(direct, "");
    }

    private record Adoptium(String link, String sha) {
    }

    private void verifySha256(Path file, String expected) throws IOException {
        if (expected == null || expected.isBlank()) {
            Log.warn("Adoptium не прислал SHA-256 — проверка невозможна");
            return;
        }
        String actual = Utils.sha256(file);
        if (!actual.equalsIgnoreCase(expected)) {
            Utils.deleteQuietly(file);
            throw new IOException("Контрольная сумма Java не совпала — файл удалён");
        }
        Log.info("SHA-256 Java подтверждён");
    }

    public static Path findJavaExe(Path home) {
        Path direct = home.resolve("bin").resolve(Http.isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(direct)) return direct;
        try (var s = Files.walk(home, 3)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                if ((n.equals("java.exe") || n.equals("java")) && p.getParent() != null
                        && p.getParent().getFileName().toString().equals("bin")) {
                    return p;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public Path javaHome() {
        Runtime r = selected;
        if (r == null) return null;
        Path exe = Path.of(r.path);
        return exe.getParent() == null ? null : exe.getParent().getParent();
    }
}
