package ru.cw.launcher.ui;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.background.BackgroundManager;
import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.core.Operation;
import ru.cw.launcher.core.Progress;
import ru.cw.launcher.model.GameState;
import ru.cw.launcher.network.ServerStatus;
import ru.cw.launcher.news.NewsManager;
import ru.cw.launcher.updater.UpdateChecker;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Главное окно по макету: слева Common World и новости, по центру запуск,
 * справа состояние и обновления. Профиль открывается кнопкой слева сверху.
 */
public class MainWindow extends JFrame {

    private static final int EDGE = 7;

    private final LauncherApp app;
    private Timer fpsTimer;
    private final BackgroundManager.View bg;
    private final Board board = new Board();

    private final SoftButton bSite = new SoftButton(Icons.Kind.PICKAXE, "Сайт сервера");
    private final SoftButton bTelegram = new SoftButton(Icons.Kind.TELEGRAM, "Telegram");
    private final SoftButton bDiscord = new SoftButton(Icons.Kind.DISCORD, "Discord");
    private final SoftButton bUser = new SoftButton(Icons.Kind.USER, "Профиль");
    private final ProfileButton bProfile = new ProfileButton();
    private final SoftButton bMin = new SoftButton(Icons.Kind.MIN, "Свернуть");
    private final SoftButton bClose = new SoftButton(Icons.Kind.CLOSE, "Закрыть");
    private final LaunchButton bMain = new LaunchButton(true);
    private final LaunchButton bCloseGame = new LaunchButton(false);
    private final VersionButton bVersion = new VersionButton();
    private final SoftButton bSupport = new SoftButton(Icons.Kind.INFO, "Тех. Поддержка");
    private final SoftButton bUpdates = new SoftButton(Icons.Kind.HISTORY, "Обновления");
    private final SoftButton bLatestUpdate = new SoftButton(Icons.Kind.HISTORY, "Обновление");
    private final SoftButton bFolder = new SoftButton(Icons.Kind.FOLDER, "Папка Minecraft");
    private final SoftButton bMods = new SoftButton(Icons.Kind.MODS, "Моды");
    private final SoftButton bSkin = new SoftButton(Icons.Kind.SKIN, "Скин");
    private final SoftButton bExit = new SoftButton(Icons.Kind.EXIT, "Выйти");
    private final CutButton bCancel = new CutButton("Отмена", CutButton.Accent.DANGER, false);
    private final SoftButton bPickVersion = new SoftButton(Icons.Kind.CHEVRON, "Выбор версии");
    private final SoftButton bSettingsMid = new SoftButton(Icons.Kind.SETTINGS, "Настройки");
    private final LaunchButton bUpdateLauncher = new LaunchButton(false);
    private final SoftButton bCopyIp = new SoftButton(Icons.Kind.COPY, "Скопировать адрес");
    private final JButton bNewsMore = new LinkButton("Подробнее");
    private final JButton bUpdatesMore = new LinkButton("Подробнее");

    private VersionPopup versionPopup;
    private Point press;
    private Rectangle origin;
    private int dragMode;
    private String fabricLine = "Fabric";
    private String modsLine = "Моды";
    private String buildLine = "";
    private String serverTitle = "Статус сервера недоступен";
    private String serverPlayers = "";
    private String serverAddress = "";
    private String newsTitle = "Новости недоступны";
    private String newsBody = "Источник новостей не настроен.";
    private String newsDate = "";
    private int updateCardY;
    private int updateCardH = 132;
    private int statusCardH;
    private Color serverTone = new Color(0x8EA0C0);
    private boolean mcReady;
    private boolean modsReady;
    private boolean fabricReady;
    private int modsInstalled;

    public MainWindow(LauncherApp app) {
        super("CWLauncher");
        this.app = app;
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        try {
            setIconImage(Icons.appIcon());
        } catch (Exception ignored) {
        }
        bg = app.background().new View();
        bg.setLayout(null);
        bg.setOpaque(true);
        bg.setBackground(new Color(0x0B1220));
        setContentPane(bg);
        getContentPane().setBackground(new Color(0x0B1220));
        bg.add(board);
        for (JComponent c : controls()) {
            bg.add(c);
        }
        // Индекс 0 рисуется последним и перекрывает кнопки. Доска должна быть сзади.
        bg.setComponentZOrder(board, bg.getComponentCount() - 1);
        bExit.setVisible(false);
        bSupport.setVisible(true);
        bProfile.setVisible(false);
        bNewsMore.setText(Lang.t("more"));
        bUpdatesMore.setText(Lang.t("more"));
        bSite.addActionListener(e -> openUrl("https://commonworld.ru/index.php"));
        bSettingsMid.addActionListener(e -> SettingsWindow.show(this, app));
        bUpdateLauncher.setText(Lang.t("upd_btn"));
        bUpdateLauncher.setVisible(false);
        bUpdateLauncher.addActionListener(e -> app.beginLauncherUpdate());
        app.setLauncherUpdateFocus(() -> {
            setExtendedState(Frame.NORMAL);
            setVisible(true);
            toFront();
            requestFocus();
            layoutAll();
            repaint();
        });
        bTelegram.addActionListener(e -> openUrl(app.cfg.telegramUrl));
        bDiscord.addActionListener(e -> openUrl(app.cfg.discordUrl));
        bUser.addActionListener(e -> ProfileWindow.show(this, app));
        bProfile.addActionListener(e -> ProfileWindow.show(this, app));
        bMin.addActionListener(e -> setExtendedState(ICONIFIED));
        bClose.addActionListener(e -> quit());
        bExit.addActionListener(e -> quit());
        bMain.addActionListener(e -> onMain());
        bCloseGame.setText(Lang.t("close_game"));
        bCloseGame.setAccent(new Color(0x3D4C63));
        bCloseGame.setVisible(false);
        bCloseGame.addActionListener(e -> app.closeRunningGame());
        bCancel.addActionListener(e -> app.cancelCurrent());
        bPickVersion.addActionListener(e -> openVersionPopup());
        bSupport.addActionListener(e -> openUrl(app.cfg.supportUrl));
        bFolder.addActionListener(e -> openFolder(Paths.gameDir()));
        bMods.addActionListener(e -> openFolder(Paths.mods(app.cfg.instanceId())));
        bSkin.addActionListener(e -> {
            Account a = app.account();
            if (a == null) {
                Dialogs.info(this, Lang.t("prof_need"));
                ProfileWindow.show(this, app);
                return;
            }
            openFolder(Paths.accountSkinDir(a.id));
        });
        bUpdates.addActionListener(e -> UpdatesWindow.show(this, app));
        bLatestUpdate.addActionListener(e -> UpdatesWindow.show(this, app));
        bCopyIp.addActionListener(e -> copyServerAddress());
        bNewsMore.addActionListener(e -> showNews());
        bUpdatesMore.addActionListener(e -> UpdatesWindow.show(this, app));
        applyLanguage();

        app.onStateChanged(this::syncFromState);
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(cursorFor(hit(e)));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragMode = hit(e);
                press = e.getLocationOnScreen();
                origin = getBounds();
                setCursor(cursorFor(dragMode));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (press == null || origin == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - press.x;
                int dy = now.y - press.y;
                Rectangle r = new Rectangle(origin);
                Dimension min = getMinimumSize();
                if (dragMode == 0) {
                    if (e.getY() > 78) return;
                    setLocation(origin.x + dx, origin.y + dy);
                    return;
                }
                if ((dragMode & 1) != 0) {
                    int nh = r.height - dy;
                    if (nh >= min.height) {
                        r.y += dy;
                        r.height = nh;
                    }
                }
                if ((dragMode & 2) != 0) r.height = Math.max(min.height, r.height + dy);
                if ((dragMode & 4) != 0) {
                    int nw = r.width - dx;
                    if (nw >= min.width) {
                        r.x += dx;
                        r.width = nw;
                    }
                }
                if ((dragMode & 8) != 0) r.width = Math.max(min.width, r.width + dx);
                setBounds(r);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragMode = 0;
                setCursor(Cursor.getDefaultCursor());
            }
        };
        board.addMouseListener(drag);
        board.addMouseMotionListener(drag);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutAll();
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                quit();
            }

            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                app.checkServer();
            }
        });
        setMinimumSize(new Dimension(980, 620));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(1120, Math.max(980, (int) (screen.width * 0.62)));
        int h = Math.min(700, Math.max(620, (int) (screen.height * 0.68)));
        setSize(w, h);
        setLocationRelativeTo(null);
        layoutAll();
        syncFromState();
        Timer timer = new Timer(200, e -> syncFromState());
        timer.start();
    }

    private JComponent[] controls() {
        return new JComponent[]{bSite, bDiscord, bTelegram, bUser, bCopyIp, bLatestUpdate, bProfile, bMin, bClose, bMain,
                bCloseGame, bVersion, bSupport, bFolder, bMods, bSkin, bExit, bCancel, bPickVersion, bSettingsMid,
                bUpdateLauncher,
                bNewsMore, bUpdatesMore};
    }

    private int hit(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        int w = board.getWidth();
        int h = board.getHeight();
        boolean left = x <= EDGE;
        boolean right = x >= w - EDGE;
        boolean top = y <= EDGE;
        boolean bottom = y >= h - EDGE;
        int mode = 0;
        if (top) mode |= 1;
        if (bottom) mode |= 2;
        if (left) mode |= 4;
        if (right) mode |= 8;
        return mode;
    }

    private static Cursor cursorFor(int mode) {
        return switch (mode) {
            case 1, 2 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case 4, 8 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case 1 | 4, 2 | 8 -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case 1 | 8, 2 | 4 -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    /** Главное меню всегда как на макете: карточки по бокам, запуск по центру. */
    private boolean focusLayout() {
        return false;
    }

    private void showNews() {
        List<NewsManager.Item> items = app.news().items();
        if (items.isEmpty()) {
            Dialogs.info(this, "Новости пока недоступны.");
            return;
        }
        NewsWindow.show(this, items);
    }

    private void measureColumn(int w, int h) {
        int colW = Math.max(210, Math.min(270, (w - 70) / 4));
        int top = 108;
        int bottom = h - 132;
        updateCardH = 132;
        updateCardY = bottom - updateCardH;
        statusCardH = updateCardY - 12 - top;
        if (statusCardH < 150) {
            updateCardH = 116;
            updateCardY = bottom - updateCardH;
            statusCardH = Math.max(96, updateCardY - 12 - top);
        }
    }

    private void layoutAll() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        board.setBounds(0, 0, w, h);
        if (bg.getComponentCount() > 0 && bg.getComponentZOrder(board) != bg.getComponentCount() - 1) {
            bg.setComponentZOrder(board, bg.getComponentCount() - 1);
        }
        int s = 46;
        int y = 16;
        bSite.setBounds(22, y, s, s);
        bTelegram.setBounds(22 + s + 10, y, s, s);
        bDiscord.setBounds(22 + (s + 10) * 2, y, s, s);
        bUser.setBounds(22 + (s + 10) * 3, y, s, s);
        bClose.setBounds(w - 18 - 36, 16, 36, 32);
        bMin.setBounds(w - 18 - 36 - 8 - 36, 16, 36, 32);
        boolean busy = app.state().get().isBusy();
        bNewsMore.setVisible(true);
        bUpdatesMore.setVisible(true);
        bPickVersion.setVisible(!busy);
        bSettingsMid.setVisible(!busy);
        bCancel.setVisible(busy);

        int colW = Math.max(210, Math.min(270, (w - 70) / 4));
        int top = 108;
        int bottom = h - 132;
        int gap = 16;
        int leftX = 22;
        int rightX = w - 22 - colW;
        int centerX = leftX + colW + gap;
        int centerW = Math.max(240, rightX - gap - centerX);
        bNewsMore.setBounds(leftX + colW - 108, bottom - 26, 96, 18);
        placeCopyButton();
        measureColumn(w, h);
        bUpdatesMore.setBounds(rightX + colW - 126, updateCardY + updateCardH - 28, 110, 18);
        bLatestUpdate.setBounds(rightX + 14, updateCardY + 44, colW - 28, 38);
        bUpdates.setVisible(false);
        bVersion.setBounds(centerX + 8, top + 4, centerW - 16, 74);
        int after = bVersion.getY() + 74 + (busy ? 118 : 34);
        boolean gamesUp = app.launch.aliveCount(app.cfg.minecraftVersion) > 0;
        boolean split = app.state().get() == GameState.RUNNING
                || (app.cfg.developerMode && gamesUp);
        bCloseGame.setVisible(split);
        if (split) {
            int buttonGap = 10;
            int half = Math.max(120, (centerW - 16 - buttonGap) / 2);
            bMain.setBounds(centerX + 8, after, half, 54);
            bCloseGame.setBounds(centerX + 8 + half + buttonGap, after, centerW - 16 - half - buttonGap, 54);
        } else {
            bMain.setBounds(centerX + 8, after, centerW - 16, 54);
        }
        bPickVersion.setBounds(centerX + 8, bMain.getY() + 64, centerW - 16, 44);
        int settingsW = Math.min(220, centerW - 48);
        int settingsX = centerX + (centerW - settingsW) / 2;
        bSettingsMid.setBounds(settingsX, bPickVersion.getY() + 52, settingsW, 40);
        boolean offerUpdate = app.launcherUpdateButton();
        bUpdateLauncher.setVisible(offerUpdate);
        bUpdateLauncher.setBounds(settingsX, bSettingsMid.getY() + 48, settingsW, 40);
        bCancel.setBounds(centerX + 8, bMain.getY() + 64, centerW - 16, 44);
        boolean fabricOn = app.cfg.commonWorld || app.cfg.fabricFor(app.cfg.minecraftVersion);
        bMods.setVisible(fabricOn);
        int tiles = fabricOn ? 3 : 2;
        int tileH = 70;
        int tileY = h - tileH - 42;
        int supportW = 196;
        int supportLeft = (w - supportW) / 2;
        int avail = Math.max(180, supportLeft - 16 - leftX);
        int gaps = (tiles - 1) * 8;
        int tileW = Math.max(64, Math.min(96, (avail - gaps) / tiles));
        bFolder.setBounds(leftX, tileY, tileW, tileH);
        int nextX = leftX + tileW + 8;
        if (fabricOn) {
            bMods.setBounds(nextX, tileY, tileW, tileH);
            nextX += tileW + 8;
        }
        bSkin.setBounds(nextX, tileY, tileW, tileH);
        int supportH = 32;
        bSupport.setBounds(supportLeft, tileY + (tileH - supportH) / 2, supportW, supportH);
        bSupport.setVisible(true);
        repaint();
    }

    public void syncFromState() {
        GameState st = app.state().get();
        boolean busy = st.isBusy();
        boolean devWindows = app.cfg.developerMode && app.launch.aliveCount(app.cfg.minecraftVersion) > 0;
        String mainText = labelFor(st);
        if (st == GameState.UPDATE_AVAILABLE && app.pendingGameUpdate()) mainText = Lang.t("update_mc");
        else if (st == GameState.UPDATE_AVAILABLE && modsUpdateButton()) mainText = Lang.t("update_mods");
        else if (devWindows && st == GameState.RUNNING) mainText = Lang.t("launch");
        bMain.setText(mainText);
        bMain.setLive(st == GameState.RUNNING);
        boolean locked = st == GameState.STARTING || (st == GameState.RUNNING && !devWindows);
        bMain.setEnabled(!busy && !locked);
        bMain.setCursor(locked || busy
                ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bMain.setAccent(st == GameState.ERROR ? new Color(0xE0524D)
                : st == GameState.UPDATE_AVAILABLE ? new Color(0x2F6FE0) : new Color(0x2F6FE0));
        mcReady = app.mc().versionReady(app.cfg.minecraftVersion);
        modsReady = app.mods().isInstalled(app.cfg.instanceId());
        String fabric = app.fabric().installedLoader(app.cfg.minecraftVersion);
        boolean fabricOn = app.cfg.commonWorld || app.cfg.fabricFor(app.cfg.minecraftVersion);
        fabricReady = fabric != null;
        fabricLine = !fabricOn ? "" : (fabric == null ? "Fabric" : "Fabric " + fabric);
        int mods = app.mods().modCount(app.cfg.instanceId());
        String build = app.mods().installedBuildVersion(app.cfg.instanceId());
        buildLine = build == null ? "" : build;
        modsInstalled = mods;
        modsLine = mods <= 0 ? Lang.t("mods_missing") : String.valueOf(mods);
        if (app.cfg.commonWorld) bVersion.setLines("Сервер Common World", "Minecraft 1.20.1");
        else bVersion.setLines("Minecraft " + app.cfg.minecraftVersion, fabricOn ? fabricLine : "");
        Account ac = app.account();
        String name = ac == null || Utils.isBlank(ac.username) ? "Игрок" : ac.username;
        bProfile.setCaption(name);
        if (ac != null && ac.type == Account.Type.ELY) {
            bUser.setToolTipText(name + " · Ely.by");
        } else {
            bUser.setToolTipText(Lang.t("profile"));
        }
        java.awt.image.BufferedImage skinFace = ru.cw.launcher.skin.PlayerSkin.face();
        bUser.setFace(skinFace);
        bSkin.setFace(skinFace);
        ServerStatus.Info srv = app.serverInfo();
        String ip = Utils.isBlank(app.cfg.serverIp) ? "" : app.cfg.serverIp;
        serverAddress = ip.isBlank() ? "" : "IP: " + ip;
        if (srv == null || !srv.available()) {
            serverTitle = Lang.t("srv_down");
            serverPlayers = ip.isBlank() ? Lang.t("no_addr") : "";
            serverTone = new Color(0x8EA0C0);
        } else if (srv.online()) {
            serverTitle = Lang.t("srv_on");
            serverPlayers = Lang.t("players") + srv.players() + "/" + srv.maxPlayers();
            serverTone = new Color(0x3DDC97);
        } else {
            serverTitle = Lang.t("srv_off");
            serverPlayers = srv.error() == null ? "" : srv.error();
            serverTone = new Color(0xF0B429);
        }
        placeCopyButton();
        List<NewsManager.Item> items = app.news().items();
        if (items.isEmpty()) {
            newsTitle = app.news().failed() ? Lang.t("news_down") : Lang.t("news");
            newsBody = app.news().failed() ? Lang.t("news_cache") : Lang.t("news_none");
            newsDate = "";
        } else {
            NewsManager.Item it = items.get(0);
            newsTitle = it.title();
            newsBody = it.text() == null ? "" : it.text();
            newsDate = it.date() == null ? "" : it.date();
        }
        List<ru.cw.launcher.updates.UpdateParser.Note> updateNotes = app.updateInfo().cached();
        if (updateNotes.isEmpty()) {
            bLatestUpdate.setText(Lang.t("update"));
            bLatestUpdate.setEnabled(false);
        } else {
            bLatestUpdate.setText(updateNotes.get(0).title());
            bLatestUpdate.setEnabled(true);
        }
        bVersion.setEnabled(!busy);
        layoutAll();
        board.repaint();
        if (UiLook.showFps()) {
            if (fpsTimer == null) {
                fpsTimer = new Timer(33, e -> board.repaint());
                fpsTimer.start();
            } else if (!fpsTimer.isRunning()) {
                fpsTimer.start();
            }
        } else if (fpsTimer != null) {
            fpsTimer.stop();
        }
    }

    private boolean modsNeedUpdate() {
        return ru.cw.launcher.settings.UpdateSchedule.offerMods(app.cfg)
                && app.cfg.commonWorldMods() && app.cfg.remoteBuildVersion != null
                && UpdateChecker.buildUpdateNeeded(
                app.mods().installedBuildVersion(app.cfg.instanceId()),
                app.cfg.remoteBuildVersion);
    }

    private boolean modsUpdateButton() {
        if (!modsNeedUpdate()) return false;
        boolean game = !app.mc().versionReady(app.cfg.minecraftVersion)
                || ("fabric".equalsIgnoreCase(app.cfg.loader)
                && app.fabric().installedLoader(app.cfg.minecraftVersion) == null);
        return !game;
    }

    public void applyLanguage() {
        Lang.use(app.cfg.resolvedLanguage());
        bSite.setToolTipText(Lang.t("site"));
        bUser.setToolTipText(Lang.t("profile"));
        bCopyIp.setToolTipText(Lang.t("copy_addr"));
        bMin.setToolTipText(Lang.t("min"));
        bClose.setToolTipText(Lang.t("close"));
        bSupport.setText(Lang.t("support"));
        bSupport.setToolTipText(Lang.t("support"));
        bUpdates.setText(Lang.t("updates"));
        bLatestUpdate.setText(Lang.t("update"));
        bFolder.stackCaption(Lang.t("folder"));
        bMods.stackCaption(Lang.t("mods"));
        bSkin.stackCaption(Lang.t("skin"));
        bCancel.setText(Lang.t("cancel"));
        bPickVersion.setText(Lang.t("version"));
        bSettingsMid.setText(Lang.t("settings"));
        bUpdateLauncher.setText(Lang.t("upd_btn"));
        bNewsMore.setText(Lang.t("more"));
        bUpdatesMore.setText(Lang.t("more"));
        bCloseGame.setText(Lang.t("close_game"));
        syncFromState();
        repaint();
    }

    private static String labelFor(GameState st) {
        return switch (st) {
            case READY, OFFLINE -> Lang.t("launch");
            case NOT_INSTALLED -> Lang.t("install");
            case UPDATE_AVAILABLE -> Lang.t("update_btn");
            case ERROR -> Lang.t("check");
            case CHECKING -> Lang.t("checking");
            case STARTING -> Lang.t("starting");
            case RUNNING -> Lang.t("running");
            default -> Lang.t("go");
        };
    }

    private void onMain() {
        GameState st = app.state().get();
        if (st.isBusy()) {
            if (Dialogs.ask(this, "Отменить текущую операцию? Незавершённый файл будет удалён.",
                    "Отменить", "Продолжить")) {
                app.cancelCurrent();
            }
            return;
        }
        switch (st) {
            case READY, OFFLINE, RUNNING -> {
                if (st == GameState.RUNNING && !app.cfg.developerMode) return;
                if (!requireAccount()) return;
                if (app.cfg.developerMode) {
                    boolean askNick = app.launch.aliveCount(app.cfg.minecraftVersion) > 0 || st == GameState.RUNNING;
                    DevLaunchDialog.Choice choice = DevLaunchDialog.ask(this, askNick);
                    if (choice == null) return;
                    app.launchGame(choice.dir, choice.nick);
                } else {
                    app.launchGame();
                }
            }
            case NOT_INSTALLED -> {
                if (!requireAccount()) return;
                while (true) {
                    java.nio.file.Path dir = Dialogs.chooseInstallRoot(this, app.cfg.minecraftVersion);
                    if (dir == null) return;
                    if (Utils.writable(dir)) {
                        app.rememberVersionDir(app.cfg.minecraftVersion, dir);
                        app.saveSettings();
                        app.install();
                        return;
                    }
                    Dialogs.info(this, Utils.writeDeniedMessage(dir));
                }
            }
            case UPDATE_AVAILABLE -> {
                if (!requireAccount()) return;
                if (app.pendingGameUpdate()) app.updateMinecraftAndLaunch();
                else if (modsNeedUpdate()) app.updateModsAndLaunch();
                else app.launchGame();
            }
            case ERROR -> {
                Operation op = app.verifyOperation();
                op.onError(t -> SwingUtilities.invokeLater(() -> {
                    if (Dialogs.ask(this, Operation.friendly(t) + "\nВосстановить сборку?",
                            "Восстановить", "Закрыть")) {
                        new RepairWindow(this, app).setVisible(true);
                    }
                }));
                if (!app.submit("Проверка сборки", op)) {
                    Dialogs.info(this, "Сейчас уже выполняется другая операция.");
                }
            }
            default -> {
            }
        }
    }

    private boolean requireAccount() {
        if (app.account() != null) return true;
        Dialogs.info(this, Lang.t("prof_need"));
        ProfileWindow.show(this, app);
        return app.account() != null;
    }

    private void placeCopyButton() {
        int w = Math.max(1, getWidth());
        int colW = Math.max(210, Math.min(270, (w - 70) / 4));
        int top = 108;
        int baseline = top + (serverPlayers == null || serverPlayers.isBlank() ? 74 : 92);
        bCopyIp.setBounds(22 + colW - 38, baseline - 18, 26, 26);
        bCopyIp.setVisible(serverAddress != null && !serverAddress.isBlank());
    }

    private void copyServerAddress() {
        String ip = Utils.isBlank(app.cfg.serverIp) ? "" : app.cfg.serverIp.trim();
        if (ip.isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ip), null);
        bCopyIp.flash(Icons.Kind.CHECK);
        bCopyIp.setToolTipText(Lang.t("copied"));
        Timer back = new Timer(1000, e -> bCopyIp.setToolTipText(Lang.t("copy_addr")));
        back.setRepeats(false);
        back.start();
    }

    private void openVersionPopup() {
        if (versionPopup == null) {
            versionPopup = new VersionPopup(this, app, this::syncFromState);
            versionPopup.ignoreOutside(bPickVersion);
        }
        if (versionPopup.isVisible()) {
            versionPopup.setVisible(false);
            return;
        }
        int top = bPickVersion.isShowing()
                ? bPickVersion.getLocationOnScreen().y
                : bVersion.getLocationOnScreen().y + bVersion.getHeight();
        int bottom = bSupport.isShowing()
                ? bSupport.getLocationOnScreen().y - 8
                : getLocationOnScreen().y + getHeight() - 40;
        versionPopup.showBelow(bVersion, top, bottom);
    }

    private void openUrl(String url) {
        if (!Utils.openUrl(url)) Dialogs.error(this, "Не удалось открыть ссылку в браузере.", url, null);
    }

    private void openFolder(java.nio.file.Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            Log.warn("Папка: " + Log.reason(e));
        }
        if (!Utils.openFolder(dir)) Dialogs.error(this, "Не удалось открыть папку.", String.valueOf(dir), null);
    }

    public void quit() {
        if (app.isBusy() && !Dialogs.ask(this, "Идёт операция. Выйти и прервать её?", "Выйти", "Отмена")) return;
        app.shutdown();
        dispose();
        System.exit(0);
    }

    public void showStartup() {
        getRootPane().setOpaque(true);
        getRootPane().setBackground(new Color(0x0B1220));
        getLayeredPane().setOpaque(true);
        bg.setOpaque(true);
        setVisible(true);
        // Иначе Windows оставляет неотрисованный буфер: окно есть, картинки нет.
        Dimension s = getSize();
        setSize(s.width + 1, s.height + 1);
        setSize(s);
        toFront();
        requestFocus();
        Utils.nameOnTaskbar(this);
        bg.startAnimation();
        syncFromState();
        repaint();
        Toolkit.getDefaultToolkit().sync();
        Log.info("Главное окно: opaque=" + isOpaque() + ", opacity=" + getOpacity()
                + ", " + getWidth() + "x" + getHeight());
    }

    public LauncherApp app() {
        return app;
    }

    private final class Board extends JPanel {
        private java.awt.image.BufferedImage cover;
        private java.awt.image.BufferedImage coverSource;
        private int coverW;
        private int coverH;

        Board() {
            setOpaque(false);
        }

        /** Картинка под размер окна считается один раз и дальше только копируется. */
        private java.awt.image.BufferedImage cover(java.awt.image.BufferedImage photo, int w, int h) {
            if (cover != null && coverSource == photo && coverW == w && coverH == h) return cover;
            java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(
                    Math.max(1, w), Math.max(1, h), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D cg = frame.createGraphics();
            cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.max((double) w / photo.getWidth(), (double) h / photo.getHeight());
            int dw = (int) Math.ceil(photo.getWidth() * scale);
            int dh = (int) Math.ceil(photo.getHeight() * scale);
            cg.drawImage(photo, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            cg.dispose();
            cover = frame;
            coverSource = photo;
            coverW = w;
            coverH = h;
            return frame;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setComposite(AlphaComposite.SrcOver);
            java.awt.image.BufferedImage photo = app.background().picture();
            if (photo != null && w > 0 && h > 0) {
                g2.drawImage(cover(photo, w, h), 0, 0, null);
            }

            GameState st = app.state().get();
            boolean busy = st.isBusy();
            boolean focus = false;

            Font titleFont = Theme.H1.deriveFont(Font.BOLD, 34f);
            g2.setFont(titleFont);
            String a = "CW ";
            String b = "Launcher";
            int tw = g2.getFontMetrics().stringWidth(a + b);
            int tx = (w - tw) / 2;
            int ty = 52;
            g2.setColor(Color.WHITE);
            g2.drawString(a, tx, ty);
            g2.setColor(new Color(0x4C8DFF));
            g2.drawString(b, tx + g2.getFontMetrics().stringWidth(a), ty);
            g2.setFont(Theme.H3.deriveFont(Font.PLAIN, 16f));
            g2.setColor(new Color(0x9BB4E0));
            String sub = "Common World";
            g2.drawString(sub, (w - g2.getFontMetrics().stringWidth(sub)) / 2, ty + 24);

            int left = 22;
            int colW = Math.max(210, Math.min(270, (w - 70) / 4));
            int top = 108;
            int bottom = h - 132;
            int gap = 16;
            int rightX = w - 22 - colW;
            int centerX = left + colW + gap;
            int centerW = Math.max(240, rightX - gap - centerX);
            measureColumn(w, h);
            int worldH = Math.max(128, (bottom - top) * 40 / 100);

            if (!focus) {
            card(g2, left, top, colW, worldH);
            card(g2, left, top + worldH + 12, colW, bottom - (top + worldH + 12));
            card(g2, rightX, top, colW, statusCardH);
            card(g2, rightX, updateCardY, colW, updateCardH);

            crown(g2, left + 14, top + 12);
            g2.setFont(Theme.H3.deriveFont(Font.BOLD, 15f));
            g2.setColor(Color.WHITE);
            g2.drawString("Common World", left + 38, top + 28);
            g2.setColor(serverTone);
            g2.fillOval(left + 16, top + 44, 8, 8);
            g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 13));
            g2.drawString(fit(g2, serverTitle, colW - 46), left + 30, top + 52);
            g2.setColor(new Color(0xC5D4EA));
            g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 12));
            if (!serverPlayers.isBlank()) {
                g2.drawString(fit(g2, serverPlayers, colW - 32), left + 16, top + 74);
            }
            if (!serverAddress.isBlank()) {
                g2.drawString(fit(g2, serverAddress, colW - 58), left + 16, top + (serverPlayers.isBlank() ? 74 : 92));
            }

            Icons.of(Icons.Kind.NEWS, 14).paint(new Color(0x4C8DFF), g2, left + 14, top + worldH + 22, 14, 14);
            g2.setColor(new Color(0x4C8DFF));
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 13));
            g2.drawString(Lang.t("news"), left + 34, top + worldH + 34);
            Shape newsClip = g2.getClip();
            g2.clipRect(left + 8, top + worldH + 8, colW - 16, Math.max(20, bottom - (top + worldH) - 16));
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 13));
            int newsCursor = drawWrap(g2, newsTitle, left + 16, top + worldH + 52, colW - 32, bottom - 34);
            g2.setColor(new Color(0x9BB0D0));
            g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 12));
            drawWrap(g2, newsBody, left + 16, newsCursor + 8, colW - 32, bottom - 34);
            g2.setClip(newsClip);
            }
            if (!focus) {
            String head = switch (st) {
                case ERROR -> Lang.t("status_err");
                case UPDATE_AVAILABLE -> Lang.t("status_info");
                case DOWNLOADING, INSTALLING, UPDATING, REPAIRING, CHECKING -> Lang.t("status_work");
                default -> Lang.t("status_build");
            };
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.H3.deriveFont(Font.BOLD, 14f));
            g2.drawString(head, rightX + 16, top + 28);
            int row = top + 52;
            boolean pack = app.cfg.commonWorldMods();
            boolean fabricOn = app.cfg.commonWorld || app.cfg.fabricFor(app.cfg.minecraftVersion);
            boolean modsOk = pack && modsReady && st != GameState.ERROR && st != GameState.NOT_INSTALLED;
            boolean serverOk = serverTone.getGreen() > 180;
            if (st == GameState.ERROR) {
                row = statusLine(g2, rightX, row, colW, false,
                        "Сборка: " + Utils.shorten(app.state().detail(), 36));
            } else if (st == GameState.UPDATE_AVAILABLE) {
                row = statusLine(g2, rightX, row, colW, false, "Доступно обновление");
                row = statusLine(g2, rightX, row, colW, true, "Лаунчер " + Log.VERSION);
            } else if (busy) {
                Operation op = app.currentOperation();
                Progress p = op == null ? null : op.progress();
                String stage = p == null ? app.state().detail() : p.stage();
                row = statusLine(g2, rightX, row, colW, false, Utils.shorten(stage, 40));
            }
            row = statusLine(g2, rightX, row, colW, mcReady, "Minecraft " + app.cfg.minecraftVersion);
            if (fabricOn) {
                row = statusLine(g2, rightX, row, colW, fabricReady,
                        fabricReady ? fabricLine : Lang.t("fabric_missing"));
            }
            if (pack) {
                String modsText = modsInstalled <= 0 ? Lang.t("mods_missing") : Lang.t("mods") + " (" + modsInstalled + ")";
                row = statusLine(g2, rightX, row, colW, modsOk, modsText);
                if (!buildLine.isBlank()) {
                    row = statusLine(g2, rightX, row, colW, modsOk, "Версия модов: " + buildLine);
                }
            }
            statusLine(g2, rightX, row, colW, serverOk,
                    serverOk ? "Сервер доступен" : serverTitle);

            int newsTop = updateCardY;
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 14));
            g2.drawString(Lang.t("latest"), rightX + 16, newsTop + 26);
            }

            if (busy) {
                Operation op = app.currentOperation();
                Progress p = op == null ? null : op.progress();
                int barX = bMain.getX();
                int barW = Math.max(40, bMain.getWidth());
                int barY = bVersion.getY() + bVersion.getHeight() + 16;
                String stage = p == null ? "Подготовка…" : (Utils.isBlank(p.stage()) ? "Подготовка…" : p.stage());
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.H3.deriveFont(Font.BOLD, 14f));
                String shown = fit(g2, stage, barW);
                g2.drawString(shown, barX + (barW - g2.getFontMetrics().stringWidth(shown)) / 2, barY);
                barY += 14;
                g2.setColor(new Color(0x162033));
                g2.fillRoundRect(barX, barY, barW, 12, 10, 10);
                int pct = -1;
                if (p != null && p.total() > 0) {
                    pct = (int) Math.min(100, Math.max(0, p.done() * 100 / p.total()));
                } else if (op != null && op.percent() >= 0) {
                    pct = op.percent();
                }
                if (pct >= 0) {
                    int fill = Math.max(8, barW * Math.min(100, pct) / 100);
                    g2.setColor(new Color(0x3B82F6));
                    g2.fillRoundRect(barX, barY, fill, 12, 10, 10);
                    String label = pct + "%";
                    g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 12));
                    g2.setColor(Color.WHITE);
                    g2.drawString(label, barX + (barW - g2.getFontMetrics().stringWidth(label)) / 2, barY + 28);
                } else {
                    int shift = (int) ((System.currentTimeMillis() / 12) % barW);
                    g2.setColor(new Color(0x3B82F6));
                    g2.fillRoundRect(barX + Math.max(0, shift - 50), barY, 50, 12, 10, 10);
                    g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 12));
                    g2.setColor(new Color(0x9BB0D0));
                    String label = "размер неизвестен";
                    g2.drawString(label, barX + (barW - g2.getFontMetrics().stringWidth(label)) / 2, barY + 28);
                }
                String speed = (p != null && p.speed() >= 1)
                        ? Utils.humanSize((long) p.speed()) + "/с"
                        : "считаю...";
                String eta = (p != null && p.etaSeconds() >= 0) ? p.etaText() : "считаю...";
                String meta = "Скорость: " + speed + "    Осталось: " + eta;
                if (p != null && p.total() > 0 && p.done() > 0) {
                    meta = Utils.humanSize(p.done()) + " / " + Utils.humanSize(p.total()) + "    " + meta;
                }
                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 13));
                String line = fit(g2, meta, barW);
                g2.drawString(line, barX + (barW - g2.getFontMetrics().stringWidth(line)) / 2, barY + 50);
            } else {
                String hint = switch (st) {
                    case READY -> "Готово к запуску";
                    case OFFLINE -> "Оффлайн, локальная сборка готова";
                    case ERROR -> {
                        String d = app.state().detail();
                        if (d != null && (d.contains("Нет прав") || d.contains("Program Files"))) yield d;
                        yield "Обнаружены ошибки в модах";
                    }
                    case UPDATE_AVAILABLE -> app.pendingGameUpdate() ? Lang.t("update_mc_h")
                            : modsUpdateButton() ? Lang.t("update_mods_h") : Lang.t("update_btn");
                    case NOT_INSTALLED -> {
                        String d = app.state().detail();
                        yield d == null || d.isBlank() ? "Сборка не установлена" : d;
                    }
                    case STARTING -> "Запускается";
                    case RUNNING -> "Запущен";
                    default -> app.state().detail();
                };
                Color c = switch (st) {
                    case READY -> new Color(0x3DDC97);
                    case ERROR -> new Color(0xFF6B6B);
                    case UPDATE_AVAILABLE, OFFLINE -> new Color(0xF0B429);
                    default -> new Color(0x9BB0D0);
                };
                g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 13));
                int hw = g2.getFontMetrics().stringWidth(hint);
                int hy = focus ? bMain.getY() + bMain.getHeight() + 28
                        : bVersion.getY() + bVersion.getHeight() + 26;
                int hx = focus ? (w - hw) / 2 : centerX + (centerW - hw) / 2;
                g2.setColor(c);
                g2.fillOval(hx - 16, hy - 9, 8, 8);
                g2.drawString(hint, hx, hy);
            }

            g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 12));
            String footSrv = srvFooter();
            g2.setColor(serverTone);
            g2.fillOval(24, h - 22, 8, 8);
            g2.setColor(new Color(0xC5D2E8));
            g2.drawString(footSrv, 38, h - 14);
            int modsX = 38 + g2.getFontMetrics().stringWidth(footSrv) + 22;
            Icons.of(Icons.Kind.MODS, 12).paint(new Color(0x3DDC97), g2, modsX, h - 24, 12, 12);
            String modsFoot = Lang.t("foot_mods") + ": " + modsFooter(st);
            boolean modsFresh = modsAreCurrent(st);
            g2.setColor(modsFresh ? new Color(0x3DDC97) : new Color(0xC5D2E8));
            g2.drawString(modsFoot, modsX + 16, h - 14);
            String ver = "CWLauncher v" + Log.VERSION;
            if (UiLook.showFps()) ver = UiLook.fpsTick() + " FPS   " + ver;
            String spent = ru.cw.launcher.core.PlayTime.format(
                    ru.cw.launcher.core.PlayTime.shownSeconds(), app.cfg.playTimeUnit);
            g2.setColor(new Color(0x8EA0C0));
            int verX = w - 22 - g2.getFontMetrics().stringWidth(ver);
            g2.drawString(ver, verX, h - 14);
            g2.drawString(spent, verX - 16 - g2.getFontMetrics().stringWidth(spent), h - 14);
            g2.dispose();
        }

        private String srvFooter() {
            ServerStatus.Info srv = app.serverInfo();
            if (srv != null && srv.available() && srv.online()) {
                return Lang.t("foot_on") + srv.players() + "/" + srv.maxPlayers();
            }
            if (srv != null && srv.available()) return Lang.t("foot_off");
            return Lang.t("foot_none");
        }

        private boolean modsAreCurrent(GameState st) {
            return app.cfg.commonWorldMods()
                    && (st == GameState.READY || st == GameState.OFFLINE);
        }

        private String modsFooter(GameState st) {
            if (!app.cfg.commonWorldMods()) {
                if (st == GameState.READY || st == GameState.OFFLINE || st == GameState.RUNNING) return Lang.t("mods_need");
            }
            if (st == GameState.ERROR) return Lang.t("mods_attn");
            if (st == GameState.NOT_INSTALLED) return Lang.t("mods_na");
            if (st == GameState.UPDATE_AVAILABLE) return Lang.t("mods_upd");
            if (st == GameState.READY || st == GameState.OFFLINE) return Lang.t("mods_fresh");
            return st.buttonLabel;
        }

        private int modsCount() {
            String digits = modsLine.replaceAll("\\D+", " ").trim();
            if (digits.isBlank()) return 0;
            try {
                return Integer.parseInt(digits.split(" ")[0]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private int statusLine(Graphics2D g2, int x, int y, int w, boolean ok, String text) {
            int cx = x + 16;
            int cy = y - 11;
            g2.setColor(ok ? new Color(0x3DDC97) : new Color(0xE0524D));
            g2.fillOval(cx, cy, 14, 14);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (ok) {
                g2.drawLine(cx + 3, cy + 7, cx + 6, cy + 10);
                g2.drawLine(cx + 6, cy + 10, cx + 11, cy + 4);
            } else {
                g2.drawLine(cx + 4, cy + 4, cx + 10, cy + 10);
                g2.drawLine(cx + 10, cy + 4, cx + 4, cy + 10);
            }
            g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 13));
            g2.setColor(Color.WHITE);
            g2.drawString(fit(g2, text == null ? "" : text, w - 44), x + 36, y);
            return y + 24;
        }

        private void crown(Graphics2D g2, int x, int y) {
            g2.setColor(new Color(0x4C8DFF));
            int[] xs = {x, x + 4, x + 8, x + 12, x + 16, x + 16, x};
            int[] ys = {y + 8, y + 2, y + 8, y + 2, y + 8, y + 13, y + 13};
            g2.fillPolygon(xs, ys, xs.length);
        }

        private void card(Graphics2D g2, int x, int y, int w, int h) {
            if (w < 20 || h < 20) return;
            g2.setColor(new Color(8, 18, 36, 210));
            g2.fillRoundRect(x, y, w, h, 18, 18);
            g2.setColor(new Color(76, 150, 255, 190));
            g2.drawRoundRect(x, y, w - 1, h - 1, 18, 18);
        }

        private String fit(Graphics2D g2, String text, int width) {
            if (text == null) return "";
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(text) <= width) return text;
            String s = text;
            while (s.length() > 1 && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        /** Переносит текст по словам внутри колонки. Возвращает базовую линию следующей строки. */
        private int drawWrap(Graphics2D g2, String text, int x, int y, int width, int maxBaseline) {
            if (text == null || width < 8) return y;
            FontMetrics fm = g2.getFontMetrics();
            int lineH = Math.max(16, fm.getHeight());
            int baseline = y;
            String[] paragraphs = text.split("\n", -1);
            for (int p = 0; p < paragraphs.length; p++) {
                String[] words = paragraphs[p].trim().isEmpty()
                        ? new String[0]
                        : paragraphs[p].trim().split("\\s+");
                StringBuilder line = new StringBuilder();
                for (String word : words) {
                    for (String part : breakWord(fm, word, width)) {
                        String trial = line.length() == 0 ? part : line + " " + part;
                        if (fm.stringWidth(trial) > width && line.length() > 0) {
                            if (baseline + lineH > maxBaseline) {
                                drawEllipsis(g2, fm, line.toString(), x, baseline, width);
                                return baseline;
                            }
                            g2.drawString(line.toString(), x, baseline);
                            baseline += lineH;
                            line = new StringBuilder(part);
                        } else {
                            line = new StringBuilder(trial);
                        }
                    }
                }
                if (line.length() > 0) {
                    if (baseline > maxBaseline) {
                        drawEllipsis(g2, fm, line.toString(), x, Math.max(y, baseline - lineH), width);
                        return baseline;
                    }
                    g2.drawString(line.toString(), x, baseline);
                    baseline += lineH;
                } else if (p < paragraphs.length - 1) {
                    baseline += lineH;
                }
            }
            return baseline;
        }

        private void drawEllipsis(Graphics2D g2, FontMetrics fm, String line, int x, int baseline, int width) {
            String shown = line == null ? "" : line;
            while (!shown.isEmpty() && fm.stringWidth(shown + "…") > width) {
                shown = shown.substring(0, shown.length() - 1);
            }
            g2.drawString(shown + "…", x, baseline);
        }

        private java.util.List<String> breakWord(FontMetrics fm, String word, int width) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (word == null || word.isEmpty()) return parts;
            if (fm.stringWidth(word) <= width) {
                parts.add(word);
                return parts;
            }
            StringBuilder part = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                part.append(word.charAt(i));
                if (fm.stringWidth(part.toString()) > width && part.length() > 1) {
                    parts.add(part.substring(0, part.length() - 1));
                    part = new StringBuilder().append(word.charAt(i));
                }
            }
            if (part.length() > 0) parts.add(part.toString());
            return parts;
        }
    }

    private static final class SoftButton extends JButton {
        private final Icons.Kind kind;
        private Icons.Kind mark;
        private boolean stack;
        private java.awt.image.BufferedImage face;

        SoftButton(Icons.Kind kind, String tip) {
            super("");
            this.kind = kind;
            setToolTipText(tip);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font(Theme.FAMILY, Font.PLAIN, 12));
            setForeground(new Color(0xD6E2F5));
        }

        void stackCaption(String caption) {
            stack = true;
            setText(caption);
        }

        void flash(Icons.Kind temp) {
            mark = temp;
            repaint();
            Timer t = new Timer(1000, e -> {
                mark = null;
                repaint();
            });
            t.setRepeats(false);
            t.start();
        }

        void setFace(java.awt.image.BufferedImage face) {
            if (this.face == face) return;
            this.face = face;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            Color fill = getModel().isRollover() ? new Color(36, 72, 130, 230) : new Color(18, 42, 78, 210);
            CutPaint.button(g2, w, h, fill, new Color(0x4C8DFF));
            g2.setColor(new Color(0xD6E2F5));
            Icons.Kind draw = mark != null ? mark : kind;
            if (stack) {
                boolean two = getText() != null && getText().contains("\n");
                int icon = two || h < 64 ? 16 : 22;
                int top = two ? 8 : (h < 64 ? 6 : 12);
                if (face != null) {
                    ru.cw.launcher.skin.PlayerSkin.paint(g2, face, (w - icon) / 2, top, icon);
                } else {
                    Icons.of(draw, icon).paint(g2.getColor(), g2, (w - icon) / 2, top, icon, icon);
                }
                float size = h < 78 ? 11f : 12f;
                g2.setFont(getFont().deriveFont(size));
                String t = getText() == null ? "" : getText().trim();
                FontMetrics fm = g2.getFontMetrics();
                String widest = t.contains("\n") ? t.split("\n", 2)[0] : t;
                if (t.contains("\n")) {
                    String second = t.split("\n", 2)[1];
                    if (fm.stringWidth(second) > fm.stringWidth(widest)) widest = second;
                }
                while (size > 8f && fm.stringWidth(widest) > w - 16) {
                    size -= 0.5f;
                    g2.setFont(getFont().deriveFont(size));
                    fm = g2.getFontMetrics();
                }
                if (t.contains("\n")) {
                    String[] lines = t.split("\n", 2);
                    g2.drawString(lines[0], Math.max(2, (w - fm.stringWidth(lines[0])) / 2), h - 20);
                    g2.drawString(lines[1], Math.max(2, (w - fm.stringWidth(lines[1])) / 2), h - 6);
                } else {
                    g2.drawString(t, Math.max(4, (w - fm.stringWidth(t)) / 2), h - 8);
                }
            } else if (getText() != null && !getText().isBlank() && w > 80) {
                if (face != null) ru.cw.launcher.skin.PlayerSkin.paint(g2, face, 16, (h - 16) / 2, 16);
                else Icons.of(draw, 16).paint(g2.getColor(), g2, 16, (h - 16) / 2, 16, 16);
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText().trim(), 40, (h - fm.getHeight()) / 2 + fm.getAscent());
            } else if (face != null) {
                int icon = Math.max(16, Math.min(w, h) - 16);
                ru.cw.launcher.skin.PlayerSkin.paint(g2, face, (w - icon) / 2, (h - icon) / 2, icon);
            } else {
                int icon = Math.min(w, h) <= 32 ? 14 : 18;
                Icons.of(draw, icon).paint(g2.getColor(), g2, (w - icon) / 2, (h - icon) / 2, icon, icon);
            }
            g2.dispose();
        }
    }

    private static final class ProfileButton extends JButton {
        ProfileButton() {
            super("Игрок");
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font(Theme.FAMILY, Font.PLAIN, 14));
            setForeground(Color.WHITE);
        }

        void setCaption(String name) {
            setText(name == null || name.isBlank() ? "Игрок" : name);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            CutPaint.button(g2, getWidth(), getHeight(),
                    getModel().isRollover() ? new Color(36, 72, 130, 235) : new Color(16, 40, 74, 220),
                    new Color(0x4C8DFF));
            g2.setColor(Color.WHITE);
            Icons.of(Icons.Kind.USER, 16).paint(Color.WHITE, g2, 12, (getHeight() - 16) / 2, 16, 16);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int max = getWidth() - 52;
            while (t.length() > 1 && fm.stringWidth(t) > max) t = t.substring(0, t.length() - 1);
            g2.drawString(t, 34, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.setColor(new Color(0x9BB0D0));
            int cx = getWidth() - 18;
            int cy = getHeight() / 2;
            g2.fillPolygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
            g2.dispose();
        }
    }

    private static final class LaunchButton extends JButton {
        private final boolean showIcon;
        private Color accent = new Color(0x2F6FE0);
        private boolean live;

        LaunchButton(boolean showIcon) {
            super("Запустить");
            this.showIcon = showIcon;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font(Theme.FAMILY, Font.BOLD, 18));
            setForeground(Color.WHITE);
        }

        void setAccent(Color c) {
            accent = c;
            repaint();
        }

        void setLive(boolean live) {
            this.live = live;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(0, 0, getWidth(), getHeight());
            int w = getWidth();
            int h = getHeight();
            boolean active = isEnabled() || live;
            Color fill = active ? (getModel().isRollover() && isEnabled() ? accent.brighter() : accent)
                    : new Color(0x2A3548);
            CutPaint.button(g2, w, h, fill, new Color(0x8EB4FF));
            g2.setColor(Color.WHITE);
            String t = getText() == null ? "" : getText();
            int pad = 14;
            int icon = showIcon ? 18 : 0;
            int gap = showIcon ? 10 : 0;
            int available = Math.max(8, w - pad * 2 - icon - gap);
            Font use = getFont();
            FontMetrics fm = g2.getFontMetrics(use);
            while (use.getSize() > 12 && fm.stringWidth(t) > available) {
                use = use.deriveFont((float) (use.getSize() - 1));
                fm = g2.getFontMetrics(use);
            }
            g2.setFont(use);
            int textW = Math.min(fm.stringWidth(t), available);
            int total = icon + gap + textW;
            int x0 = Math.max(pad, (w - total) / 2);
            if (showIcon) {
                Icons.of(Icons.Kind.PLAY, icon).paint(Color.WHITE, g2, x0, (h - icon) / 2, icon, icon);
            }
            g2.drawString(t, x0 + icon + gap, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static final class VersionButton extends JButton {
        private static java.awt.image.BufferedImage GRASS;
        private String line1 = "Minecraft 1.20.1";
        private String line2 = "Fabric";

        VersionButton() {
            super("");
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFocusable(false);
            setCursor(Cursor.getDefaultCursor());
        }

        void setLines(String a, String b) {
            line1 = a;
            line2 = b;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            CutPaint.button(g2, getWidth(), getHeight(),
                    new Color(14, 36, 68, 230),
                    new Color(70, 120, 190));
            int cube = 46;
            int cubeY = Math.max(10, (getHeight() - cube) / 2 + 8);
            paintCube(g2, 12, cubeY, cube);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 16));
            int textY = line2 == null || line2.isBlank() ? (getHeight() + 16) / 2 : 30;
            g2.drawString(line1, 68, textY);
            if (line2 != null && !line2.isBlank()) {
                g2.setColor(new Color(0x9BB0D0));
                g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, 13));
                g2.drawString(line2, 68, 50);
            }
            g2.dispose();
        }

        private void paintCube(Graphics2D g2, int x, int y, int size) {
            if (GRASS == null) GRASS = grassIcon();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(GRASS, x, y, size, size, null);
        }

        /** Блок травы: верх зелёный, бока земля, кромка травы. Рисуется крупно и без мыла. */
        private static java.awt.image.BufferedImage grassIcon() {
            int n = 128;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int[] topX = {64, 118, 64, 10};
            int[] topY = {8, 36, 64, 36};
            int[] leftX = {10, 64, 64, 10};
            int[] leftY = {36, 64, 118, 90};
            int[] rightX = {64, 118, 118, 64};
            int[] rightY = {64, 36, 90, 118};
            g.setColor(new Color(0x8A5A32));
            g.fillPolygon(leftX, leftY, 4);
            g.setColor(new Color(0x6A4428));
            g.fillPolygon(rightX, rightY, 4);
            g.setClip(new java.awt.Polygon(leftX, leftY, 4));
            g.setColor(new Color(0x3F7A1C));
            g.fillRect(10, 36, 54, 16);
            g.setColor(new Color(0x2E6214));
            g.fillRect(10, 48, 54, 6);
            g.setColor(new Color(0x5C3A22));
            g.fillRect(18, 78, 8, 6);
            g.fillRect(34, 96, 7, 5);
            g.setColor(new Color(0xC4A06A));
            g.fillRect(26, 86, 5, 4);
            g.setClip(null);
            g.setClip(new java.awt.Polygon(rightX, rightY, 4));
            g.setColor(new Color(0x2A5810));
            g.fillRect(64, 36, 54, 16);
            g.setColor(new Color(0x1E420C));
            g.fillRect(64, 48, 54, 6);
            g.setColor(new Color(0x4A3018));
            g.fillRect(78, 80, 8, 6);
            g.fillRect(96, 98, 6, 5);
            g.setClip(null);
            g.setColor(new Color(0x5CB02E));
            g.fillPolygon(topX, topY, 4);
            g.setClip(new java.awt.Polygon(topX, topY, 4));
            g.setColor(new Color(0x7ED348));
            g.fillRect(28, 22, 14, 8);
            g.fillRect(70, 30, 16, 8);
            g.fillRect(46, 40, 12, 7);
            g.setColor(new Color(0x3E8618));
            g.fillRect(18, 34, 12, 7);
            g.fillRect(88, 38, 10, 6);
            g.setClip(null);
            g.setColor(new Color(0x14200C));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(topX, topY, 4);
            g.drawPolygon(leftX, leftY, 4);
            g.drawPolygon(rightX, rightY, 4);
            g.dispose();
            return img;
        }
    }

    private static final class LinkButton extends JButton {
        LinkButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(new Color(0x8EB4FF));
            setFont(new Font(Theme.FAMILY, Font.PLAIN, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(new Color(0x8EB4FF));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 0, fm.getAscent());
            g2.dispose();
        }
    }
}
