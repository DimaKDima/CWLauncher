package ru.cw.launcher.updater;

import ru.cw.launcher.core.Operation;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.core.State;
import ru.cw.launcher.model.GameState;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.network.GoogleDrive;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Безопасное обновление лаунчера. Работающий EXE/JAR нельзя заменить самим собой,
 * поэтому применяется такой порядок:
 *  1) новый файл скачивается в updates/ (tmp) и проверяется (размер, сигнатура);
 *  2) запускается отдельный процесс-апгрейдер (java -jar <новый> --update-apply),
 *     который ждёт завершения текущего PID, создаёт резервную копию, заменяет файл,
 *     запускает новую версию и при ошибке восстанавливает старую.
 */
public final class LauncherUpdater {

    private final State state;

    public LauncherUpdater(State state) {
        this.state = state;
    }

    public static Path downloaded(String version) {
        return Paths.updates().resolve("CWLauncher-" + version + ".jar");
    }

    /** Загрузка нового файла лаунчера. */
    public Operation downloadOperation(String url, String version, Settings cfg) {
        Operation op = new Operation("Загрузка обновления лаунчера", state)
                .runningAs(GameState.DOWNLOADING).finishAs(GameState.UPDATE_AVAILABLE);
        op.add("Загрузка нового файла", 5, () -> {
            Path dest = downloaded(version);
            Utils.ensureDirectories(dest.getParent());
            Utils.deleteQuietly(dest);
            if (url != null && url.contains("drive.google.com")) {
                GoogleDrive.download(url, dest, null,
                        (done, total) -> op.progress().progress(done, total, "Скачиваю лаунчер"),
                        op.cancel()::isCancelled);
            } else {
                Http.download(url, dest, null, 0,
                        (done, total) -> op.progress().progress(done, total, "Скачиваю лаунчер"),
                        op.cancel()::isCancelled);
            }
            if (!looksLikeExecutable(dest)) {
                Utils.deleteQuietly(dest);
                throw new IOException("Загруженный файл не похож на JAR/EXE — обновление отменено.");
            }
            long size = Utils.size(dest);
            if (size < 20_000) {
                Utils.deleteQuietly(dest);
                throw new IOException("Слишком маленький файл обновления (" + size + " байт) — "
                        + "возможно, загружена страница вместо файла.");
            }
            Log.info("Обновление лаунчера загружено: " + dest.getFileName() + " (" + Utils.humanSize(size) + ")");
        });
        return op;
    }

    private static boolean looksLikeExecutable(Path file) {
        try (var in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(4);
            if (head.length < 4) return false;
            // ZIP/JAR: PK\x03\x04 ; PE/EXE: MZ
            boolean zip = head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04;
            boolean pe = head[0] == 'M' && head[1] == 'Z';
            return zip || pe;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Запуск процесса-апгрейдера и завершение текущего приложения.
     *
     * @param targetFile     что заменяем (CWLauncher.jar или CWLauncher.exe)
     * @param restartCommand чем запускать новую версию
     */
    public boolean applyAndRestart(Path targetFile, List<String> restartCommand) {
        Path newFile = newestDownload();
        if (newFile == null) {
            Log.error("Нет загруженного файла обновления");
            return false;
        }
        long pid = currentPid();
        List<String> cmd = new ArrayList<>();
        Path exe = Paths.launcherExecutable();
        if (exe != null) {
            cmd.add(exe.toString());
        } else {
            String sep = System.getProperty("file.separator", "/");
            String javaExe = System.getProperty("java.home") + sep + "bin" + sep
                    + (Utils.isWindows() ? "java.exe" : "java");
            cmd.add(Files.exists(Path.of(javaExe)) ? javaExe : "java");
            cmd.add("-cp");
            Path cp = Paths.codeSource();
            cmd.add(cp == null ? "." : cp.toString());
            cmd.add("ru.cw.launcher.Main");
        }
        cmd.add("--update-apply");
        cmd.add("--target");
        cmd.add(targetFile.toString());
        cmd.add("--source");
        cmd.add(newFile.toString());
        cmd.add("--pid");
        cmd.add(String.valueOf(pid));
        if (restartCommand != null && !restartCommand.isEmpty()) {
            cmd.add("--");
            cmd.addAll(restartCommand);
        }
        try {
            new ProcessBuilder(cmd).start();
            Log.info("Запущен процесс обновления: " + String.join(" ", cmd));
            return true;
        } catch (IOException e) {
            Log.error("Не удалось запустить апгрейдер: " + Log.reason(e), e);
            return false;
        }
    }

    public Path newestDownload() {
        if (!Files.isDirectory(Paths.updates())) return null;
        try (var s = Files.list(Paths.updates())) {
            return s.filter(p -> p.getFileName().toString().startsWith("CWLauncher-")
                    && p.getFileName().toString().endsWith(".jar"))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static long currentPid() {
        try {
            return Long.parseLong(java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
                    .split("@")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Режим апгрейдера (--update-apply): ждёт завершения PID, меняет файл, перезапускает.
     * Возвращает код выхода.
     */
    public static int runUpdater(Path target, Path source, long pid, List<String> restart) {
        Log.init(Paths.mainLog(), true, false);
        Log.info("Апгрейдер: цель " + target + ", источник " + source + ", ждём PID " + pid);
        try {
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                if (pid <= 0 || !processAlive(pid)) break;
                Thread.sleep(400);
            }
            if (processAlive(pid)) {
                Log.error("Старый процесс не завершился за 60 с — обновление отменено");
                return 2;
            }
            if (!Files.isRegularFile(source)) {
                Log.error("Файл обновления отсутствует: " + source);
                return 3;
            }
            Path backup = target.resolveSibling(target.getFileName() + ".old");
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                Files.move(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.info("Резервная копия: " + backup.getFileName());
            }
            try {
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.info("Файл обновлён: " + target);
            } catch (IOException e) {
                Log.error("Не удалось заменить файл: " + Log.reason(e), e);
                if (Files.exists(backup)) {
                    Files.move(backup, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Log.warn("Восстановлена старая версия");
                }
                return 4;
            }
            Utils.deleteQuietly(source);
            if (restart != null && !restart.isEmpty()) {
                new ProcessBuilder(restart).start();
                Log.info("Новая версия запущена: " + String.join(" ", restart));
            }
            if (Files.exists(backup)) Utils.deleteQuietly(backup);
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        } catch (IOException e) {
            Log.error("Апгрейдер: " + Log.reason(e), e);
            return 1;
        }
    }

    /** Запускает отдельный CWLauncher-Update.exe и не заменяет файлы из этого процесса. */
    public static boolean launchExternalUpdater(String zipUrl) {
        Path root = Paths.installRoot();
        Path updater = root.resolve("CWLauncher-Update.exe");
        if (!Files.isRegularFile(updater)) {
            Log.error("Не найден файл обновления: " + updater);
            return false;
        }
        long pid = ProcessHandle.current().pid();
        try {
            java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
            cmd.add(updater.toString());
            cmd.add("--update");
            cmd.add("--pid");
            cmd.add(Long.toString(pid));
            cmd.add("--path");
            cmd.add(root.toString());
            if (zipUrl != null && zipUrl.startsWith("https://")) {
                cmd.add("--url");
                cmd.add(zipUrl);
            }
            new ProcessBuilder(cmd).directory(root.toFile()).start();
            Log.info("Запущен CWLauncher-Update.exe для " + root);
            return true;
        } catch (IOException e) {
            Log.error("Не удалось запустить обновление лаунчера: " + Log.reason(e), e);
            return false;
        }
    }

    private static boolean processAlive(long pid) {
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception e) {
            return true;
        }
    }
}
