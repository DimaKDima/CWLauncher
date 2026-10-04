package ru.cw.launcher.core;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.accounts.AccountManager;
import ru.cw.launcher.background.BackgroundManager;
import ru.cw.launcher.diagnostics.DiagnosticsManager;
import ru.cw.launcher.fabric.FabricManager;
import ru.cw.launcher.minecraft.JavaManager;
import ru.cw.launcher.minecraft.LaunchManager;
import ru.cw.launcher.minecraft.MinecraftManager;
import ru.cw.launcher.mods.ModManager;
import ru.cw.launcher.mods.ModVerifier;
import ru.cw.launcher.model.GameState;
import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.network.ServerStatus;
import ru.cw.launcher.news.NewsManager;
import ru.cw.launcher.profiles.LauncherProfile;
import ru.cw.launcher.profiles.ProfileManager;
import ru.cw.launcher.settings.SettingsManager;
import ru.cw.launcher.updater.LauncherUpdater;
import ru.cw.launcher.updater.UpdateChecker;
import ru.cw.launcher.updates.UpdateInfoManager;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Центральный контекст CWLauncher: менеджеры, единое состояние, очередь фоновых задач
 * и высокоуровневые операции (проверка, установка, обновление, исправление, запуск).
 * В UI не знает ничего про Swing — кроме публикаций в State и вызовов на EDT.
 */
public final class LauncherApp {

    public final State state = new State();
    public final Settings cfg;
    public final MinecraftManager mc = new MinecraftManager();
    public final FabricManager fabric = new FabricManager();
    public final JavaManager java = new JavaManager();
    public final ModManager mods;
    public final LaunchManager launch;
    public final AccountManager accounts = new AccountManager();
    public final ProfileManager profiles = new ProfileManager();
    public final NewsManager news = new NewsManager();
    public final DiagnosticsManager diagnostics = new DiagnosticsManager();
    public final LauncherUpdater updater;
    public final UpdateInfoManager updateInfo = new UpdateInfoManager();
    public final BackgroundManager background = new BackgroundManager();


    private final ExecutorService io = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "cw-io");
        t.setDaemon(true);
        return t;
    });    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile Operation current;
    private volatile ServerStatus.Info serverInfo;
    private volatile long lastServerCheck;
    private volatile boolean networkAvailable = true;
    private volatile Runnable onStateChanged;

    public LauncherApp() {
        this.cfg = SettingsManager.get();
        this.mods = new ModManager(state);
        this.launch = new LaunchManager(state, mc, java);
        this.updater = new LauncherUpdater(state);
        Paths.relocateGames();
        boolean dropped = cfg.versionDirs.entrySet().removeIf(e -> e.getValue() == null
                || Paths.forbiddenGameDir(Path.of(e.getValue())));
        if (dropped) SettingsManager.save();
        if (cfg.minecraftVersion == null || cfg.minecraftVersion.isBlank() || "1.20.3".equals(cfg.minecraftVersion)) {
            cfg.minecraftVersion = "1.20.1";
        }
        if (cfg.commonWorld) {
            cfg.minecraftVersion = "1.20.1";
            cfg.loader = "fabric";
        }
        bindPaths();
        PlayTime.watch(launch::runningPids);
        background.reload(cfg);
        syncProfileToSettings();
        if ("1.20.1".equals(cfg.minecraftVersion)) {
            cfg.loader = cfg.commonWorld || cfg.fabricFor(cfg.minecraftVersion) ? "fabric" : "vanilla";
            LauncherProfile profile = profiles.active();
            if (profile != null && (profile.minecraftVersion == null || profile.minecraftVersion.isBlank()
                    || "1.20.3".equals(profile.minecraftVersion))) {
                profile.minecraftVersion = "1.20.1";
                profiles.update(profile);
            }
        }
        Paths.ensureDirs();
    }

    /** Пути игры всегда смотрят на выбранную версию и её папку. */
    public void bindPaths() {
        Paths.Holder.versionDirs.clear();
        if (cfg.versionDirs != null) Paths.Holder.versionDirs.putAll(cfg.versionDirs);
        Paths.Holder.activeVersion = cfg.minecraftVersion;
    }

    public void onStateChanged(Runnable r) {
        this.onStateChanged = r;
    }

    public void rememberVersionDir(String version, Path dir) {
        if (version == null || dir == null) return;
        String key = Paths.sanitize(version);
        cfg.versionDirs.put(key, dir.toAbsolutePath().normalize().toString());
        bindPaths();
        Paths.ensureDirs();
    }

    public ExecutorService io() {
        return io;
    }

    public State state() {
        return state;
    }

    public MinecraftManager mc() {
        return mc;
    }

    public FabricManager fabric() {
        return fabric;
    }

    public JavaManager java() {
        return java;
    }

    public ModManager mods() {
        return mods;
    }

    public UpdateInfoManager updateInfo() {
        return updateInfo;
    }

    /** Информационный ZIP не ломает сборку модов, если его не удалось получить. */
    public void refreshUpdateInfo() {
        io.submit(() -> {
            try {
                UpdateInfoManager.Outcome outcome = updateInfo.sync(true);
                if (!outcome.ok()) Log.warn("Информация об обновлениях: " + outcome.message());
            } catch (Exception e) {
                Log.warn("Информация об обновлениях: " + Log.reason(e));
            }
        });
    }

    public LaunchManager launch() {
        return launch;
    }

    public AccountManager accounts() {
        return accounts;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public NewsManager news() {
        return news;
    }

    public LauncherUpdater updater() {
        return updater;
    }

    public DiagnosticsManager diagnostics() {
        return diagnostics;
    }

    public BackgroundManager background() {
        return background;
    }

    public boolean isNetworkAvailable() {
        return networkAvailable;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public Operation currentOperation() {
        return current;
    }

    /** Запуск фоновой операции: только одна «долгая» операция одновременно. */
    public boolean submit(String name, Operation op) {
        if (!busy.compareAndSet(false, true)) {
            Log.warn("Операция «" + name + "» отклонена: уже выполняется "
                    + (current == null ? "?" : current.name()));
            return false;
        }
        current = op;
        io.submit(() -> {
            try {
                op.run();
            } finally {
                boolean stopped = op.cancel().isCancelled();
                current = null;
                busy.set(false);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (stopped) {
                        launchAfterMods = false;
                        refreshState();
                    } else if (onStateChanged != null) onStateChanged.run();
                });
            }
        });
        return true;
    }

    public void cancelCurrent() {
        Operation op = current;
        if (op != null) {
            op.cancelOperation();
            Log.info("Запрошена отмена операции «" + op.name() + "»");
        }
    }

    // ------------------------------------------------------------------ состояние

    /**
     * Профиль запуска без сети: заданный Fabric, иначе уже установленный.
     * Запрос meta.fabricmc.net делается только в момент установки, не при отрисовке окна.
     */
    public String activeProfileId() {
        if (!"fabric".equalsIgnoreCase(cfg.loader)) return cfg.minecraftVersion;
        if (!Utils.isBlank(cfg.fabricLoaderVersion)) {
            return FabricManager.profileId(cfg.fabricLoaderVersion, cfg.minecraftVersion);
        }
        String installed = fabric.installedLoader(cfg.minecraftVersion);
        if (installed != null) return FabricManager.profileId(installed, cfg.minecraftVersion);
        return cfg.minecraftVersion;
    }

    public String requiredLoader() {
        return fabric.requiredLoader(cfg.minecraftVersion, cfg.fabricLoaderVersion);
    }

    /** Пересчёт реального состояния: UI показывает только его. */
    public GameStateSnapshot evaluate() {
        bindPaths();
        String profileId = activeProfileId();
        boolean pack = cfg.commonWorldMods();
        boolean mcReady = mc.versionReady(cfg.minecraftVersion);
        String installedLoader = fabric.installedLoader(cfg.minecraftVersion);
        boolean fabricReady = !"fabric".equalsIgnoreCase(cfg.loader)
                || (installedLoader != null && mc.versionReady(
                FabricManager.profileId(installedLoader, cfg.minecraftVersion)));
        boolean modsInstalled = mods.isInstalled(cfg.instanceId());
        ModVerifier.Report report = null;
        try {
            report = new ModVerifier().verify(cfg);
        } catch (Exception e) {
            Log.debug("ModVerifier недоступен: " + Log.reason(e));
        }
        boolean modsOk = modsInstalled && (report == null || report.problems().isEmpty());
        GameState next;
        String detail = "";
        if (state.get().isBusy()) {
            return new GameStateSnapshot(state.get(), state.detail(), mcReady, fabricReady, modsOk,
                    report == null ? List.of() : report.problems(), profileId);
        }
        if (launch.isActive(cfg.minecraftVersion)) {
            GameState live = launch.isBooted(cfg.minecraftVersion) ? GameState.RUNNING : GameState.STARTING;
            String liveDetail = live == GameState.RUNNING ? "Minecraft запущен" : "Minecraft запускается";
            return new GameStateSnapshot(live, liveDetail, mcReady, fabricReady, modsOk,
                    report == null ? List.of() : report.problems(), profileId);
        }
        boolean jar = Paths.localGamePresent(cfg.minecraftVersion);
        boolean modsNeedUpdate = pack && ru.cw.launcher.settings.UpdateSchedule.offerMods(cfg)
                && cfg.remoteBuildVersion != null
                && UpdateChecker.buildUpdateNeeded(mods.installedBuildVersion(cfg.instanceId()),
                cfg.remoteBuildVersion);
        boolean gameNeedsUpdate = jar && (minecraftFilesNeedUpdate || !mcReady || !fabricReady);
        pendingGameUpdate = gameNeedsUpdate;
        if (!jar) {
            next = GameState.NOT_INSTALLED;
            if (!Utils.writable(Paths.gameDir())) {
                detail = Utils.writeDeniedMessage(Paths.gameDir());
            } else {
                detail = "Minecraft " + cfg.minecraftVersion + " не установлен";
            }
        } else if (gameNeedsUpdate) {
            next = GameState.UPDATE_AVAILABLE;
            int assetsLeft = mc.missingAssets(cfg.minecraftVersion);
            if (!mcReady && assetsLeft > 0) {
                detail = "Файлы игры скачаны не полностью: не хватает текстур, звуков или языков. Нажмите «Обновить Minecraft».";
            } else if (!fabricReady) {
                detail = "Нужно обновить Fabric для Minecraft " + cfg.minecraftVersion;
            } else {
                detail = "Файлы Minecraft " + cfg.minecraftVersion + " нужно обновить";
            }
        } else if (pack && !modsInstalled) {
            next = GameState.NOT_INSTALLED;
            detail = "Моды Common World не установлены";
        } else if (modsNeedUpdate) {
            next = GameState.UPDATE_AVAILABLE;
            detail = "Доступно обновление модов: " + cfg.remoteBuildVersion;
        } else if (pack && cfg.verifyBuildOnLaunch && !modsOk) {
            next = GameState.ERROR;
            detail = report == null ? "Проверка модов не пройдена"
                    : Utils.shorten(String.join("; ", report.problems()), 220);
        } else if (!networkAvailable && cfg.offlineAllowed) {
            next = GameState.OFFLINE;
            detail = "Нет соединения — запуск из локальной установки";
        } else {
            next = GameState.READY;
            detail = "Сборка актуальна";
        }
        return new GameStateSnapshot(next, detail, mcReady, fabricReady, modsOk,
                report == null ? List.of() : report.problems(), profileId);
    }

    public record GameStateSnapshot(GameState state, String detail, boolean mcReady, boolean fabricReady,
                                    boolean modsOk, List<String> modProblems, String profileId) {
    }

    /** Центр состояния COMMON WORLD (доп. функция 16). */
    public List<Map<String, Object>> statusCenter() {
        List<Map<String, Object>> rows = new ArrayList<>();
        GameStateSnapshot snap = evaluate();
        rows.add(row("Сборка", modsOk() ? "актуальна" : "требует внимания",
                modsOk() ? "ok" : "warn", mods.installedBuildVersion(cfg.instanceId())));
        rows.add(row("Minecraft " + cfg.minecraftVersion, snap.mcReady() ? "установлен" : "не установлен",
                snap.mcReady() ? "ok" : "error", null));
        String loader = fabric.installedLoader(cfg.minecraftVersion);
        rows.add(row("Fabric", loader == null ? "не установлен" : "установлен " + loader,
                loader == null ? "error" : "ok", null));
        int count = mods.modCount(cfg.instanceId());
        rows.add(row(count + (count == 1 ? " мод" : " модов"),
                modsOk() ? "проверены" : "есть проблемы", modsOk() ? "ok" : "error", null));
        ServerStatus.Info s = serverInfo;
        if (s == null || !s.available()) {
            rows.add(row("Сервер", "статус сервера недоступен", "info", null));
        } else if (s.online()) {
            rows.add(row("Сервер доступен", s.players() + "/" + s.maxPlayers() + " · " + s.pingMs() + " мс",
                    "ok", null));
        } else {
            rows.add(row("Сервер не отвечает", "проверьте адрес в настройках", "error", null));
        }
        return rows;
    }

    private boolean modsOk() {
        if (!mods.isInstalled(cfg.instanceId())) return false;
        try {
            return new ModVerifier().verify(cfg).problems().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, Object> row(String title, String value, String tone, String extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("value", value);
        m.put("tone", tone);
        m.put("extra", extra);
        return m;
    }

    // ------------------------------------------------------------------ операции

    /** Скачивает patchCWL.txt и подставляет ссылки до остальных загрузок. */
    public void fetchPatch() {
        try {
            ru.cw.launcher.updater.PatchFile patch = ru.cw.launcher.updater.PatchFile.download();
            patch.apply(cfg);
            updateInfo.setArchiveUrl(cfg.updateInfoUrl);
            Log.info("patchCWL.txt: ссылок " + patch.size());
        } catch (Exception e) {
            Log.warn("patchCWL.txt: " + Log.reason(e));
        }
    }

    /** Скачивает lVersion.txt. Вызывать из фонового потока. */
    public void fetchLauncherVersion() {
        UpdateChecker.Remote lv = UpdateChecker.lVersion(cfg);
        if (lv.available() || ServerStatus.cached() != null) networkAvailable = true;
        if (lv.ok()) {
            cfg.remoteLauncherVersion = lv.value();
            Log.info("lVersion.txt = " + lv.value() + (lv.fromCache() ? " (из кэша)" : ""));
        } else {
            cfg.remoteLauncherVersion = null;
            Log.warn("lVersion.txt недоступен: " + lv.error());
        }
    }

    /** Версия из modsCWL.zip, прочитанная при запуске. Не затирается устаревшим cwVersion.txt. */
    private volatile String modsZipVersion;

    /**
     * Версия модов. При запуске читается Version.txt внутри modsCWL.zip.
     * Файл cwVersion.txt остаётся запасным, если архив не скачался.
     */
    public void fetchBuildVersion() {
        fetchBuildVersion(false);
    }

    public void fetchBuildVersion(boolean readZip) {
        if (readZip && cfg.commonWorldMods()) {
            try {
                modsZipVersion = mods.downloadRemoteModsVersion(cfg);
            } catch (Exception e) {
                modsZipVersion = null;
                Log.warn("modsCWL.zip: " + Log.reason(e));
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
        UpdateChecker.Remote cw = UpdateChecker.cwVersion(cfg);
        if (cw.available() || ServerStatus.cached() != null) networkAvailable = true;
        if (modsZipVersion != null && !modsZipVersion.isBlank()) {
            cfg.remoteBuildVersion = modsZipVersion;
            if (cw.ok() && Utils.compareVersions(modsZipVersion, cw.value()) != 0) {
                Log.warn("cwVersion.txt = " + cw.value() + ", в modsCWL.zip " + modsZipVersion
                        + ". Версия модов берётся из архива.");
            } else if (readZip) {
                Log.info("modsCWL.zip = " + modsZipVersion);
            }
            return;
        }
        if (cw.ok()) {
            cfg.remoteBuildVersion = cw.value();
            Log.info("cwVersion.txt = " + cw.value() + (cw.fromCache() ? " (из кэша)" : ""));
        } else {
            cfg.remoteBuildVersion = null;
            Log.warn("Версия модов недоступна: "
                    + (cw.error() == null ? "архив модов не прочитан" : cw.error()));
        }
    }

    private volatile boolean minecraftFilesNeedUpdate;
    private volatile boolean pendingGameUpdate;

    /** Во время загрузки: сверка выбранной версии с Mojang. Файлы ещё не качаются. */
    public void checkSelectedMinecraft() {
        String version = cfg.minecraftVersion;
        boolean remote = false;
        if (Paths.localGamePresent(version)) {
            remote = mc.remoteJsonDiffers(version);
        }
        minecraftFilesNeedUpdate = remote;
        Log.info(remote
                ? "Файлы Minecraft " + version + " на Mojang новее установленных"
                : "Файлы Minecraft " + version + " совпадают с Mojang");
    }

    public void recheckMinecraftFiles() {
        minecraftFilesNeedUpdate = false;
        io.submit(() -> {
            checkSelectedMinecraft();
            javax.swing.SwingUtilities.invokeLater(this::refreshState);
        });
    }

    public boolean pendingGameUpdate() {
        return pendingGameUpdate;
    }

    /** Новости. Ошибка не останавливает запуск. */
    public void fetchNews() {
        if (Utils.isBlank(cfg.newsUrl)) return;
        news.load(cfg, true);
    }

    /** Список обновлений, который потом открывается в окне «Обновления». */
    public void fetchUpdateNotes() {
        try {
            UpdateInfoManager.Outcome outcome = updateInfo.sync(true);
            if (!outcome.ok()) Log.warn("Информация об обновлениях: " + outcome.message());
        } catch (Exception e) {
            Log.warn("Информация об обновлениях: " + Log.reason(e));
        }
    }

    /** Сравнение уже скачанных версий. Вызывать, когда окно и вопросы уже подключены. */
    public void applyRemoteVersionDecision() {
        applyRemoteVersionDecision(false);
    }

    public void applyRemoteVersionDecision(boolean startup) {
        if (launcherVersionDiffers()
                && ru.cw.launcher.settings.UpdateSchedule.launcherHandoff(cfg, startup)) {
            ru.cw.launcher.settings.UpdateSchedule.mark("launcher");
        } else if ("day".equals(cfg.launcherUpdateEvery)) {
            ru.cw.launcher.settings.UpdateSchedule.mark("launcher");
        }
        if (launcherVersionDiffers()) offerLauncherUpdate();
        refreshState();
    }

    private volatile boolean updateRevealed;
    private volatile String offeredVersion;
    private volatile Runnable launcherUpdateFocus;

    public void setLauncherUpdateFocus(Runnable focus) {
        this.launcherUpdateFocus = focus;
    }

    /** Кнопка под настройками: уведомления выключены или уже нажато уведомление Windows. */
    public boolean launcherUpdateButton() {
        return launcherVersionDiffers() && (updateRevealed || !cfg.notificationsEnabled);
    }

    private void offerLauncherUpdate() {
        String version = cfg.remoteLauncherVersion;
        if (version == null || version.equals(offeredVersion)) return;
        offeredVersion = version;
        if (!cfg.notificationsEnabled) {
            updateRevealed = true;
            return;
        }
        String text = ru.cw.launcher.ui.Lang.t("upd_toast") + version
                + ru.cw.launcher.ui.Lang.t("upd_now") + ru.cw.launcher.util.Log.VERSION + ".";
        ru.cw.launcher.ui.UiLook.notifyUpdate(text, () -> {
            updateRevealed = true;
            Runnable focus = launcherUpdateFocus;
            if (focus != null) javax.swing.SwingUtilities.invokeLater(focus);
        });
    }

    /** Проверка версий источников (cwVersion.txt / lVersion.txt) — только факты. */
    public void checkVersionsAsync(boolean offlineOk) {
        io.submit(() -> {
            if (ru.cw.launcher.settings.UpdateSchedule.refetch(cfg, false)) fetchLauncherVersion();
            if (ru.cw.launcher.settings.UpdateSchedule.refetch(cfg, true)) {
                fetchBuildVersion();
                if ("day".equals(cfg.modsUpdateEvery)) ru.cw.launcher.settings.UpdateSchedule.mark("mods");
            }
            javax.swing.SwingUtilities.invokeLater(this::applyRemoteVersionDecision);
        });
    }

    private void notifyLauncherUpdate(String version) {
        Log.info("Автообновление лаунчера: " + Log.VERSION + " → " + version);
        Operation op = updater.downloadOperation(
                cfg.launcherDownloadUrlFor(version), version, cfg);
        op.onSuccess(() -> javax.swing.SwingUtilities.invokeLater(() -> {
            state.set(GameState.UPDATE_AVAILABLE, "Обновление готово к установке");
            if (updateReadyCallback != null) updateReadyCallback.accept(version);
        }));
        submit("Обновление лаунчера", op);
    }

    public interface UpdateReady {
        void accept(String version);
    }

    private volatile UpdateReady updateReadyCallback;
    private volatile BuildUpdatePrompt buildUpdatePrompt;
    private volatile LauncherUpdatePrompt launcherUpdatePrompt;
    private volatile boolean launchAfterMods;

    public void setUpdateReadyCallback(UpdateReady cb) {
        this.updateReadyCallback = cb;
    }

    public interface BuildUpdatePrompt {
        void prompt(String from, String to);
    }

    public interface LauncherUpdatePrompt {
        void prompt(String from, String to);
    }

    public void setBuildUpdatePrompt(BuildUpdatePrompt p) {
        this.buildUpdatePrompt = p;
    }

    public void setLauncherUpdatePrompt(LauncherUpdatePrompt p) {
        this.launcherUpdatePrompt = p;
    }

    private void suggestLauncherUpdate(String version) {
        if (launcherUpdatePrompt != null) {
            launcherUpdatePrompt.prompt(Log.VERSION, version);
        }
        state.set(GameState.UPDATE_AVAILABLE, "Доступно обновление лаунчера " + version);
    }

    private void suggestBuildUpdate(String from, String to) {
        if (buildUpdatePrompt != null) {
            buildUpdatePrompt.prompt(from, to);
        }
        state.set(GameState.UPDATE_AVAILABLE, "Доступно обновление сборки " + to);
    }

    /** Полная первичная/плановая проверка состояния сборки. */
    public void refreshState() {
        GameStateSnapshot snap = evaluate();
        if (!state.get().isBusy()) {
            state.set(snap.state(), snap.detail());
        }
        if (onStateChanged != null) {
            javax.swing.SwingUtilities.invokeLater(onStateChanged);
        }
    }

    /** Установка/подготовка выбранной версии (Minecraft + Fabric + моды). */
    public void install() {
        install(false);
    }

    /** Скачать обновление файлов Minecraft и сразу запустить игру. */
    public void updateMinecraftAndLaunch() {
        install(true);
    }

    private void install(boolean thenLaunch) {
        if (isBusy()) return;
        bindPaths();
        if (accounts.count() == 0) {
            Log.warn("Установка отменена: сначала создайте профиль");
            return;
        }
        if (cfg.minecraftVersion == null || cfg.minecraftVersion.isBlank()) {
            cfg.minecraftVersion = "1.20.1";
        }
        if (cfg.commonWorld) {
            cfg.minecraftVersion = "1.20.1";
            cfg.loader = "fabric";
        } else {
            cfg.loader = cfg.fabricFor(cfg.minecraftVersion) ? "fabric" : "vanilla";
        }
        boolean useFabric = "fabric".equalsIgnoreCase(cfg.loader);
        if (!Utils.writable(Paths.gameDir())) {
            String msg = Utils.writeDeniedMessage(Paths.gameDir());
            Log.error(msg);
            state.set(GameState.NOT_INSTALLED, msg);
            refreshState();
            return;
        }
        Log.info("Установка Minecraft " + cfg.minecraftVersion + (useFabric ? " с Fabric" : " без Fabric"));
        String profileId = activeProfileId();
        List<Operation.Task> tasks = new ArrayList<>();
        if (minecraftFilesNeedUpdate || !mc.versionReady(cfg.minecraftVersion)) {
            tasks.addAll(mc.installTasks(cfg.minecraftVersion, listener()));
        }
        if ("fabric".equalsIgnoreCase(cfg.loader)) {
            String loader = requiredLoader();
            if (loader == null) {
                state.set(GameState.ERROR, "Не удалось определить версию Fabric. Проверьте интернет.");
                return;
            }
            if (!fabric.isInstalled(loader, cfg.minecraftVersion)
                    || !mc.versionReady(profileId)) {
                tasks.addAll(fabric.installTasks(cfg.minecraftVersion, loader, listener()));
            }
        }
        tasks.add(new Operation.Task("Проверка ресурсов", 2, () -> {
            List<String> problems = mc.verify(cfg.minecraftVersion);
            if (!problems.isEmpty()) throw new IOException("Minecraft не готов: " + problems);
        }));
        String opName = cfg.commonWorldMods() ? "Подготовка Common World" : "Установка Minecraft " + cfg.minecraftVersion;
        Operation op = new Operation(opName, state)
                .runningAs(GameState.INSTALLING).finishAs(GameState.READY);
        for (Operation.Task t : tasks) op.add(t.title, t.weight, t.body);
        op.onSuccess(() -> javax.swing.SwingUtilities.invokeLater(() -> {
            minecraftFilesNeedUpdate = false;
            pendingGameUpdate = false;
            ModVerifier.writeManifest(cfg.instanceId(), Paths.mods(cfg.instanceId()));
            boolean modsNeedUpdate = cfg.commonWorldMods() && cfg.remoteBuildVersion != null
                    && UpdateChecker.buildUpdateNeeded(mods.installedBuildVersion(cfg.instanceId()),
                    cfg.remoteBuildVersion);
            if (cfg.commonWorldMods() && (!mods.isInstalled(cfg.instanceId()) || modsNeedUpdate)) {
                launchAfterMods = thenLaunch;
                updateBuild();
            } else if (thenLaunch) {
                refreshState();
                refreshUpdateInfo();
                launchGame();
            } else {
                refreshState();
                refreshUpdateInfo();
            }
        }));
        submit("Установка", op);
    }

    /** Кнопка «Обновить»: ставит моды и после этого запускает игру. */
    public void updateModsAndLaunch() {
        if (isBusy()) return;
        launchAfterMods = true;
        updateBuild();
    }

    /** Скачивание и установка сборки модов Common World. */
    public void updateBuild() {
        if (isBusy()) {
            launchAfterMods = false;
            return;
        }
        if (!cfg.commonWorldMods()) {
            launchAfterMods = false;
            Log.info("Моды Common World не ставятся на версию " + cfg.minecraftVersion);
            refreshState();
            return;
        }
        if (launch.isActive(cfg.minecraftVersion)) launch.stopVersion(cfg.minecraftVersion);
        bindPaths();
        String remote = cfg.remoteBuildVersion;
        Operation op = mods.installOperation(cfg, remote, listener());
        op.onSuccess(() -> javax.swing.SwingUtilities.invokeLater(() -> {
            ModVerifier.writeManifest(cfg.instanceId(), Paths.mods(cfg.instanceId()));
            boolean start = launchAfterMods;
            launchAfterMods = false;
            refreshState();
            refreshUpdateInfo();
            if (start) launchGame();
        }));
        op.onError(t -> javax.swing.SwingUtilities.invokeLater(() -> {
            launchAfterMods = false;
            if (buildFailedCallback != null) buildFailedCallback.fail(Operation.friendly(t));
        }));
        if (!submit("Обновление сборки", op)) launchAfterMods = false;
    }

    public interface BuildFailed {
        void fail(String message);
    }

    private volatile BuildFailed buildFailedCallback;

    public void setBuildFailedCallback(BuildFailed cb) {
        this.buildFailedCallback = cb;
    }

    /** Проверка сборки (доп. функция 3): реальные проверки + понятный итог. */
    public Operation verifyOperation() {
        bindPaths();
        Operation op = new Operation("Проверка сборки", state).runningAs(GameState.CHECKING)
                .finishAs(GameState.READY);
        op.add("Проверка Java", 1, () -> {
            if (java.select(cfg.minecraftVersion, cfg.javaPath) == null) {
                throw new IOException("Не найдена Java " + JavaManager.requiredMajor(cfg.minecraftVersion)
                        + " — установите её в разделе «Диагностика».");
            }
        });
        op.add("Проверка Minecraft " + cfg.minecraftVersion, 3, () -> {
            List<String> problems = mc.verify(cfg.minecraftVersion);
            if (!problems.isEmpty()) {
                throw new IOException("Проблемы Minecraft: " + String.join("; ", problems));
            }
        });
        if ("fabric".equalsIgnoreCase(cfg.loader)) {
            op.add("Проверка Fabric", 2, () -> {
                String loader = requiredLoader();
                if (loader == null) throw new IOException("Версия Fabric не определена (нет сети).");
                List<String> problems = mc.verify(FabricManager.profileId(loader, cfg.minecraftVersion));
                if (!problems.isEmpty()) {
                    throw new IOException("Проблемы Fabric: " + String.join("; ", problems));
                }
            });
        }
        op.add("Проверка модов", 3, () -> {
            ModVerifier.Report r = new ModVerifier().verify(cfg);
            if (!r.problems().isEmpty()) {
                throw new IOException("Проблемы сборки модов: " + String.join("; ", r.problems()));
            }
        });
        op.add("Проверка версии сборки", 1, () -> {
            String installed = mods.installedBuildVersion(cfg.instanceId());
            if (cfg.remoteBuildVersion != null && UpdateChecker.buildUpdateNeeded(installed,
                    cfg.remoteBuildVersion)) {
                throw new IOException("Установлена устаревшая сборка " + installed + ", актуальная "
                        + cfg.remoteBuildVersion);
            }
        });
        op.add("Свободное место и доступ", 1, () -> {
            long free = Paths.freeSpaceMb(Paths.root());
            if (cfg.checkDiskSpace && free >= 0 && free < 1024) {
                throw new IOException("Мало свободного места: " + free + " МБ");
            }
            Path probe = Paths.tmp().resolve("probe.tmp");
            Utils.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        });
        op.add("Сеть и источники", 1, () -> {
            UpdateChecker.Remote cw = UpdateChecker.cwVersion(cfg);
            if (!cw.ok()) throw new IOException("Источник cwVersion.txt недоступен: " + cw.error());
        });
        return op;
    }

    /** Восстановление сборки (доп. функция 4): сохраняет пользовательские данные. */
    public void repair() {
        if (isBusy()) return;
        install();
    }

    /**
     * Удаляет все скачанные версии игры, моды и миры и возвращает лаунчер
     * к виду первой установки. Сам лаунчер и аккаунты остаются.
     */
    public void resetGameData() {
        launch.stopGame();
        java.util.LinkedHashSet<Path> dirs = new java.util.LinkedHashSet<>();
        dirs.add(Paths.gamesRoot());
        dirs.add(Paths.installRoot().resolve("games"));
        if (cfg.versionDirs != null) {
            for (String saved : cfg.versionDirs.values()) {
                if (saved != null && !saved.isBlank()) dirs.add(Path.of(saved));
            }
        }
        for (String extra : Paths.Holder.versionDirs.values()) {
            if (extra != null && !extra.isBlank()) dirs.add(Path.of(extra));
        }
        for (Path dir : dirs) {
            if (dir == null) continue;
            Utils.deleteTree(dir);
            Log.info("Удалена папка игры: " + dir);
        }
        Utils.clearDir(Paths.cache());
        Utils.clearDir(Paths.tmp());
        cfg.versionDirs.clear();
        Paths.Holder.versionDirs.clear();
        cfg.commonWorld = true;
        cfg.minecraftVersion = "1.20.1";
        cfg.loader = "fabric";
        cfg.fabricLoaderVersion = "";
        cfg.buildVersion = null;
        cfg.validate();
        saveSettings();
        bindPaths();
        refreshState();
    }

    /**
     * Стирает все данные лаунчера по всем путям и после выхода удаляет папку программы.
     * Исходники проекта разработки не трогает.
     */
    public void uninstallEverything() {
        launch.stopGame();
        SettingsManager.applyAutoStart(false);
        java.util.LinkedHashSet<Path> dirs = new java.util.LinkedHashSet<>();
        dirs.add(Paths.root());
        Path homeCw = Paths.home().resolve("CWLauncher");
        dirs.add(homeCw);
        dirs.add(Paths.gamesRoot());
        if (cfg.versionDirs != null) {
            for (String saved : cfg.versionDirs.values()) {
                if (saved != null && !saved.isBlank()) dirs.add(Path.of(saved));
            }
        }
        Path install = Paths.installRoot();
        boolean sourceTree = Files.isRegularFile(install.resolve("build-release.ps1"))
                || Files.isDirectory(install.resolve("src"));
        if (!sourceTree && Files.isRegularFile(install.resolve("CWLauncher.exe"))) {
            dirs.add(install);
        }
        long pid = ProcessHandle.current().pid();
        try {
            Path script = Files.createTempFile("cw-uninstall-", ".ps1");
            StringBuilder ps = new StringBuilder();
            ps.append("$target = ").append(pid).append("\r\n");
            ps.append("while (Get-Process -Id $target -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 400 }\r\n");
            for (Path dir : dirs) {
                String literal = dir.toAbsolutePath().normalize().toString().replace("'", "''");
                ps.append("Remove-Item -LiteralPath '").append(literal).append("' -Recurse -Force -ErrorAction SilentlyContinue\r\n");
            }
            ps.append("Remove-Item -LiteralPath '").append(script.toAbsolutePath().toString().replace("'", "''"))
                    .append("' -Force -ErrorAction SilentlyContinue\r\n");
            Files.writeString(script, ps.toString());
            new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                    .start();
            Log.info("Полное удаление запущено, папок: " + dirs.size());
        } catch (IOException e) {
            Log.error("Не удалось подготовить удаление: " + Log.reason(e), e);
        }
    }

    /** Запуск игры. */
    public void launchGame() {
        launchGame(null, null);
    }

    /**
     * @param playDir папка модов и миров; null — основная папка версии
     * @param tempNick ник только этого окна; null — текущий аккаунт
     */
    public void launchGame(Path playDir, String tempNick) {
        if (isBusy()) return;
        boolean extra = cfg.developerMode && launch.aliveCount(cfg.minecraftVersion) > 0;
        if (!cfg.developerMode && launch.isActive(cfg.minecraftVersion)) return;
        bindPaths();
        GameStateSnapshot snap = evaluate();
        if (!extra && !snap.state().canLaunch()) {
            state.set(snap.state(), snap.detail());
            return;
        }
        Account account = accounts.active();
        if (account == null) {
            Log.warn("Запуск отменён: нет аккаунта");
            return;
        }
        if (tempNick != null && !tempNick.isBlank()) {
            if (!Account.validNick(tempNick.trim())) {
                Log.warn("Запуск отменён: временный ник не подходит");
                return;
            }
            Account guest = new Account(Account.Type.OFFLINE, tempNick.trim());
            guest.id = "temp-" + guest.uuid;
            account = guest;
            Log.info("Временный ник этого окна: " + guest.username);
        }
        Path folder = cfg.developerMode ? playDir : null;
        LauncherProfile profile = profiles.active();
        if (profile == null) {
            profile = profiles.create("Common World");
        }
        launch.markStarting(cfg.minecraftVersion);
        state.set(GameState.STARTING, "Minecraft запускается");
        Process[] started = new Process[1];
        Path[] log = new Path[1];
        Path[] natives = new Path[1];
        Operation op = launch.launchOperation(cfg, profile, account, snap.profileId(), folder, started, log, natives);
        op.onSuccess(() -> {
            Process game = started[0];
            Path gameLog = log[0];
            Path nativesDir = natives[0];
            javax.swing.SwingUtilities.invokeLater(() -> launch.watch(cfg.minecraftVersion, game, gameLog, nativesDir, () -> {
                refreshState();
                checkServer();
            }));
        });
        op.onError(t -> javax.swing.SwingUtilities.invokeLater(() -> {
            launch.failLaunch(cfg.minecraftVersion, started[0]);
            String message = Operation.friendly(t);
            if (launch.aliveCount(cfg.minecraftVersion) > 0) {
                state.set(GameState.RUNNING, "Minecraft запущен");
                if (launchFailedCallback != null) launchFailedCallback.fail(message);
            } else if (launchFailedCallback != null) {
                launchFailedCallback.fail(message);
            } else {
                state.set(GameState.ERROR, message);
            }
        }));
        submit("Запуск Minecraft", op);
    }

    public interface MessageCallback {
        void fail(String message);
    }

    private volatile MessageCallback launchFailedCallback;
    private volatile Runnable closeOnLaunchCallback;

    public void setLaunchFailedCallback(MessageCallback cb) {
        this.launchFailedCallback = cb;
    }

    public void setCloseOnLaunchCallback(Runnable r) {
        this.closeOnLaunchCallback = r;
    }

    /** Полностью закрывает запущенный Minecraft и возвращает кнопку «Запустить». */
    public void closeRunningGame() {
        io.submit(() -> {
            launch.stopVersion(cfg.minecraftVersion);
            javax.swing.SwingUtilities.invokeLater(this::refreshState);
        });
    }

    private final java.util.concurrent.atomic.AtomicBoolean serverBusy = new java.util.concurrent.atomic.AtomicBoolean();

    /** Статус сервера: живой запрос на commonworld.pterohost.ru:25965. Повтор не копится. */
    public void checkServer() {
        if (!serverBusy.compareAndSet(false, true)) return;
        io.submit(() -> {
            try {
                serverInfo = ServerStatus.check(Utils.isBlank(cfg.serverIp) ? Settings.SERVER : cfg.serverIp,
                        2000);
                lastServerCheck = System.currentTimeMillis();
                boolean skinChanged = ru.cw.launcher.skin.PlayerSkin.refresh(accounts.active());
                if (skinChanged || onStateChanged != null) {
                    if (onStateChanged != null) javax.swing.SwingUtilities.invokeLater(onStateChanged);
                }
            } finally {
                serverBusy.set(false);
            }
        });
    }

    public ServerStatus.Info serverInfo() {
        return serverInfo;
    }

    public long serverCheckedAt() {
        return lastServerCheck;
    }

    /** Синхронизация выбранного профиля с общими настройками (быстрый запуск, п.24). */
    public void syncProfileToSettings() {
        LauncherProfile p = profiles.active();
        if (p == null) return;
        if ("1.20.3".equals(p.minecraftVersion)) {
            p.minecraftVersion = "1.20.1";
            profiles.update(p);
        }
        if (!Utils.isBlank(p.minecraftVersion)) cfg.minecraftVersion = p.minecraftVersion;
        if (!Utils.isBlank(p.fabricLoader)) cfg.fabricLoaderVersion = p.fabricLoader;
        if (p.ramMb > 0) cfg.ramMb = p.ramMb;
        if (!Utils.isBlank(p.jvmArgs)) cfg.extraJvmArgs = p.jvmArgs;
        if (!Utils.isBlank(p.accountId)) cfg.activeAccountId = p.accountId;
        bindPaths();
    }

    public void saveSettings() {
        bindPaths();
        SettingsManager.save();
        background.reload(cfg);
    }

    public Account account() {
        return accounts.active();
    }

    public LauncherProfile profile() {
        return profiles.active();
    }

    /** Версия в lVersion.txt отличается от этой сборки. */
    public boolean launcherVersionDiffers() {
        return cfg.remoteLauncherVersion != null
                && UpdateChecker.launcherUpdateNeeded(Log.VERSION, cfg.remoteLauncherVersion);
    }

    /** Запускает Uninstall.exe рядом с CWLauncher.exe. Сам лаунчер после этого закрывает окно. */
    public boolean launchUninstaller() {
        Path root = Paths.installRoot();
        Path exe = root.resolve("Uninstall.exe");
        if (!Files.isRegularFile(exe)) {
            Log.error("Не найден Uninstall.exe: " + exe);
            return false;
        }
        try {
            new ProcessBuilder(exe.toString(), "--uninstall").directory(root.toFile()).start();
            Log.info("Запущен Uninstall.exe");
            return true;
        } catch (IOException e) {
            Log.error("Не удалось запустить Uninstall.exe: " + Log.reason(e), e);
            return false;
        }
    }

    /**
     * Обновление лаунчера делает отдельный CWLauncher-Update.exe.
     * Этот процесс только запускает его и закрывается.
     */
    public void handOffLauncherUpdate() {
        if (LauncherUpdater.launchExternalUpdater(cfg.launcherDownloadUrl)) {
            Log.info("Обновление лаунчера передано CWLauncher-Update.exe");
            shutdown();
            System.exit(0);
        }
        state.set(GameState.ERROR, "Рядом с лаунчером нет CWLauncher-Update.exe");
        if (onStateChanged != null) {
            javax.swing.SwingUtilities.invokeLater(onStateChanged);
        }
    }

    /** Ручной запуск обновления CWLauncher отдельным файлом. */
    public void beginLauncherUpdate() {
        String version = cfg.remoteLauncherVersion;
        if (version == null || version.isBlank()) {
            state.set(GameState.ERROR, "Актуальная версия лаунчера (lVersion.txt) неизвестна.");
            return;
        }
        handOffLauncherUpdate();
    }

    /**
     * Применение обновления лаунчера. Заменяется JAR внутри установки,
     * затем запускается CWLauncher.exe. Работающий EXE сам себя не перезаписывает.
     */
    public boolean applyLauncherUpdate() {
        Path target = Paths.updatableFile();
        if (target == null || !Files.isRegularFile(target)) {
            Log.error("Не найден JAR лаунчера для замены");
            return false;
        }
        Path exe = Paths.launcherExecutable();
        List<String> restart;
        if (exe != null) {
            restart = List.of(exe.toString());
        } else {
            String sep = System.getProperty("file.separator", "\\");
            String javaw = System.getProperty("java.home") + sep + "bin" + sep + "javaw.exe";
            if (!Files.isRegularFile(Path.of(javaw))) javaw = "javaw";
            restart = List.of(javaw, "-jar", target.toString());
        }
        boolean ok = updater.applyAndRestart(target, restart);
        if (ok) Log.info("Апгрейдер запущен, текущий процесс должен завершиться");
        return ok;
    }

    public List<Operation.Task> javaInstallTasks(int major) {
        return java.installTasks(major, listener());
    }

    private ProgressListener listener() {
        return new ProgressListener() {
            @Override
            public void stage(String title) {
                Operation op = current;
                if (op != null) op.progress().stage(title);
                else state.set(state.get(), title);
            }

            @Override
            public void progress(long done, long total, String text) {
                Operation op = current;
                if (op != null) op.progress().progress(done, total, text);
            }

            @Override
            public boolean isCancelled() {
                Operation op = current;
                return op != null && op.cancel().isCancelled();
            }

            @Override
            public void checkCancel() throws java.io.IOException {
                if (isCancelled()) throw new java.io.IOException("Отменено пользователем");
            }
        };
    }

    public void shutdown() {
        PlayTime.end();
        io.shutdownNow();
        SettingsManager.save();
        Log.flushNow();
    }
}
