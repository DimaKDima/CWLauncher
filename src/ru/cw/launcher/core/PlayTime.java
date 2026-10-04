package ru.cw.launcher.core;

import ru.cw.launcher.ui.Lang;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Общее время: лаунчер открыт или Minecraft ещё запущен после закрытия лаунчера.
 * Секунды копятся в playtime.json. Пока игра жива без лаунчера, их дописывает фоновый скрипт.
 */
public final class PlayTime {

    private static final Object LOCK = new Object();
    private static final AtomicBoolean CLOSED = new AtomicBoolean();
    private static volatile long bank;
    private static volatile long since;
    private static volatile long until;
    private static volatile String pids = "";
    private static volatile java.util.function.Supplier<List<Long>> games = List::of;
    private static volatile boolean started;
    private static Thread pulse;

    private PlayTime() {
    }

    public static void watch(java.util.function.Supplier<List<Long>> running) {
        if (running != null) games = running;
    }

    public static void begin() {
        synchronized (LOCK) {
            if (started) return;
            started = true;
            CLOSED.set(false);
            load();
            long now = System.currentTimeMillis();
            if (since > 0 && !anyAlive(pids)) {
                long end = until > since ? until : now;
                bank += Math.max(0, (end - since) / 1000L);
                since = 0;
                until = 0;
                pids = "";
            }
            if (since <= 0) since = now;
            until = now;
            save();
            pulse = new Thread(PlayTime::beat, "cw-playtime");
            pulse.setDaemon(true);
            pulse.start();
            Runtime.getRuntime().addShutdownHook(new Thread(PlayTime::end, "cw-playtime-end"));
        }
    }

    public static long shownSeconds() {
        synchronized (LOCK) {
            if (since <= 0) return Math.max(0, bank);
            long now = System.currentTimeMillis();
            return Math.max(0, bank + Math.max(0, (now - since) / 1000L));
        }
    }

    public static void end() {
        List<Long> live;
        synchronized (LOCK) {
            if (!started || !CLOSED.compareAndSet(false, true)) return;
            live = games.get();
            long now = System.currentTimeMillis();
            if (live == null || live.isEmpty()) {
                if (since > 0) bank += Math.max(0, (now - since) / 1000L);
                since = 0;
                until = 0;
                pids = "";
                save();
                return;
            }
            until = now;
            pids = join(live);
            save();
        }
        spawnWatcher();
    }

    /** Две старшие единицы. Мелкие отсекаются выбранной точностью. */
    public static String format(long totalSeconds, String unit) {
        long sec = Math.max(0, totalSeconds);
        long[] div = {7L * 24 * 3600, 24L * 3600, 3600, 60, 1};
        int finest = switch (unit == null ? "sec" : unit) {
            case "week" -> 0;
            case "day" -> 1;
            case "hour" -> 2;
            case "min" -> 3;
            default -> 4;
        };
        long[] vals = new long[5];
        long rest = sec;
        for (int i = 0; i < 5; i++) {
            vals[i] = rest / div[i];
            rest %= div[i];
        }
        List<Integer> show = new ArrayList<>();
        for (int i = 0; i <= finest; i++) {
            if (vals[i] > 0) show.add(i);
        }
        if (show.isEmpty()) {
            for (int i = finest + 1; i < 5; i++) {
                if (vals[i] > 0) {
                    show.add(i);
                    break;
                }
            }
        }
        if (show.isEmpty()) show.add(finest);
        if (show.size() > 2) show = show.subList(0, 2);
        StringBuilder out = new StringBuilder();
        for (int index : show) {
            long n = vals[index];
            if (out.length() > 0) out.append(' ');
            out.append(n).append(' ').append(word(n, index));
        }
        return out.toString();
    }

    private static void beat() {
        while (!CLOSED.get()) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (LOCK) {
                if (CLOSED.get() || since <= 0) return;
                until = System.currentTimeMillis();
                List<Long> live = games.get();
                pids = live == null ? "" : join(live);
                save();
            }
        }
    }

    private static void spawnWatcher() {
        if (!ru.cw.launcher.util.Utils.isWindows()) return;
        try {
            Path script = Files.createTempFile("cw-play-", ".ps1");
            script.toFile().deleteOnExit();
            Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-File", script.toString(), file().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Log.info("Время продолжается, пока открыт Minecraft");
        } catch (Exception e) {
            Log.warn("Счётчик времени не оставлен с игрой: " + Log.reason(e));
        }
    }

    private static boolean anyAlive(String raw) {
        if (raw == null || raw.isBlank()) return false;
        for (String part : raw.split(",")) {
            try {
                long pid = Long.parseLong(part.trim());
                if (pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static String join(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null || id <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private static Path file() {
        return Paths.root().resolve("playtime.json");
    }

    private static void load() {
        bank = 0;
        since = 0;
        until = 0;
        pids = "";
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) return;
            String json = Files.readString(path);
            bank = num(json, "bank");
            since = num(json, "since");
            until = num(json, "until");
            pids = text(json, "pids");
        } catch (Exception e) {
            Log.warn("Время не прочитано: " + Log.reason(e));
        }
    }

    private static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            String json = "{\"bank\":" + bank + ",\"since\":" + since + ",\"until\":" + until
                    + ",\"pids\":\"" + (pids == null ? "" : pids) + "\"}";
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn("Время не записано: " + Log.reason(e));
        }
    }

    private static long num(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return 0;
        int c = json.indexOf(':', i);
        if (c < 0) return 0;
        int s = c + 1;
        while (s < json.length() && json.charAt(s) == ' ') s++;
        int e = s;
        if (e < json.length() && json.charAt(e) == '-') e++;
        while (e < json.length() && Character.isDigit(json.charAt(e))) e++;
        try {
            return Long.parseLong(json.substring(s, e));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String text(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int q = json.indexOf('"', json.indexOf(':', i) + 1);
        if (q < 0) return "";
        int e = json.indexOf('"', q + 1);
        if (e < 0) return "";
        return json.substring(q + 1, e);
    }

    private static String word(long n, int index) {
        boolean ru = Lang.russian();
        String[] one = ru
                ? new String[]{"неделя", "день", "час", "минута", "секунда"}
                : new String[]{"week", "day", "hour", "minute", "second"};
        String[] few = ru
                ? new String[]{"недели", "дня", "часа", "минуты", "секунды"}
                : new String[]{"weeks", "days", "hours", "minutes", "seconds"};
        String[] many = ru
                ? new String[]{"недель", "дней", "часов", "минут", "секунд"}
                : few;
        long a = Math.abs(n) % 100;
        long b = a % 10;
        if (!ru) return n == 1 ? one[index] : few[index];
        if (a > 10 && a < 20) return many[index];
        if (b == 1) return one[index];
        if (b >= 2 && b <= 4) return few[index];
        return many[index];
    }

    private static final String SCRIPT = """
            param([string]$File)
            $deadline = (Get-Date).AddSeconds(30)
            while ((Get-Process -Name CWLauncher -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
              Start-Sleep -Milliseconds 400
            }
            if (Get-Process -Name CWLauncher -ErrorAction SilentlyContinue) { exit 0 }
            function Read-State {
              if (-not (Test-Path -LiteralPath $File)) { return $null }
              Get-Content -LiteralPath $File -Raw | ConvertFrom-Json
            }
            function Write-State([int64]$bank, [int64]$since, [int64]$until, [string]$pids) {
              $json = '{"bank":' + $bank + ',"since":' + $since + ',"until":' + $until + ',"pids":"' + $pids + '"}'
              Set-Content -LiteralPath $File -Value $json -Encoding ascii
            }
            $state = Read-State
            if ($null -eq $state) { exit 0 }
            $since = [int64]$state.since
            if ($since -le 0) { exit 0 }
            $pidList = @()
            if ($state.pids) { $pidList = ($state.pids -split ',') | Where-Object { $_ -match '^\\d+$' } }
            while ($true) {
              if (Get-Process -Name CWLauncher -ErrorAction SilentlyContinue) { exit 0 }
              $alive = $false
              foreach ($id in $pidList) {
                if (Get-Process -Id ([int]$id) -ErrorAction SilentlyContinue) { $alive = $true }
              }
              if (-not $alive) { break }
              $until = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
              Write-State ([int64]$state.bank) $since $until ([string]$state.pids)
              Start-Sleep -Seconds 5
              $state = Read-State
              if ($null -eq $state -or [int64]$state.since -le 0) { exit 0 }
              $since = [int64]$state.since
            }
            $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            $add = [int64](($now - $since) / 1000)
            if ($add -lt 0) { $add = 0 }
            $fresh = Read-State
            $bank = 0
            if ($null -ne $fresh) { $bank = [int64]$fresh.bank }
            Write-State ($bank + $add) 0 0 ""
            """;
}
