package ru.cw.launcher;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.accounts.ElyAuthManager;
import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.settings.SettingsManager;
import ru.cw.launcher.ui.Dialogs;
import ru.cw.launcher.ui.Lang;
import ru.cw.launcher.ui.MainWindow;
import ru.cw.launcher.ui.SplashWindow;
import ru.cw.launcher.ui.Theme;
import ru.cw.launcher.updater.LauncherUpdater;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Точка входа CWLauncher: один экземпляр, подготовка каталогов и главное окно.
 */
public final class Main {

    private static final int SINGLE_PORT = 47621;
    private static volatile MainWindow window;
    private static volatile ServerSocket single;

    private Main() {
    }

    public static void main(String[] args) {
        if (isUpdater(args)) {
            System.exit(runUpdater(args));
            return;
        }
        String oauth = oauthArg(args);
        if (!acquireSingleInstance(oauth)) {
            System.exit(0);
            return;
        }
        Paths.ensureDirs();
        javax.imageio.ImageIO.setUseCache(false);
        Log.init(Paths.mainLog(), false, true);
        Http.init(Paths.cache(), 12_000, 40_000);
        SettingsManager.load();
        ru.cw.launcher.core.PlayTime.begin();
        if (SettingsManager.get().autoStart) {
            SettingsManager.applyAutoStart(true);
        }
        try {
            SwingUtilities.invokeAndWait(Main::openUi);
        } catch (Exception e) {
            Log.error("Не удалось открыть окно: " + Log.reason(e), e);
            System.exit(1);
        }
    }

    private static void openUi() {
        Theme.install();
        Lang.use(SettingsManager.get().resolvedLanguage());
        Dialogs.supportOpener = () -> Utils.openUrl(SettingsManager.get().supportUrl);
        LauncherApp app = new LauncherApp();
        SplashWindow splash = new SplashWindow();
        splash.step(5, "Загрузка данных о лаунчере");
        splash.setVisible(true);
        Utils.nameOnTaskbar(splash);
        app.io().submit(() -> startup(app, splash));
    }

    private static void startup(LauncherApp app, SplashWindow splash) {
        try {
            Log.info("Экран загрузки: ссылки, версии, новости и обновления");
            splash.step(8, "Загрузка данных о лаунчере");
            try {
                app.fetchPatch();
            } catch (Exception e) {
                Log.warn("patchCWL.txt: " + Log.reason(e));
            }
            splash.step(18, "Проверка установленной игры");
            boolean mc = app.mc().versionReady(app.cfg.minecraftVersion);
            String loader = app.fabric().installedLoader(app.cfg.minecraftVersion);
            splash.step(28, "Подгрузка версии лаунчера");
            loadStartupFeeds(app, splash);
            boolean mods = app.mods().isInstalled(app.cfg.instanceId());
            splash.step(100, "Готово");
            if (app.launcherVersionDiffers()) {
                Log.info("Версия лаунчера отличается от lVersion.txt");
            }
            Log.info("Экран загрузки завершён, открываю лаунчер");
            try {
                Thread.sleep(280);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    splash.dispose();
                    MainWindow w = new MainWindow(app);
                    window = w;
                    bind(app, w);
                    w.showStartup();
                    schedule(app);
                    app.refreshState();
                    app.checkServer();
                    boolean pack = app.cfg.commonWorldMods();
                    boolean fabricNeeded = "fabric".equalsIgnoreCase(app.cfg.loader) && loader == null;
                    if (!mc || fabricNeeded || (pack && !mods)) {
                        Log.info("Сборка не готова — ждём кнопку «Установить»");
                    }
                    app.applyRemoteVersionDecision();
                    app.io().submit(() -> restoreEly(app, w));
                } catch (Throwable t) {
                    Log.error("Не удалось открыть главное окно: " + Log.reason(t), t);
                }
            });
        } catch (Exception e) {
            Log.error("Подготовка: " + Log.reason(e), e);
            SwingUtilities.invokeLater(() -> {
                splash.dispose();
                MainWindow w = new MainWindow(app);
                window = w;
                bind(app, w);
                w.showStartup();
                Dialogs.error(w, "Не удалось закончить проверку. Лаунчер открыт, повторите действие с главного экрана.",
                        Log.reason(e), null);
                app.io().submit(() -> restoreEly(app, w));
            });
        }
    }

    /** Версия лаунчера, моды, новости и список обновлений качаются одновременно. */
    private static void loadStartupFeeds(LauncherApp app, SplashWindow splash) {
        String[] labels = {
                "Подгрузка версии модов",
                "Подгрузка новостей",
                "Подгрузка списка обновлений",
                "Проверка файлов Minecraft",
                "Готово"
        };
        int[] marks = {48, 64, 78, 92, 100};
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        Runnable[] jobs = {
                () -> {
                    try {
                        app.fetchLauncherVersion();
                    } catch (Exception e) {
                        Log.warn("Версия лаунчера: " + Log.reason(e));
                    }
                },
                () -> {
                    try {
                        app.fetchBuildVersion(true);
                    } catch (Exception e) {
                        Log.warn("Версия модов: " + Log.reason(e));
                    }
                },
                () -> {
                    try {
                        app.fetchNews();
                    } catch (Exception e) {
                        Log.warn("Новости: " + Log.reason(e));
                    }
                },
                () -> {
                    try {
                        app.fetchUpdateNotes();
                    } catch (Exception e) {
                        Log.warn("Список обновлений: " + Log.reason(e));
                    }
                },
                () -> {
                    try {
                        app.checkSelectedMinecraft();
                    } catch (Exception e) {
                        Log.warn("Файлы Minecraft: " + Log.reason(e));
                    }
                }
        };
        ExecutorService pool = Executors.newFixedThreadPool(jobs.length, r -> {
            Thread t = new Thread(r, "cw-boot");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (Runnable job : jobs) {
                tasks.add(pool.submit(() -> {
                    job.run();
                    int i = Math.min(labels.length - 1, done.getAndIncrement());
                    splash.step(marks[i], labels[i]);
                }));
            }
            for (Future<?> task : tasks) task.get();
        } catch (Exception e) {
            Log.warn("Подгрузка при старте: " + Log.reason(e));
        } finally {
            pool.shutdownNow();
        }
    }

    private static void bind(LauncherApp app, MainWindow w) {
        app.setUpdateReadyCallback(version -> {
            if (Dialogs.ask(w, "Обновление CWLauncher " + version
                    + " загружено. Закрыть лаунчер и заменить файл?", "Перезапустить", "Позже")) {
                if (!app.applyLauncherUpdate()) {
                    Dialogs.error(w, "Не удалось запустить обновление лаунчера. Текущая установка не изменена.",
                            null, null);
                    return;
                }
                app.shutdown();
                System.exit(0);
            }
        });
        app.setLauncherUpdatePrompt((from, to) -> {
            if (Dialogs.ask(w, "Обновить лаунчер?\nСейчас " + from + ", в lVersion.txt " + to + ".",
                    "Обновить", "Закрыть")) {
                app.beginLauncherUpdate();
            } else {
                app.shutdown();
                System.exit(0);
            }
        });
        app.setBuildUpdatePrompt((from, to) -> {
            if (Dialogs.ask(w, "Обновить сборку?\nСейчас " + (from == null ? "неизвестно" : from)
                            + ", в cwVersion.txt " + to + ".",
                    "Обновить", "Закрыть")) {
                app.updateBuild();
            } else {
                app.shutdown();
                System.exit(0);
            }
        });
        app.setBuildFailedCallback(message -> Dialogs.error(w, message, null, app::updateBuild));
        app.setLaunchFailedCallback(message -> Dialogs.error(w, message, null, app::launchGame));
        if (SettingsManager.wasRepaired()) {
            Dialogs.info(w, SettingsManager.repairMessage());
        }
    }

    private static void schedule(LauncherApp app) {
        int seconds = Math.max(30, app.cfg.checkIntervalSec);
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cw-schedule");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(() -> {
            try {
                app.checkServer();
            } catch (Exception e) {
                Log.warn("Статус сервера: " + Log.reason(e));
            }
        }, 2, 2, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> {
            if (app.isBusy()) return;
            try {
                app.checkVersionsAsync(true);
                if (!Utils.isBlank(app.cfg.newsUrl)) app.news().load(app.cfg, false);
            } catch (Exception e) {
                Log.warn("Плановая проверка: " + Log.reason(e));
            }
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    private static void restoreEly(LauncherApp app, MainWindow w) {
        try {
            ElyAuthManager.registerProtocol();
            Account account = app.account();
            if (account == null || account.type != Account.Type.ELY) return;
            ElyAuthManager.SessionStatus session = ElyAuthManager.ensureSession(app.cfg, account, true);
            SwingUtilities.invokeLater(() -> {
                if (!session.ok() && session.message() != null) Dialogs.info(w, session.message());
                w.syncFromState();
            });
        } catch (Exception e) {
            Log.warn("Ely.by: " + Log.reason(e));
        }
    }

    private static String oauthArg(String[] args) {
        if (args == null) return null;
        for (String a : args) {
            if (a != null && a.toLowerCase(java.util.Locale.ROOT).startsWith(ElyAuthManager.SCHEME + ":")) {
                return a.replace("\r", "").replace("\n", "");
            }
        }
        return null;
    }

    private static boolean acquireSingleInstance(String oauthUri) {
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), SINGLE_PORT));
            single = socket;
            Thread t = new Thread(() -> {
                while (single != null && !single.isClosed()) {
                    try (Socket client = single.accept()) {
                        String line = readLine(client);
                        if (line.startsWith("OAUTH ")) ElyAuthManager.deliver(line.substring(6).trim());
                        SwingUtilities.invokeLater(() -> {
                            MainWindow w = window;
                            if (w == null) return;
                            w.setState(Frame.NORMAL);
                            w.setVisible(true);
                            w.toFront();
                            w.requestFocus();
                        });
                    } catch (IOException e) {
                        break;
                    }
                }
            }, "cw-single");
            t.setDaemon(true);
            t.start();
            return true;
        } catch (IOException e) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", SINGLE_PORT), 800);
                String payload = oauthUri == null ? "ACTIVATE\n" : "OAUTH " + oauthUri + "\n";
                s.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    private static String readLine(Socket client) {
        try {
            client.setSoTimeout(2000);
            InputStream in = client.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] tmp = new byte[256];
            while (bos.size() < 2048) {
                int n = in.read(tmp);
                if (n < 0) break;
                bos.write(tmp, 0, n);
                if (new String(bos.toByteArray(), StandardCharsets.UTF_8).indexOf('\n') >= 0) break;
            }
            String text = bos.toString(StandardCharsets.UTF_8);
            int nl = text.indexOf('\n');
            return (nl < 0 ? text : text.substring(0, nl)).trim();
        } catch (IOException e) {
            return "ACTIVATE";
        }
    }

    private static boolean isUpdater(String[] args) {
        for (String a : args) if ("--update-apply".equals(a)) return true;
        return false;
    }

    private static int runUpdater(String[] args) {
        Path target = null;
        Path source = null;
        long pid = -1;
        List<String> restart = new ArrayList<>();
        boolean rest = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--".equals(a)) {
                rest = true;
                continue;
            }
            if (rest) {
                restart.add(a);
                continue;
            }
            if ("--target".equals(a) && i + 1 < args.length) target = Path.of(args[++i]);
            else if ("--source".equals(a) && i + 1 < args.length) source = Path.of(args[++i]);
            else if ("--pid".equals(a) && i + 1 < args.length) pid = Long.parseLong(args[++i]);
        }
        if (target == null || source == null) return 2;
        return LauncherUpdater.runUpdater(target, source, pid, restart);
    }
}
