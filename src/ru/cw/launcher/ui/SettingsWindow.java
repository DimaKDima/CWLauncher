package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.minecraft.JavaManager;
import ru.cw.launcher.settings.SettingsManager;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Настройки в пяти вкладках: Основные, Игра, Моды, Внешний вид, Дополнительно.
 */
public final class SettingsWindow {

    private SettingsWindow() {
    }

    public static void show(Window owner, LauncherApp app) {
        Settings cfg = app.cfg;
        Lang.use(cfg.resolvedLanguage());
        JDialog d = dialog(owner);
        String[][] languages = Settings.languages();
        String[] everyItems = {Lang.t("every_day"), Lang.t("every_start"), Lang.t("every_manual")};

        SettingsChrome.Switch autoStart = SettingsChrome.toggle(cfg.autoStart);
        JComboBox<String> launcherEvery = SettingsChrome.combo(everyItems[everyIndex(cfg.launcherUpdateEvery)], everyItems);
        SettingsChrome.Switch autoLauncher = SettingsChrome.toggle(cfg.autoUpdateLauncher);
        JComboBox<String> launchBehavior = SettingsChrome.combo(Lang.t("menu_behavior"), Lang.t("menu_behavior"));
        SettingsChrome.Switch notes = SettingsChrome.toggle(cfg.notificationsEnabled);
        String[] timeItems = {Lang.t("time_sec"), Lang.t("time_min"), Lang.t("time_hour"),
                Lang.t("time_day"), Lang.t("time_week")};
        int timeIndex = switch (cfg.playTimeUnit == null ? "sec" : cfg.playTimeUnit) {
            case "min" -> 1;
            case "hour" -> 2;
            case "day" -> 3;
            case "week" -> 4;
            default -> 0;
        };
        JComboBox<String> timeUnit = SettingsChrome.combo(timeItems[timeIndex], timeItems);
        JComboBox<String> language = SettingsChrome.combo(null);
        int languageIndex = 0;
        for (int i = 0; i < languages.length; i++) {
            language.addItem(languages[i][0].isEmpty() ? Lang.t("lang_sys") : languages[i][1]);
            if (languages[i][0].equals(cfg.gameLanguage)) languageIndex = i;
        }
        language.setSelectedIndex(languageIndex);
        JComboBox<String> theme = SettingsChrome.combo(Lang.t("blue"), Lang.t("blue"));

        int ramCapGb = Math.max(1, SettingsManager.maxGameRamMb() / 1024);
        int ramNowMb = cfg.ramMb > 0 ? Math.min(cfg.ramMb, ramCapGb * 1024) : SettingsManager.defaultRamMb();
        int ramNowGb = Math.max(1, Math.min(ramCapGb, (ramNowMb + 512) / 1024));
        JSlider ram = SettingsChrome.slider(1, ramCapGb, ramNowGb);
        JLabel ramText = new JLabel(ramLabel(ram.getValue(), ramCapGb));
        ramText.setFont(Theme.H3);
        ramText.setForeground(Color.WHITE);
        ram.addChangeListener(e -> ramText.setText(ramLabel(ram.getValue(), ramCapGb)));
        boolean own = cfg.javaPath != null && !cfg.javaPath.isBlank();
        SettingsChrome.Switch ownJava = SettingsChrome.toggle(own);
        JTextField javaPath = Theme.field(cfg.javaPath, 18);
        javaPath.setEnabled(own);
        ownJava.onChange(() -> javaPath.setEnabled(ownJava.isOn()));
        JLabel javaInfo = SettingsChrome.caption(app.java().describe());
        JTextField jvm = Theme.field(cfg.extraJvmArgs, 22);
        SettingsChrome.Switch leader = SettingsChrome.toggle(cfg.internetLeader);
        SettingsChrome.Switch limitNet = SettingsChrome.toggle(cfg.limitBandwidth);
        String[] bandItems = {Lang.t("band_none"), "5 " + Lang.t("mbit"), "10 " + Lang.t("mbit"),
                "25 " + Lang.t("mbit"), "50 " + Lang.t("mbit"), "100 " + Lang.t("mbit")};
        JComboBox<String> band = SettingsChrome.combo(bandItems[bandIndex(cfg.bandwidthMbit, cfg.limitBandwidth)], bandItems);
        band.setEnabled(limitNet.isOn());
        limitNet.onChange(() -> band.setEnabled(limitNet.isOn()));
        JCheckBox noFs = Theme.check(Lang.t("nofull"), cfg.disableFullscreenOpt);
        JCheckBox highPri = Theme.check(Lang.t("hipri"), cfg.highPriority);
        JCheckBox noRt = Theme.check(Lang.t("nort"), cfg.disableRealtimeOpt);
        JSpinner width = new JSpinner(new SpinnerNumberModel(cfg.screenWidth, 854, 7680, 2));
        JSpinner height = new JSpinner(new SpinnerNumberModel(cfg.screenHeight, 480, 4320, 2));
        Theme.style(width);
        Theme.style(height);
        JCheckBox full = Theme.check(Lang.t("fullscreen"), cfg.screenFullscreen);
        JCheckBox vsync = Theme.check("VSync", cfg.vsync);
        JSpinner fps = new JSpinner(new SpinnerNumberModel(cfg.framerateLimit, 0, 1000, 10));
        Theme.style(fps);

        SettingsChrome.Switch autoBuild = SettingsChrome.toggle(cfg.autoUpdateBuild);
        JComboBox<String> modsEvery = SettingsChrome.combo(everyItems[everyIndex(cfg.modsUpdateEvery)], everyItems);
        SettingsChrome.Switch verify = SettingsChrome.toggle(cfg.verifyBuildOnLaunch);
        SettingsChrome.Switch backups = SettingsChrome.toggle(cfg.modBackups);
        JComboBox<String> copies = SettingsChrome.combo(String.valueOf(nearestCopies(cfg.modBackupCount)),
                "3", "5", "10");

        Thumb preview = new Thumb();
        preview.setImage(app.background().picture());
        JLabel bgName = SettingsChrome.caption(currentBackgroundName(cfg));
        int dimPercent = Math.max(0, Math.min(100, cfg.backgroundDim * 100 / 80));
        JSlider dim = SettingsChrome.slider(0, 100, dimPercent);
        JLabel dimText = new JLabel(dim.getValue() + "%");
        dimText.setFont(Theme.H3);
        dimText.setForeground(Color.WHITE);
        final int openedDim = dimPercent;
        final boolean[] committed = {false};
        dim.addChangeListener(e -> {
            dimText.setText(dim.getValue() + "%");
            ru.cw.launcher.background.BackgroundManager.setDimPercent(dim.getValue());
            d.repaint();
        });
        SettingsChrome.Switch themeOn = SettingsChrome.toggle(true);
        themeOn.onChange(() -> themeOn.setOn(true));
        SettingsChrome.Switch effects = SettingsChrome.toggle(cfg.uiEffects);
        JCheckBox blur = Theme.check(Lang.t("blur"), cfg.uiBlur);
        JCheckBox anim = Theme.check(Lang.t("anim"), cfg.uiAnim);
        JCheckBox fade = Theme.check(Lang.t("fade"), cfg.uiTransitions);
        effects.onChange(() -> {
            boolean on = effects.isOn();
            blur.setEnabled(on);
            anim.setEnabled(on);
            fade.setEnabled(on);
        });
        blur.setEnabled(cfg.uiEffects);
        anim.setEnabled(cfg.uiEffects);
        fade.setEnabled(cfg.uiEffects);
        String[] styleItems = {Lang.t("style_std"), Lang.t("style_compact")};
        JComboBox<String> style = SettingsChrome.combo(styleItems["compact".equals(cfg.uiStyle) ? 1 : 0], styleItems);
        JCheckBox showFps = Theme.check(Lang.t("fps"), cfg.showFps);
        Runnable previewLook = () -> {
            UiLook.preview(effects.isOn(), blur.isSelected(), anim.isSelected(), fade.isSelected(),
                    style.getSelectedIndex() == 1, showFps.isSelected());
            d.repaint();
            if (owner instanceof MainWindow mw) mw.syncFromState();
        };
        blur.addActionListener(e -> previewLook.run());
        anim.addActionListener(e -> previewLook.run());
        fade.addActionListener(e -> previewLook.run());
        style.addActionListener(e -> previewLook.run());
        showFps.addActionListener(e -> previewLook.run());
        effects.onChange(previewLook);

        JLabel cacheSize = SettingsChrome.caption(Lang.t("cache_size") + Utils.humanSize(cacheBytes()));
        JCheckBox disk = Theme.check(Lang.t("disk"), cfg.checkDiskSpace);
        JCheckBox proxy = Theme.check(Lang.t("proxy"), cfg.useSystemProxy);
        JCheckBox offline = Theme.check(Lang.t("offline"), cfg.offlineAllowed);
        JCheckBox dev = Theme.check(Lang.t("dev"), cfg.developerMode);
        dev.setToolTipText(Lang.t("dev_hint"));
        JCheckBox verbose = Theme.check(Lang.t("verbose"), cfg.verboseLogging);

        JButton pickBg = SettingsChrome.pill(Lang.t("pick_bg"), false);
        pickBg.addActionListener(e -> {
            if (pickBackground(d, app, cfg)) {
                preview.setImage(app.background().picture());
                bgName.setText(currentBackgroundName(cfg));
            }
        });
        JButton browseJava = SettingsChrome.pill(Lang.t("browse"), false);
        browseJava.addActionListener(e -> {
            String path = chooseJava(d);
            if (path == null) return;
            javaPath.setText(path);
            ownJava.setOn(true);
            javaPath.setEnabled(true);
        });
        JButton editSources = SettingsChrome.pill(Lang.t("edit"), false);
        editSources.addActionListener(e -> Dialogs.info(d,
                "Источник модов Common World один: архив modsCWL.zip. Его нельзя заменить другой ссылкой."));
        JButton openMods = SettingsChrome.pill(Lang.t("open_mods"), false);
        openMods.addActionListener(e -> Utils.openFolder(Paths.mods(cfg.instanceId())));
        JButton clear = SettingsChrome.pill(Lang.t("clear"), false);
        clear.addActionListener(e -> {
            long size = cacheBytes();
            if (!Dialogs.ask(d, "Удалить временные данные CWLauncher (" + Utils.humanSize(size) + ")?",
                    "Очистить", "Отмена")) return;
            Utils.clearDir(Paths.cache());
            Utils.clearDir(Paths.tmp());
            cacheSize.setText(Lang.t("cache_size") + Utils.humanSize(cacheBytes()));
        });
        JButton openDownloads = SettingsChrome.pill(Lang.t("open_dl"), false);
        openDownloads.addActionListener(e -> Utils.openFolder(Paths.cache()));
        JButton openLogs = SettingsChrome.pill(Lang.t("logs_btn"), false);
        openLogs.addActionListener(e -> Utils.openFolder(Paths.logs()));
        JButton makeDiag = SettingsChrome.pill(Lang.t("diag_btn"), false);
        makeDiag.addActionListener(e -> {
            makeDiag.setEnabled(false);
            new Thread(() -> {
                try {
                    Path zip = app.diagnostics().writeReportZip(app.cfg, app.mc(), app.fabric(), app.java(), app.mods());
                    SwingUtilities.invokeLater(() -> {
                        makeDiag.setEnabled(true);
                        Dialogs.info(d, "Файл диагностики создан:\n" + zip);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        makeDiag.setEnabled(true);
                        Dialogs.error(d, "Не удалось создать диагностику.", ru.cw.launcher.util.Log.reason(ex), null);
                    });
                }
            }, "cw-settings-diag").start();
        });
        JButton details = SettingsChrome.pill(Lang.t("details"), false);
        details.addActionListener(e -> showDeveloper(d, app));
        JButton wipeGames = SettingsChrome.pill(Lang.t("wipe_ver"), false);
        wipeGames.addActionListener(e -> {
            if (app.isBusy()) {
                Dialogs.info(d, "Дождитесь окончания текущей операции.");
                return;
            }
            if (!Dialogs.ask(d, "Удалить все версии игры, моды и миры? Лаунчер останется, но игру нужно будет скачать заново.",
                    "Удалить версии", "Отмена")) return;
            app.resetGameData();
            Dialogs.info(d, "Игровые файлы удалены. Лаунчер снова на первой установке.");
            d.dispose();
        });
        JButton wipeAll = SettingsChrome.pill(Lang.t("wipe_all"), false);
        wipeAll.addActionListener(e -> {
            if (app.isBusy()) {
                Dialogs.info(d, "Дождитесь окончания текущей операции.");
                return;
            }
            if (!Dialogs.ask(d, "Полностью удалить лаунчер и все его файлы? Это сотрёт настройки, аккаунты и все версии игры. Вернуть их будет нельзя.",
                    "Удалить всё", "Отмена")) return;
            if (!app.launchUninstaller()) {
                Dialogs.error(d, "Рядом с лаунчером нет Uninstall.exe. Удаление не начато.", null, null);
                return;
            }
            app.shutdown();
            d.dispose();
            if (owner != null) owner.dispose();
            System.exit(0);
        });

        Runnable resetForm = () -> {
            autoStart.setOn(true);
            autoLauncher.setOn(true);
            launcherEvery.setSelectedIndex(0);
            launchBehavior.setSelectedIndex(0);
            notes.setOn(true);
            language.setSelectedIndex(0);
            int defGb = Math.max(1, Math.min(ramCapGb, (SettingsManager.defaultRamMb() + 512) / 1024));
            ram.setValue(defGb);
            ramText.setText(ramLabel(ram.getValue(), ramCapGb));
            ownJava.setOn(false);
            javaPath.setText("");
            javaPath.setEnabled(false);
            jvm.setText("");
            leader.setOn(false);
            limitNet.setOn(false);
            band.setSelectedIndex(0);
            noFs.setSelected(false);
            highPri.setSelected(false);
            noRt.setSelected(false);
            width.setValue(1280);
            height.setValue(720);
            full.setSelected(false);
            vsync.setSelected(true);
            fps.setValue(240);
            autoBuild.setOn(true);
            modsEvery.setSelectedIndex(0);
            verify.setOn(true);
            backups.setOn(true);
            copies.setSelectedItem("5");
            dim.setValue(60);
            dimText.setText("60%");
            ru.cw.launcher.background.BackgroundManager.setDimPercent(60);
            d.repaint();
            effects.setOn(true);
            blur.setSelected(true);
            anim.setSelected(true);
            fade.setSelected(false);
            blur.setEnabled(true);
            anim.setEnabled(true);
            fade.setEnabled(true);
            style.setSelectedIndex(0);
            showFps.setSelected(false);
            disk.setSelected(true);
            proxy.setSelected(false);
            offline.setSelected(true);
            dev.setSelected(false);
            verbose.setSelected(false);
            timeUnit.setSelectedIndex(0);
        };
        Runnable save = () -> {
            String languageBefore = cfg.gameLanguage == null ? "" : cfg.gameLanguage;
            cfg.autoStart = autoStart.isOn();
            cfg.autoUpdateLauncher = autoLauncher.isOn();
            cfg.launcherUpdateEvery = everyKeyIndex(launcherEvery.getSelectedIndex());
            cfg.launchBehavior = "menu";
            cfg.notificationsEnabled = notes.isOn();
            cfg.playTimeUnit = switch (timeUnit.getSelectedIndex()) {
                case 1 -> "min";
                case 2 -> "hour";
                case 3 -> "day";
                case 4 -> "week";
                default -> "sec";
            };
            int langIndex = language.getSelectedIndex();
            cfg.gameLanguage = langIndex >= 0 && langIndex < languages.length ? languages[langIndex][0] : "";
            cfg.theme = Settings.THEME_DARK;
            cfg.ramMb = ram.getValue() * 1024;
            cfg.javaPath = ownJava.isOn() ? javaPath.getText().trim() : "";
            cfg.extraJvmArgs = jvm.getText().trim();
            cfg.internetLeader = leader.isOn();
            cfg.limitBandwidth = limitNet.isOn();
            cfg.bandwidthMbit = bandMbit(String.valueOf(band.getSelectedItem()));
            cfg.disableFullscreenOpt = noFs.isSelected();
            cfg.highPriority = highPri.isSelected();
            cfg.disableRealtimeOpt = noRt.isSelected();
            cfg.screenWidth = ((Number) width.getValue()).intValue();
            cfg.screenHeight = ((Number) height.getValue()).intValue();
            cfg.screenFullscreen = full.isSelected();
            cfg.vsync = vsync.isSelected();
            cfg.framerateLimit = ((Number) fps.getValue()).intValue();
            cfg.autoUpdateBuild = autoBuild.isOn();
            cfg.modsUpdateEvery = everyKeyIndex(modsEvery.getSelectedIndex());
            cfg.verifyBuildOnLaunch = verify.isOn();
            cfg.modBackups = backups.isOn();
            cfg.modBackupCount = copies.getSelectedItem() == null ? 5 : Integer.parseInt(String.valueOf(copies.getSelectedItem()));
            cfg.backgroundDim = dim.getValue() * 80 / 100;
            cfg.uiEffects = effects.isOn();
            cfg.uiBlur = blur.isSelected();
            cfg.uiAnim = anim.isSelected();
            cfg.uiTransitions = fade.isSelected();
            cfg.uiStyle = style.getSelectedIndex() == 1 ? "compact" : "standard";
            cfg.showFps = showFps.isSelected();
            cfg.checkDiskSpace = disk.isSelected();
            cfg.useSystemProxy = proxy.isSelected();
            cfg.offlineAllowed = offline.isSelected();
            cfg.developerMode = dev.isSelected();
            cfg.verboseLogging = verbose.isSelected();
            cfg.closeLauncherOnLaunch = false;
            cfg.validate();
            app.saveSettings();
            app.background().reload(cfg);
            Lang.use(cfg.resolvedLanguage());
            if (owner instanceof MainWindow mw) mw.applyLanguage();
            Path gameHome = Paths.instance(cfg.instanceId());
            if (Files.isDirectory(gameHome)) app.launch().applyLanguage(cfg, cfg.instanceId());
            if (!SettingsManager.applyAutoStart(cfg.autoStart) && cfg.autoStart) {
                Dialogs.info(d, "Автозапуск записан в настройках. В реестр Windows он попадёт, когда лаунчер запущен как CWLauncher.exe.");
            }
            app.refreshState();
            committed[0] = true;
            boolean languageChanged = !languageBefore.equals(cfg.gameLanguage == null ? "" : cfg.gameLanguage);
            d.dispose();
            if (languageChanged) SwingUtilities.invokeLater(() -> show(owner, app));
        };

        JPanel general = SettingsChrome.split(
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.CLOCK, Lang.t("autostart"), Lang.t("autostart_h"), autoStart, null),
                        SettingsChrome.card(Icons.Kind.UPDATE, Lang.t("autoupdate"), Lang.t("autoupdate_h"), autoLauncher,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("check_updates")), launcherEvery)),
                        SettingsChrome.card(Icons.Kind.PLAY, Lang.t("behavior"), Lang.t("behavior_h"), null,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("on_launch")), launchBehavior))),
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.INFO, Lang.t("notes"), Lang.t("notes_h"), notes,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("time_show")), timeUnit)),
                        SettingsChrome.card(Icons.Kind.NEWS, Lang.t("lang"), Lang.t("lang_hint"), null,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("lang_pick")), language)),
                        SettingsChrome.card(Icons.Kind.GALLERY, Lang.t("theme"), Lang.t("theme_hint"), null,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("color")), theme))));
        JPanel game = SettingsChrome.split(
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.MEMORY, Lang.t("ram"),
                                Lang.t("ram_hint") + " " + ramCapGb + " " + Lang.t("gb") + ".", null,
                                SettingsChrome.row(ram, ramText)),
                        SettingsChrome.card(Icons.Kind.JAVA, Lang.t("java"),
                                Lang.t("java_h") + " " + JavaManager.requiredMajor(cfg.minecraftVersion) + ".", ownJava,
                                SettingsChrome.stack(javaInfo, SettingsChrome.row(javaPath, browseJava))),
                        SettingsChrome.card(Icons.Kind.SETTINGS, Lang.t("jvm"), Lang.t("jvm_h"), null, jvm)),
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.NET, Lang.t("leader"), Lang.t("leader_h"), leader,
                                SettingsChrome.stack(limitNetRow(limitNet), SettingsChrome.caption(Lang.t("mbit")), band)),
                        SettingsChrome.card(Icons.Kind.SHIELD, Lang.t("gameopt"), Lang.t("gameopt_h"), null,
                                SettingsChrome.stack(noFs, highPri, noRt, SettingsChrome.caption(Lang.t("res")),
                                        SettingsChrome.row(width, SettingsChrome.caption("x"), height),
                                        full, vsync, SettingsChrome.caption(Lang.t("fps_cap")), fps))));
        JPanel mods = SettingsChrome.split(
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.UPDATE, Lang.t("modsup"), Lang.t("modsup_h"), autoBuild,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("check_updates")), modsEvery)),
                        SettingsChrome.card(Icons.Kind.CHECK, Lang.t("verify"), Lang.t("verify_h"), verify, null),
                        SettingsChrome.card(Icons.Kind.FOLDER, Lang.t("modmgr"), Lang.t("modmgr_h"), null, openMods)),
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.MODS, Lang.t("sources"), Lang.t("sources_h"), null, editSources),
                        SettingsChrome.card(Icons.Kind.COPY, Lang.t("backups"), Lang.t("backups_h"), backups,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("copies")), copies))));
        JPanel look = SettingsChrome.split(
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.GALLERY, Lang.t("bg"), Lang.t("bg_h"),
                                null, SettingsChrome.stack(SettingsChrome.row(preview, pickBg), bgName)),
                        SettingsChrome.card(Icons.Kind.GALLERY, Lang.t("look_theme"), Lang.t("look_theme_h"), themeOn,
                                SettingsChrome.stack(SettingsChrome.caption(Lang.t("color")), theme)),
                        SettingsChrome.card(Icons.Kind.EDIT, Lang.t("dim"), Lang.t("dim_hint"), null,
                                SettingsChrome.row(dim, dimText))),
                SettingsChrome.column(
                        SettingsChrome.card(Icons.Kind.INFO, Lang.t("effects"), Lang.t("effects_h"), effects,
                                SettingsChrome.stack(blur, anim, fade)),
                        SettingsChrome.card(Icons.Kind.SETTINGS, Lang.t("uistyle"), Lang.t("uistyle_h"), null, style)));
        JButton resetCard = SettingsChrome.pill(Lang.t("reset_short"), false);
        resetCard.addActionListener(e -> resetForm.run());
        JPanel extra = SettingsChrome.grid(2, 3,
                SettingsChrome.card(Icons.Kind.TRASH, Lang.t("cache"), Lang.t("cache_h"), null,
                        SettingsChrome.stack(cacheSize, clear)),
                SettingsChrome.card(Icons.Kind.HISTORY, Lang.t("logs"), Lang.t("logs_h"), null, openLogs),
                SettingsChrome.card(Icons.Kind.SETTINGS, Lang.t("extra_opts"), Lang.t("extra_h"), null,
                        SettingsChrome.stack(showFps, disk, proxy, offline, dev, verbose)),
                SettingsChrome.card(Icons.Kind.DOWNLOAD, Lang.t("downloads"), Lang.t("downloads_h"), null, openDownloads),
                SettingsChrome.card(Icons.Kind.DOCTOR, Lang.t("diag"), Lang.t("diag_h"), null,
                        SettingsChrome.stack(makeDiag, details)),
                SettingsChrome.card(Icons.Kind.REFRESH, Lang.t("reset_card"), Lang.t("reset_h"), null,
                        SettingsChrome.stack(resetCard, wipeGames, wipeAll)));

        CardLayout layout = new CardLayout();
        JPanel pages = new JPanel(layout);
        pages.setOpaque(false);
        pages.add(general, "general");
        pages.add(game, "game");
        pages.add(mods, "mods");
        pages.add(look, "look");
        pages.add(extra, "extra");

        SettingsChrome.Tab[] tabs = {
                new SettingsChrome.Tab(Lang.t("tab_main"), Icons.Kind.SETTINGS),
                new SettingsChrome.Tab(Lang.t("tab_game"), Icons.Kind.PLAY),
                new SettingsChrome.Tab(Lang.t("tab_mods"), Icons.Kind.MODS),
                new SettingsChrome.Tab(Lang.t("tab_look"), Icons.Kind.GALLERY),
                new SettingsChrome.Tab(Lang.t("tab_extra"), Icons.Kind.INFO)
        };
        String[] keys = {"general", "game", "mods", "look", "extra"};
        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabBar.setOpaque(false);
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i].addActionListener(e -> {
                for (int n = 0; n < tabs.length; n++) tabs[n].setActive(n == index);
                layout.show(pages, keys[index]);
            });
            tabBar.add(tabs[i]);
        }
        tabs[0].setActive(true);

        JLabel title = new JLabel("CWLauncher");
        title.setFont(Theme.H2);
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel(Lang.t("settings"));
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.MUTED);
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(sub);
        JButton close = SettingsChrome.pill("×", false);
        close.addActionListener(e -> d.dispose());
        JPanel headerTop = new JPanel(new BorderLayout());
        headerTop.setOpaque(false);
        headerTop.add(titles, BorderLayout.WEST);
        headerTop.add(close, BorderLayout.EAST);
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(headerTop);
        header.add(Box.createVerticalStrut(10));
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(tabBar);

        JButton reset = SettingsChrome.pill(Lang.t("reset"), false);
        JButton saveBtn = SettingsChrome.pill(Lang.t("save"), true);
        reset.addActionListener(e -> resetForm.run());
        saveBtn.addActionListener(e -> save.run());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.add(reset);
        footer.add(saveBtn);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.add(header, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        d.add(root, BorderLayout.CENTER);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        WindowBackground.dim(d);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (!committed[0]) {
                    ru.cw.launcher.background.BackgroundManager.setDimPercent(openedDim);
                    UiLook.apply(cfg);
                }
            }
        });
        ru.cw.launcher.background.BackgroundManager.setDimPercent(dim.getValue());
        d.setVisible(true);
        if (UiLook.transitions()) {
            try {
                d.setOpacity(0f);
                Timer fadeTimer = new Timer(16, null);
                fadeTimer.addActionListener(ev -> {
                    float next = Math.min(1f, d.getOpacity() + 0.12f);
                    d.setOpacity(next);
                    if (next >= 1f) fadeTimer.stop();
                });
                fadeTimer.start();
            } catch (Exception ignored) {
            }
        }
    }

    private static JComponent limitNetRow(SettingsChrome.Switch limitNet) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel name = new JLabel(Lang.t("limit_name"));
        name.setFont(Theme.BODY);
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = SettingsChrome.caption(Lang.t("limit_hint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(hint);
        p.add(text, BorderLayout.CENTER);
        p.add(limitNet, BorderLayout.EAST);
        return p;
    }

    private static boolean pickBackground(JDialog owner, LauncherApp app, Settings cfg) {
        JDialog pick = new JDialog(owner, "Фон", Dialog.ModalityType.APPLICATION_MODAL);
        pick.setSize(460, 420);
        pick.setLocationRelativeTo(owner);
        pick.getContentPane().setBackground(new Color(0x0B1220));
        pick.setLayout(new BorderLayout(8, 8));
        ((JComponent) pick.getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            box.removeAll();
            java.util.List<Path> pics = new java.util.ArrayList<>(Paths.backgroundImages());
            String current = cfg.backgroundPath == null ? "" : cfg.backgroundPath;
            if (cfg.useCustomBackground && !current.isBlank()) {
                Path extra = Path.of(current);
                boolean listed = pics.stream().anyMatch(p -> p.toAbsolutePath().normalize().toString()
                        .equalsIgnoreCase(extra.toAbsolutePath().normalize().toString()));
                if (!listed && Files.isRegularFile(extra)) pics.add(0, extra);
            }
            if (pics.isEmpty()) {
                JLabel empty = new JLabel("В папке пока нет картинок");
                empty.setForeground(Theme.MUTED);
                box.add(empty);
            }
            for (Path pic : pics) {
                boolean active = sameFile(pic, current) || ((current.isBlank() || !cfg.useCustomBackground)
                        && pic.getFileName().toString().equalsIgnoreCase("main.png"));
                JButton row = new JButton(pic.getFileName().toString());
                row.setHorizontalAlignment(SwingConstants.LEFT);
                row.setFont(Theme.BODY);
                row.setForeground(Color.WHITE);
                row.setBackground(active ? new Color(0x2F6FE0) : new Color(0x12203A));
                row.setOpaque(true);
                row.setBorderPainted(false);
                row.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
                row.addActionListener(ev -> {
                    cfg.backgroundPath = pic.toAbsolutePath().normalize().toString();
                    cfg.useCustomBackground = true;
                    app.background().reload(cfg);
                    app.saveSettings();
                    refresh[0].run();
                });
                box.add(row);
                box.add(Box.createVerticalStrut(4));
            }
            box.revalidate();
            box.repaint();
        };
        refresh[0].run();
        JScrollPane scroll = new JScrollPane(box);
        Theme.style(scroll);
        JButton add = SettingsChrome.pill(Lang.t("add_bg"), true);
        add.addActionListener(e -> {
            String path = ru.cw.launcher.background.BackgroundManager.chooseImage(pick);
            if (path == null) return;
            try {
                copyBackground(Path.of(path));
                refresh[0].run();
                Dialogs.info(pick, "Картинка добавлена. Выберите её в списке, чтобы включить.");
            } catch (Exception ex) {
                Dialogs.error(pick, "Не удалось добавить фон.", ru.cw.launcher.util.Log.reason(ex), null);
            }
        });
        JButton reset = SettingsChrome.pill(Lang.t("reset_bg"), false);
        reset.addActionListener(e -> {
            cfg.backgroundPath = "";
            cfg.useCustomBackground = false;
            app.background().reload(cfg);
            app.saveSettings();
            refresh[0].run();
        });
        JButton folder = SettingsChrome.pill(Lang.t("folder_btn"), false);
        folder.addActionListener(e -> Utils.openFolder(Paths.backgroundDir()));
        JButton gallery = SettingsChrome.pill(Lang.t("gallery"), false);
        gallery.addActionListener(e -> GalleryWindow.show(pick, app));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(reset);
        buttons.add(folder);
        buttons.add(gallery);
        JButton done = SettingsChrome.pill(Lang.t("done"), true);
        done.addActionListener(e -> pick.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.CENTER);
        south.add(done, BorderLayout.EAST);
        pick.add(scroll, BorderLayout.CENTER);
        pick.add(south, BorderLayout.SOUTH);
        pick.setVisible(true);
        return true;
    }

    private static void copyBackground(Path src) throws java.io.IOException {
        Path dir = Paths.backgroundDir();
        Files.createDirectories(dir);
        String name = src.getFileName().toString();
        Path dest = dir.resolve(name);
        if (Files.exists(dest) && !dest.toAbsolutePath().normalize().equals(src.toAbsolutePath().normalize())) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            int n = 2;
            do {
                dest = dir.resolve(base + "-" + n + ext);
                n++;
            } while (Files.exists(dest));
        }
        if (!dest.toAbsolutePath().normalize().equals(src.toAbsolutePath().normalize())) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean sameFile(Path pic, String current) {
        if (current == null || current.isBlank()) return false;
        return pic.toAbsolutePath().normalize().toString()
                .equalsIgnoreCase(Path.of(current).toAbsolutePath().normalize().toString());
    }

    private static String currentBackgroundName(Settings cfg) {
        if (cfg.useCustomBackground && cfg.backgroundPath != null && !cfg.backgroundPath.isBlank()) {
            return "Сейчас: " + Path.of(cfg.backgroundPath).getFileName();
        }
        return "Сейчас: main.png";
    }

    private static String chooseJava(Component parent) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        Frame frame = owner instanceof Frame f ? f : new Frame();
        FileDialog dialog = new FileDialog(frame, "Выберите java.exe", FileDialog.LOAD);
        dialog.setFile("java.exe");
        dialog.setVisible(true);
        if (dialog.getFile() == null || dialog.getDirectory() == null) return null;
        return new java.io.File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
    }

    private static void showDeveloper(Component parent, LauncherApp app) {
        StringBuilder sb = new StringBuilder();
        sb.append("CWLauncher ").append(ru.cw.launcher.util.Log.VERSION).append('\n');
        sb.append("Состояние: ").append(app.state().get()).append(" — ").append(app.state().detail()).append('\n');
        sb.append("Minecraft: ").append(app.cfg.minecraftVersion).append('\n');
        sb.append("Fabric loader (установлен): ").append(String.valueOf(app.fabric().installedLoader(app.cfg.minecraftVersion))).append('\n');
        sb.append("Java: ").append(app.java().describe()).append('\n');
        sb.append("Профиль: ").append(app.profile() == null ? "—" : app.profile().name).append('\n');
        sb.append("Каталог: ").append(Paths.root()).append('\n');
        sb.append("Minecraft: ").append(Paths.gameDir()).append('\n');
        sb.append("Моды: ").append(Paths.mods(app.cfg.instanceId())).append('\n');
        sb.append("Установка: ").append(Paths.installRoot()).append('\n');
        sb.append("Секреты и токены в этот текст не включаются.\n");
        Dialogs.error(parent, "Сведения для разработчика записаны ниже. Токены не показываются.", sb.toString(), null);
    }

    private static long cacheBytes() {
        return Utils.dirSize(Paths.cache()) + Utils.dirSize(Paths.tmp());
    }

    private static String ramLabel(int gb, int cap) {
        return gb + " " + Lang.t("gb") + " " + Lang.t("ram_of") + " " + cap + " " + Lang.t("gb");
    }

    private static int everyIndex(String key) {
        if ("start".equals(key)) return 1;
        if ("manual".equals(key)) return 2;
        return 0;
    }

    private static String everyKeyIndex(int index) {
        if (index == 1) return "start";
        if (index == 2) return "manual";
        return "day";
    }

    private static int bandIndex(int mbit, boolean limited) {
        if (!limited || mbit <= 0) return 0;
        int[] steps = {0, 5, 10, 25, 50, 100};
        int best = 1;
        int dist = Integer.MAX_VALUE;
        for (int i = 1; i < steps.length; i++) {
            int gap = Math.abs(steps[i] - mbit);
            if (gap < dist) {
                dist = gap;
                best = i;
            }
        }
        return best;
    }

    private static int bandMbit(String label) {
        if (label == null || label.startsWith("Без")) return 0;
        String digits = label.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        return Integer.parseInt(digits);
    }

    private static int nearestCopies(int n) {
        if (n <= 3) return 3;
        if (n >= 10) return 10;
        return 5;
    }

    private static JDialog dialog(Window owner) {
        JDialog d = new JDialog(owner, "CWLauncher", Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        if (owner != null && owner.isShowing()) d.setBounds(owner.getBounds());
        else {
            d.setSize(1100, 720);
            d.setLocationRelativeTo(owner);
        }
        d.getContentPane().setBackground(new Color(0x07101C));
        d.setLayout(new BorderLayout());
        ((JComponent) d.getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        return d;
    }

    private static final class Thumb extends JPanel {
        private BufferedImage image;

        Thumb() {
            setOpaque(false);
            setPreferredSize(new Dimension(148, 84));
            setMinimumSize(new Dimension(148, 84));
            setMaximumSize(new Dimension(148, 84));
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            RoundRectangle2D clip = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setClip(clip);
            if (image != null && image.getWidth() > 0) {
                double scale = Math.max(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
                int dw = (int) Math.round(image.getWidth() * scale);
                int dh = (int) Math.round(image.getHeight() * scale);
                g2.drawImage(image, (getWidth() - dw) / 2, (getHeight() - dh) / 2, dw, dh, null);
            } else {
                g2.setColor(new Color(0x12203A));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setClip(null);
            g2.setColor(new Color(0x1E5AA8));
            g2.draw(clip);
            g2.dispose();
        }
    }
}
