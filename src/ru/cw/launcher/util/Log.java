package ru.cw.launcher.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Файловое логирование с ротацией по размеру и буфером последних строк.
 * Пишется в logs/launcher.log (UTF-8). Async writer, чтобы не тормозить UI.
 */
public final class Log {

    public static final String VERSION = "3.5.12";

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int MAX_LINES = 4000;

    private static volatile Path file;
    private static volatile Level level = Level.INFO;
    private static final Deque<String> BUFFER = new ArrayDeque<>();
    private static final ScheduledExecutorService FLUSHER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cw-log");
                t.setDaemon(true);
                return t;
            });
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static volatile long fileSize;
    private static volatile long maxBytes = 16L * 1024 * 1024;
    private static volatile boolean console = true;

    static {
        FLUSHER.scheduleWithFixedDelay(Log::drain, 200, 500, TimeUnit.MILLISECONDS);
    }

    private Log() {
    }

    public static void init(Path logFile, boolean verbose, boolean printConsole) {
        file = logFile;
        console = printConsole;
        level = verbose ? Level.DEBUG : Level.INFO;
        try {
            Files.createDirectories(logFile.getParent());
            if (Files.exists(logFile)) fileSize = Files.size(logFile);
        } catch (IOException e) {
            file = null;
        }
        info("=== CWLauncher v" + VERSION + " запущен (" + (file != null ? logFile : "лог в файл недоступен") + ") ===");
    }

    public static void setLevel(Level l) {
        level = l;
    }

    public static Level level() {
        return level;
    }

    public static void debug(String m) {
        log(Level.DEBUG, m, null);
    }

    public static void info(String m) {
        log(Level.INFO, m, null);
    }

    public static void warn(String m) {
        log(Level.WARN, m, null);
    }

    public static void warn(String m, Throwable t) {
        log(Level.WARN, m, t);
    }

    public static void error(String m) {
        log(Level.ERROR, m, null);
    }

    public static void error(String m, Throwable t) {
        log(Level.ERROR, m, t);
    }

    public static void stage(String stage, String message) {
        log(Level.INFO, "[" + stage + "] " + message, null);
    }

    public static void log(Level lv, String message, Throwable t) {
        if (lv.ordinal() < level.ordinal() && lv != Level.ERROR) return;
        String line = TS.format(LocalDateTime.now()) + " " + pad(lv) + " " + message;
        synchronized (BUFFER) {
            BUFFER.addLast(line);
            while (BUFFER.size() > MAX_LINES) BUFFER.removeFirst();
        }
        synchronized (QUEUE) {
            QUEUE.addLast(line);
        }
        if (t != null) {
            String stack = stackTrace(t);
            synchronized (QUEUE) {
                QUEUE.addLast(stack);
            }
            if (console) System.err.println(stack);
        }
        if (console) {
            PrintStream out = lv == Level.ERROR ? System.err : System.out;
            out.println(line);
        }
    }

    private static String pad(Level lv) {
        return switch (lv) {
            case DEBUG -> "[DEBUG]";
            case INFO -> "[INFO ]";
            case WARN -> "[WARN ]";
            case ERROR -> "[ERROR]";
        };
    }

    public static String stackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw, true);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString().replace("	", "    ").stripTrailing();
    }

    /**
     * Краткая причина ошибки для пользователя (без стектрейса).
     */
    public static String reason(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        if (m == null || m.isBlank()) {
            Throwable c = t.getCause();
            if (c != null) return reason(c);
            return t.getClass().getSimpleName();
        }
        return t.getClass().getSimpleName() + ": " + m;
    }

    private static void drain() {
        List<String> batch = new ArrayList<>();
        synchronized (QUEUE) {
            while (!QUEUE.isEmpty()) batch.add(QUEUE.removeFirst());
        }
        if (batch.isEmpty() || file == null) return;
        try {
            if (fileSize > maxBytes) rotate();
            StringBuilder sb = new StringBuilder();
            for (String s : batch) sb.append(s).append(System.lineSeparator());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            fileSize += sb.toString().getBytes(StandardCharsets.UTF_8).length;
        } catch (IOException ignored) {
            // лог не должен ронять лаунчер
        }
    }

    private static void rotate() {
        try {
            for (int n = 4; n >= 1; n--) {
                Path old = file.resolveSibling(file.getFileName() + "." + n);
                Path to = file.resolveSibling(file.getFileName() + "." + (n + 1));
                if (Files.exists(old)) Files.move(old, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(file, file.resolveSibling(file.getFileName() + ".1"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            fileSize = 0;
        } catch (IOException ignored) {
        }
    }

    /**
     * Последние n строк лога (для окна диагностики).
     */
    public static List<String> tail(int n) {
        synchronized (BUFFER) {
            List<String> all = new ArrayList<>(BUFFER);
            if (all.size() <= n) return all;
            return all.subList(all.size() - n, all.size());
        }
    }

    public static void dumpTo(Writer w) throws IOException {
        for (String s : tail(MAX_LINES)) w.write(s + System.lineSeparator());
        w.flush();
    }

    public static void flushNow() {
        drain();
    }
}
