package ru.cw.launcher.ui;

import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Понятные сообщения об ошибках (раздел 26 ТЗ): текст + [Повторить] [Тех. Поддержка] [Закрыть],
 * подробности — отдельно и в лог. Плюс всплывающие уведомления без дублей.
 */
public final class Dialogs {

    private static final List<String> shownToasts = new ArrayList<>();

    private Dialogs() {
    }

    public static Runnable supportOpener = () -> Utils.openUrl("https://discord.gg/vEkuksnUQ4");

    /** Ошибка с повтором. Возвращает true, если пользователь нажал «Повторить». */
    public static boolean error(Component parent, String message, String details, Runnable retry) {
        Log.error(message + (details == null ? "" : " | " + details.replaceAll("\\s+", " ")));
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), "CWLauncher",
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(Theme.PANEL);
        body.setBorder(BorderFactory.createCompoundBorder(
                new CutBorder(Theme.INFO, 10), BorderFactory.createEmptyBorder(18, 20, 16, 20)));
        JLabel icon = new JLabel("!");
        icon.setPreferredSize(new Dimension(28, 28));
        icon.setForeground(Theme.AMBER);
        icon.setFont(Theme.H2);
        JLabel text = new JLabel("<html><div style='width:360px'>" + esc(message)
                + "</div></html>");
        text.setForeground(Theme.TEXT);
        text.setFont(Theme.BODY);
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(icon, BorderLayout.WEST);
        top.add(text, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        final boolean[] retryFlag = {false};
        CutButton again = new CutButton(retry == null ? "Повторить" : "Повторить", CutButton.Accent.PRIMARY);
        again.setEnabled(retry != null);
        CutButton support = new CutButton("Тех. Поддержка", CutButton.Accent.NEUTRAL);
        CutButton close = new CutButton("Закрыть", CutButton.Accent.GHOST);
        again.addActionListener(e -> {
            retryFlag[0] = true;
            d.dispose();
        });
        support.addActionListener(e -> {
            supportOpener.run();
        });
        close.addActionListener(e -> d.dispose());
        buttons.add(again);
        buttons.add(support);
        if (details != null && !details.isBlank()) {
            CutButton more = new CutButton("Подробности", CutButton.Accent.GHOST);
            final String det = details;
            more.addActionListener(e -> showDetails(parent, det));
            buttons.add(more);
        }
        buttons.add(close);
        body.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(body);
        WindowBackground.dim(d);
        d.pack();
        center(d, parent);
        d.setVisible(true);
        if (retryFlag[0] && retry != null) retry.run();
        return retryFlag[0];
    }

    private static void showDetails(Component parent, String details) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), "Подробности",
                Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea ta = new JTextArea(details, 18, 70);
        ta.setEditable(false);
        ta.setFont(Theme.MONO);
        ta.setBackground(Theme.FIELD);
        ta.setForeground(Theme.TEXT);
        ta.setCaretColor(Theme.TEXT);
        JScrollPane detailsScroll = new JScrollPane(ta);
        Theme.style(detailsScroll);
        d.setContentPane(detailsScroll);
        WindowBackground.dim(d);
        d.setSize(760, 460);
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    /** Informational message with an optional action button. */
    public static boolean ask(Component parent, String message, String okText, String cancelText) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), "CWLauncher",
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setBackground(Theme.PANEL);
        body.setBorder(BorderFactory.createCompoundBorder(
                new CutBorder(Theme.INFO, 10), BorderFactory.createEmptyBorder(20, 22, 18, 22)));
        JLabel text = new JLabel("<html><div style='width:380px'>" + esc(message) + "</div></html>");
        text.setForeground(Theme.TEXT);
        text.setFont(Theme.BODY);
        body.add(text, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        final boolean[] res = {false};
        CutButton ok = new CutButton(okText, CutButton.Accent.PRIMARY);
        CutButton no = new CutButton(cancelText, CutButton.Accent.GHOST);
        ok.addActionListener(e -> {
            res[0] = true;
            d.dispose();
        });
        no.addActionListener(e -> d.dispose());
        buttons.add(ok);
        buttons.add(no);
        body.add(buttons, BorderLayout.SOUTH);
        d.setContentPane(body);
        WindowBackground.dim(d);
        d.pack();
        center(d, parent);
        d.setVisible(true);
        return res[0];
    }

    public static void info(Component parent, String message) {
        ask(parent, message, "Понятно", "Закрыть");
    }

    /**
     * Куда скачать выбранную версию: папка CWLauncher или своя папка через проводник Windows.
     * null — пользователь отменил установку.
     */
    public static java.nio.file.Path chooseInstallRoot(Component parent, String version) {
        java.nio.file.Path launcherDir = ru.cw.launcher.util.Paths.gamesRoot()
                .resolve(ru.cw.launcher.util.Paths.sanitize(version));
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), "Куда скачать",
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createCompoundBorder(
                new CutBorder(Theme.INFO, 10), BorderFactory.createEmptyBorder(20, 22, 18, 22)));
        JLabel text = new JLabel("<html><div style='width:460px'>Куда скачать Minecraft " + esc(version)
                + "?<br>В папку лаунчера в Program Files писать нельзя."
                + "<br>Обычное место: " + esc(launcherDir.toString())
                + "<br>У каждой версии свои моды, миры, звуки и языки.</div></html>");
        text.setForeground(Theme.TEXT);
        text.setFont(Theme.BODY);
        body.add(text, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        final java.nio.file.Path[] chosen = {null};
        final boolean[] pickOwn = {false};
        CutButton here = new CutButton("В папку CWLauncher", CutButton.Accent.PRIMARY, false);
        CutButton custom = new CutButton("Своя папка", CutButton.Accent.NEUTRAL, false);
        CutButton cancel = new CutButton("Отмена", CutButton.Accent.GHOST, false);
        here.addActionListener(e -> {
            chosen[0] = launcherDir;
            d.dispose();
        });
        custom.addActionListener(e -> {
            pickOwn[0] = true;
            d.setVisible(false);
        });
        cancel.addActionListener(e -> d.dispose());
        buttons.add(here);
        buttons.add(custom);
        buttons.add(cancel);
        body.add(buttons, BorderLayout.SOUTH);
        d.setContentPane(body);
        WindowBackground.dim(d);
        d.pack();
        center(d, parent);
        d.setVisible(true);
        if (pickOwn[0]) {
            d.dispose();
            return Utils.chooseDirectory(parent, "Выберите папку");
        }
        return chosen[0];
    }

    /** Уведомление-тост: недолгое сообщение в углу окна. Не повторяет одно и то же подряд. */
    public static boolean toast(Component anchor, String message, Color accent) {
        if (shownToasts.contains(message)) return false;
        shownToasts.add(message);
        if (shownToasts.size() > 40) shownToasts.remove(0);
        Window w = anchor instanceof Window win ? win : SwingUtilities.getWindowAncestor(anchor);
        if (w == null) return false;
        JWindow tw = new JWindow(w);
        JLabel label = new JLabel("<html><div style='width:320px'>" + esc(message) + "</div></html>");
        label.setForeground(Theme.TEXT);
        label.setFont(Theme.BODY);
        label.setBorder(BorderFactory.createCompoundBorder(
                new CutBorder(accent, 8), BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        label.setBackground(Theme.PANEL);
        label.setOpaque(true);
        tw.setContentPane(label);
        tw.pack();
        Point p = anchor.getLocationOnScreen();
        tw.setLocation(p.x + anchor.getWidth() - tw.getWidth() - 18, p.y + 64);
        tw.setVisible(true);
        Timer t = new Timer(6000, e -> {
            tw.dispose();
            shownToasts.remove(message);
        });
        t.setRepeats(false);
        t.start();
        return true;
    }

    public static void clearToastHistory() {
        shownToasts.clear();
    }

    private static void center(JDialog d, Component parent) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        if (w == null) {
            d.setLocationRelativeTo(null);
            return;
        }
        int x = w.getX() + (w.getWidth() - d.getWidth()) / 2;
        int y = w.getY() + (w.getHeight() - d.getHeight()) / 2;
        d.setLocation(Math.max(0, x), Math.max(0, y));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    /** Рамка со срезанными верхним-левым и нижним-правым углами (как у кнопок). */
    public static final class CutBorder extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int cut;

        public CutBorder(Color color, int cut) {
            this.color = color;
            this.cut = cut;
        }

        public static Shape shape(int w, int h, int cut) {
            java.awt.geom.GeneralPath p = new java.awt.geom.GeneralPath();
            p.moveTo(cut, 0);
            p.lineTo(w, 0);
            p.lineTo(w, h - cut);
            p.lineTo(w - cut, h);
            p.lineTo(0, h);
            p.lineTo(0, cut);
            p.closePath();
            return p;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(shape(width - 1, height - 1, cut));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(3, 3, 3, 3);
        }
    }
}
