package ru.cw.launcher.ui;

import ru.cw.launcher.core.Settings;

import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/** Живые переключатели внешнего вида и уведомлений. Главное меню ими не скругляется и не гасится. */
public final class UiLook {

    private static volatile boolean blur = true;
    private static volatile boolean anim = true;
    private static volatile boolean transitions;
    private static volatile boolean compact;
    private static volatile boolean showFps;
    private static volatile boolean notes = true;
    private static long fpsWindow;
    private static int fpsFrames;
    private static int fpsShown;

    private UiLook() {
    }

    public static void apply(Settings cfg) {
        boolean effects = cfg == null || cfg.uiEffects;
        preview(effects, cfg == null || cfg.uiBlur, cfg == null || cfg.uiAnim,
                cfg != null && cfg.uiTransitions, cfg != null && "compact".equals(cfg.uiStyle),
                cfg != null && cfg.showFps);
        notes = cfg == null || cfg.notificationsEnabled;
    }

    /** Предпросмотр в открытых настройках, пока пользователь двигает переключатели. */
    public static void preview(boolean effects, boolean blurOn, boolean animOn, boolean fadeOn,
                               boolean compactOn, boolean fpsOn) {
        blur = effects && blurOn;
        anim = effects && animOn;
        transitions = effects && fadeOn;
        compact = compactOn;
        showFps = fpsOn;
    }

    public static boolean blur() {
        return blur;
    }

    public static boolean anim() {
        return anim;
    }

    public static boolean transitions() {
        return transitions;
    }

    public static boolean compact() {
        return compact;
    }

    public static boolean showFps() {
        return showFps;
    }

    public static int pad() {
        return compact ? 8 : 14;
    }

    public static int gap() {
        return compact ? 6 : 12;
    }

    /** Считает кадры отрисовки главного окна за последнюю секунду. */
    public static int fpsTick() {
        long now = System.currentTimeMillis();
        if (fpsWindow == 0) fpsWindow = now;
        fpsFrames++;
        if (now - fpsWindow >= 1000) {
            fpsShown = fpsFrames;
            fpsFrames = 0;
            fpsWindow = now;
        }
        return fpsShown;
    }

    public static void notify(String text) {
        if (!notes || text == null || text.isBlank()) return;
        show("CWLauncher", text, null, true);
    }

    /** Уведомление Windows. Нажатие вызывает действие и убирает значок. */
    public static void notifyUpdate(String text, Runnable onClick) {
        if (text == null || text.isBlank()) return;
        if (!SystemTray.isSupported() || !show("Common World", text, onClick, false)) {
            if (onClick != null) onClick.run();
        }
    }

    private static boolean show(String title, String text, Runnable onClick, boolean autoHide) {
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) img.setRGB(x, y, 0xFF2F6FE0);
            }
            TrayIcon icon = new TrayIcon(img.getScaledInstance(16, 16, Image.SCALE_SMOOTH), title);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> {
                try {
                    tray.remove(icon);
                } catch (Exception ignored) {
                }
                if (onClick != null) onClick.run();
            });
            tray.add(icon);
            icon.displayMessage(title, text, TrayIcon.MessageType.INFO);
            if (autoHide) {
                Thread remover = new Thread(() -> {
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        tray.remove(icon);
                    } catch (Exception ignored) {
                    }
                }, "cw-notify");
                remover.setDaemon(true);
                remover.start();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
