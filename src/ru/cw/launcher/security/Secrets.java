package ru.cw.launcher.security;

import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище секретов (токены Ely.by и пр.). Пароли не хранятся никогда.
 *
 * Windows: используется штатный DPAPI (CryptProtectData) через powershell.exe —
 * сторонние библиотеки не нужны, ключ привязан к учётной записи Windows.
 * Если DPAPI недоступен (не-Windows/ограничения), секреты не сохраняются на диск,
 * а живут только в памяти процесса — это безопаснее, чем plaintext.
 */
public final class Secrets {

    private static final Path FILE = Paths.secretsFile();
    private static final Map<String, String> memory = new ConcurrentHashMap<>();
    private static volatile boolean dpapiAvailable = true;
    private static volatile boolean loaded;

    private Secrets() {
    }

    public static synchronized String get(String key) {
        loadIfNeeded();
        return memory.get(key);
    }

    public static synchronized void put(String key, String value) {
        loadIfNeeded();
        if (value == null || value.isEmpty()) {
            memory.remove(key);
        } else {
            memory.put(key, value);
        }
        persist();
    }

    public static synchronized void remove(String key) {
        put(key, null);
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            String blob = Files.readString(FILE, StandardCharsets.US_ASCII).trim();
            if (blob.isEmpty()) return;
            String plain = dpapiUnprotect(blob);
            if (plain == null) {
                Log.warn("Не удалось расшифровать secrets.dpx — секреты будут только в памяти");
                Files.deleteIfExists(FILE);
                return;
            }
            for (String line : plain.split("\n")) {
                int i = line.indexOf('=');
                if (i > 0) memory.put(line.substring(0, i), line.substring(i + 1));
            }
            Log.info("Загружено секретов: " + memory.size());
        } catch (IOException e) {
            Log.warn("Secrets: " + Log.reason(e));
        }
    }

    private static void persist() {
        if (!dpapiAvailable) return;
        try {
            if (memory.isEmpty()) {
                Files.deleteIfExists(FILE);
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : memory.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            String blob = dpapiProtect(sb.toString());
            if (blob == null) {
                dpapiAvailable = false;
                Log.warn("DPAPI недоступен: секреты сохраняются только в памяти процесса");
                return;
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, blob, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            Log.warn("Не удалось сохранить секреты: " + Log.reason(e));
        }
    }

    private static String dpapiProtect(String plain) {
        String script = "$s=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($s);"
                + "Add-Type -AssemblyName System.Security;"
                + "$p=[Security.Cryptography.ProtectedData]::Protect($b,$null,'CurrentUser');"
                + "[Convert]::ToBase64String($p)";
        return run(script, plain);
    }

    private static String dpapiUnprotect(String blob) {
        String script = "$s=[Console]::In.ReadToEnd().Trim();$b=[Convert]::FromBase64String($s);"
                + "Add-Type -AssemblyName System.Security;"
                + "[Text.Encoding]::UTF8.GetString("
                + "[Security.Cryptography.ProtectedData]::Unprotect($b,$null,'CurrentUser'))";
        return run(script, blob);
    }

    private static String run(String script, String stdin) {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            try (var out = p.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            String res = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) return null;
            return res.replace("\r", "").trim();
        } catch (Exception e) {
            Log.debug("DPAPI вызов неудачен: " + Log.reason(e));
            return null;
        }
    }

    /** Маскирование значений для логов и отчётов. */
    public static String redact(String text) {        if (text == null) return null;
        String out = text;
        for (String key : new LinkedHashMap<String, String>(memory).keySet()) {
            String val = memory.get(key);
            if (val != null && val.length() >= 6) out = out.replace(val, "***");
        }
        out = out.replaceAll("(?i)(password|pass|token|accessToken|refreshToken|clientToken|secret|authorization|bearer)"
                + "\\s*[=:]\\s*\\S+", "$1=***");
        return out;
    }

    public static int size() {
        return memory.size();
    }

    public static boolean isDpapiAvailable() {
        return dpapiAvailable;
    }
}
