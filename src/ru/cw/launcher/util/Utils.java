package ru.cw.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/** Разные утилиты: хеши, размеры, версии, строки. */
public final class Utils {

    private Utils() {
    }

    public static String sha1(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            }
            return hex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 недоступен", e);
        }
    }

    public static String sha1(InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            return hex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 недоступен", e);
        }
    }

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    /** SHA-1 от произвольных байт (для ключей кэша, без IOException). */
    public static String sha1Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    public static String sha1Hex(String text) {
        return sha1Hex(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static volatile long physicalMemoryCache = Long.MIN_VALUE;

    /** Объём оперативной памяти компьютера в мегабайтах, не куча самого лаунчера. */
    public static long physicalMemoryMb() {
        long cached = physicalMemoryCache;
        if (cached != Long.MIN_VALUE) return cached;
        long mb = readPhysicalMemoryMb();
        physicalMemoryCache = mb;
        return mb;
    }

    private static long readPhysicalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            // getTotalMemorySize есть, но из лаунчера его нельзя вызвать.
            // getTotalPhysicalMemorySize на этой Java как раз отдаёт всю память компьютера.
            for (String name : new String[]{"getTotalPhysicalMemorySize", "getTotalMemorySize"}) {
                try {
                    java.lang.reflect.Method m = bean.getClass().getMethod(name);
                    Object v = m.invoke(bean);
                    if (v instanceof Number n && n.longValue() > 0) return n.longValue() / (1024 * 1024);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static long freeMemoryMb() {
        Runtime r = Runtime.getRuntime();
        return (r.maxMemory() - (r.totalMemory() - r.freeMemory())) / (1024 * 1024);
    }

    public static long maxMemoryMb() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    public static long usedMemoryMb() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
    }

    public static int cpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static String osCaption() {
        return System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "")
                + " (" + System.getProperty("os.arch", "") + ")";
    }

    public static void ensureDirectories(Path dir) throws IOException {
        if (dir != null) Files.createDirectories(dir);
    }

    /** Атомарная замена файла (с fallback для сетевых дисков). */
    public static void replaceFile(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public static String humanSize(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " Б";
        double v = bytes;
        String[] u = {"КБ", "МБ", "ГБ", "ТБ"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }

    /** "1.4.2" -> массив чисел; сравнение версий. */
    public static int compareVersions(String a, String b) {
        String[] pa = normalizeVersion(a).split("\\.");
        String[] pb = normalizeVersion(b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            long x = i < pa.length ? parseLong(pa[i], 0) : 0;
            long y = i < pb.length ? parseLong(pb[i], 0) : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    public static String normalizeVersion(String v) {
        if (v == null) return "0";
        String s = v.trim().replaceAll("^v", "");
        s = s.replaceAll("[^0-9.]", "");
        if (s.isEmpty()) return "0";
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "0" : s;
    }

    public static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static String trimTrailingSlash(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    public static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
        return a.contains("64") ? "x64" : "x86";
    }

    public static String javaBinName() {
        return isWindows() ? "java.exe" : "java";
    }

    public static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String fileNameOf(String pathOrUrl) {
        String s = pathOrUrl;
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.flush();
    }

    public static void deleteRecursive(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> a.getNameCount() - b.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            Log.warn("Не удалось удалить " + root + ": " + e.getMessage());
        }
    }

    public static String readString(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    public static void writeString(Path p, String s) throws IOException {
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }

    /** Можно ли создавать файлы в папке. Program Files без прав администратора — нельзя. */
    public static boolean writable(Path dir) {
        if (dir == null) return false;
        Path probe = dir.resolve(".cw-write-test");
        try {
            Files.createDirectories(dir);
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(probe);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    public static String writeDeniedMessage(Path dir) {
        String where = dir == null ? "выбранную папку" : dir.toString();
        return "Нет прав записать игру в " + where
                + ". Папка внутри Program Files закрыта. Нажмите «Установить» и выберите другой диск.";
    }

    /** Чтение текстового файла из ресурсов приложения (classpath). */
    public static String readResource(String path) {
        try (InputStream in = Utils.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static String firstNonEmpty(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return "";
    }

    public static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    /** Удаляет файл или папку вместе с содержимым. */
    public static void deleteTree(Path p) {
        if (p == null || !Files.exists(p)) return;
        try {
            if (Files.isDirectory(p)) {
                try (var children = Files.list(p)) {
                    for (Path child : children.toList()) deleteTree(child);
                }
            }
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    public static long size(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.size(p) : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            }
            return hex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 недоступен", e);
        }
    }

    /**
     * Распаковка ZIP с защитой от ZIP Slip (path traversal).
     * Все пути нормализуются и проверяются на выход за target.
     *
     * @return количество извлечённых файлов
     */
    public static int extractZip(Path zip, Path targetDir) throws IOException {
        java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile());
        int count = 0;
        try {
            Path base = targetDir.toAbsolutePath().normalize();
            Files.createDirectories(base);
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry e = entries.nextElement();
                String name = e.getName().replace((char) 92, '/');
                if (name.startsWith("/") || name.contains(":")) {
                    throw new IOException("Недопустимый путь в архиве: " + name);
                }
                Path out = base.resolve(name).normalize();
                if (!out.startsWith(base)) {
                    throw new IOException("ZIP Slip: элемент выходит за каталог распаковки: " + name);
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
        } finally {
            zf.close();
        }
        return count;
    }

    /** Является ли файл ZIP-архивом (по сигнатуре PK\x03\x04). */
    public static boolean looksLikeZip(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = new byte[4];
            int r = in.read(head);
            if (r < 4) return false;
            return head[0] == 0x50 && head[1] == 0x4B
                    && (head[2] == 0x03 || head[2] == 0x05 || head[2] == 0x07)
                    && (head[3] == 0x04 || head[3] == 0x06 || head[3] == 0x08);
        } catch (IOException e) {
            return false;
        }
    }

    public static long dirSize(Path dir) {
        if (dir == null || !Files.exists(dir)) return 0;
        try (var walk = Files.walk(dir)) {
            long[] sum = {0};
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    sum[0] += Files.size(p);
                } catch (IOException ignored) {
                }
            });
            return sum[0];
        } catch (IOException e) {
            return 0;
        }
    }

    /** «5 мин назад» и т.п. — для отображения времени последней проверки. */
    public static String timeAgo(long epochMs) {
        if (epochMs <= 0) return "никогда";
        long s = (System.currentTimeMillis() - epochMs) / 1000;
        if (s < 5) return "только что";
        if (s < 60) return s + " с назад";
        if (s < 3600) return (s / 60) + " мин назад";
        if (s < 86400) return (s / 3600) + " ч назад";
        return (s / 86400) + " дн назад";
    }

    /** Открыть ссылку в системном браузере (без жёстко прописанных путей). */
    public static boolean openUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url}).exitValue() == 0;
            }
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            return true;
        } catch (Throwable t) {
            try { return Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url}).waitFor() == 0; } catch (Throwable t2) { return false; }
        }
    }

    /**
     * Стандартный выбор папки Windows. Окно остаётся, пока пользователь не ответит.
     * null — «Отмена».
     */
    public static Path chooseDirectory(java.awt.Component parent, String title) {
        String caption = title == null || title.isBlank() ? "Выберите папку" : title;
        if (!isWindows()) return null;
        try {
            Log.info("Открываю выбор папки");
            return windowsFolder(caption, windowHandle(parent));
        } catch (Exception e) {
            Log.warn("Проводник не открылся: " + Log.reason(e));
            return null;
        }
    }

    /** Подпись кнопки на панели задач: только CWLauncher. */
    public static void nameOnTaskbar(java.awt.Window window) {
        if (window == null || !isWindows()) return;
        if (window instanceof java.awt.Frame frame) frame.setTitle("CWLauncher");
        else if (window instanceof java.awt.Dialog dialog) dialog.setTitle("CWLauncher");
        long hwnd = windowHandle(window);
        if (hwnd == 0) return;
        Thread worker = new Thread(() -> {
            try {
                applyTaskbarName(hwnd);
            } catch (Exception e) {
                Log.warn("Имя панели задач: " + Log.reason(e));
            }
        }, "cw-taskbar-name");
        worker.setDaemon(true);
        worker.start();
    }

    private static long windowHandle(java.awt.Component component) {
        java.awt.Window window = component instanceof java.awt.Window w
                ? w : javax.swing.SwingUtilities.getWindowAncestor(component);
        if (window == null) return 0;
        try {
            java.lang.reflect.Field peerField = java.awt.Component.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            Object peer = peerField.get(window);
            if (peer == null) {
                window.addNotify();
                peer = peerField.get(window);
            }
            if (peer == null) return 0;
            java.lang.reflect.Field hwndField = peer.getClass().getDeclaredField("hwnd");
            hwndField.setAccessible(true);
            return hwndField.getLong(peer);
        } catch (Exception e) {
            Log.warn("HWND: " + Log.reason(e));
            return 0;
        }
    }

    private static void applyTaskbarName(long hwnd) throws IOException, InterruptedException {
        Path script = Files.createTempFile("cw-taskbar-", ".ps1");
        String ps = """
                $ErrorActionPreference = 'Stop'
                Add-Type -TypeDefinition @'
                using System;
                using System.Runtime.InteropServices;
                [StructLayout(LayoutKind.Sequential, Pack = 4)]
                public struct CwKey { public Guid fmtid; public uint pid; }
                [StructLayout(LayoutKind.Explicit)]
                public struct CwVar {
                    [FieldOffset(0)] public ushort vt;
                    [FieldOffset(8)] public IntPtr pointer;
                }
                [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                public interface CwStore {
                    uint GetCount(out uint c);
                    uint GetAt(uint i, out CwKey key);
                    uint GetValue(ref CwKey key, out CwVar value);
                    uint SetValue(ref CwKey key, ref CwVar value);
                    uint Commit();
                }
                public static class CwTaskbar {
                    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
                    public static extern bool SetWindowText(IntPtr hwnd, string text);
                    [DllImport("shell32.dll")]
                    public static extern int SHGetPropertyStoreForWindow(IntPtr hwnd, ref Guid iid, out CwStore store);
                    [DllImport("ole32.dll")]
                    public static extern int PropVariantClear(ref CwVar value);
                    public static void Apply(long hwnd) {
                        IntPtr handle = new IntPtr(hwnd);
                        SetWindowText(handle, "CWLauncher");
                        Guid iid = new Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99");
                        CwStore store;
                        if (SHGetPropertyStoreForWindow(handle, ref iid, out store) != 0 || store == null) return;
                        SetText(store, 5, "CWLauncher");
                        SetText(store, 4, "CWLauncher");
                        store.Commit();
                    }
                    static void SetText(CwStore store, uint pid, string text) {
                        CwKey key = new CwKey();
                        key.fmtid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");
                        key.pid = pid;
                        CwVar value = new CwVar();
                        value.vt = 31;
                        value.pointer = Marshal.StringToCoTaskMemUni(text);
                        try { store.SetValue(ref key, ref value); }
                        finally { PropVariantClear(ref value); }
                    }
                }
                '@
                [CwTaskbar]::Apply([int64]$env:CW_TASKBAR_HWND)
                """;
        runPowershell(script, ps, java.util.Map.of("CW_TASKBAR_HWND", Long.toString(hwnd)));
    }

    private static Path windowsFolder(String title, long hwnd) throws IOException, InterruptedException {
        Path script = Files.createTempFile("cw-folder-", ".ps1");
        String ps = """
                $ErrorActionPreference = 'Stop'
                $shell = New-Object -ComObject Shell.Application
                $folder = $shell.BrowseForFolder(0, $env:CW_FOLDER_TITLE, 0x41, 0)
                if ($null -eq $folder) { Write-Output 'CANCEL'; exit 0 }
                $path = $folder.Self.Path
                if ([string]::IsNullOrWhiteSpace($path)) { Write-Output 'CANCEL' } else { Write-Output $path }
                """;
        String out = runPowershell(script, ps, java.util.Map.of(
                "CW_FOLDER_TITLE", title,
                "CW_FOLDER_HWND", Long.toString(hwnd)));
        if (out.isBlank() || out.equals("CANCEL") || out.endsWith("CANCEL")) return null;
        String line = out;
        int nl = Math.max(out.lastIndexOf('\n'), out.lastIndexOf('\r'));
        if (nl >= 0 && nl + 1 < out.length()) line = out.substring(nl + 1).trim();
        Path path = Path.of(line);
        return Files.isDirectory(path) ? path : null;
    }

    private static String runPowershell(Path script, String body, java.util.Map<String, String> env)
            throws IOException, InterruptedException {
        Files.writeString(script, body, java.nio.charset.StandardCharsets.UTF_8);
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-ExecutionPolicy", "Bypass",
                    "-File", script.toString());
            if (env != null) builder.environment().putAll(env);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String out = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .trim();
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("окно не ответило");
            }
            if (process.exitValue() != 0) throw new IOException(out.isBlank() ? "команда завершилась с ошибкой" : out);
            return out;
        } finally {
            Files.deleteIfExists(script);
        }
    }

    /** Открывает папку в проводнике Windows (explorer.exe), создавая при необходимости. */
    public static boolean openFolder(Path dir) {
        if (dir == null) return false;
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        try {
            if (isWindows()) {
                new ProcessBuilder("explorer.exe", dir.toAbsolutePath().toString()).start();
                return true;
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
                return true;
            }
            return new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString()).start().waitFor() == 0;
        } catch (Exception e) {
            ru.cw.launcher.util.Log.warn("Не удалось открыть папку " + dir + ": "
                    + ru.cw.launcher.util.Log.reason(e));
            return false;
        }
    }

    public static void clearDir(Path dir) {        if (dir == null || !Files.isDirectory(dir)) return;
        try (var s = Files.list(dir)) {
            for (Path p : s.toList()) deleteRecursive(p);
        } catch (IOException e) {
            Log.warn("Не удалось очистить " + dir + ": " + Log.reason(e));
        }
    }
}
