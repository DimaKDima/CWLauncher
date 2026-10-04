package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.core.Operation;
import ru.cw.launcher.core.Progress;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.model.GameState;
import ru.cw.launcher.network.ServerStatus;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Центральный информационный блок главного меню (раздел 77 ТЗ).
 * Один и тот же блок меняет содержимое в состояниях A/B/C/D:
 * A — всё готово, B — ошибка сборки, C — идёт установка/обновление, D — доступно обновление.
 * Все строки берутся из реальных проверок LauncherApp, никаких декоративных статусов.
 */
public class StatusPanel extends JPanel {

    private final LauncherApp app;
    private final JLabel title = new JLabel(" ");
    private final Box rows = Box.createVerticalBox();
    private final JProgressBar bar = new JProgressBar();
    private final JLabel progressText = new JLabel(" ");
    private final JLabel subText = new JLabel(" ");
    private GameState shown = GameState.CHECKING;

    public StatusPanel(LauncherApp app) {
        this.app = app;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        title.setFont(Theme.H3);
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(10));
        rows.setAlignmentX(LEFT_ALIGNMENT);
        add(rows);
        bar.setUI(new Theme.FlatProgressBarUI());
        bar.setForeground(Theme.GREEN);
        bar.setBackground(Theme.FIELD);
        bar.setStringPainted(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.setVisible(false);
        bar.setMaximumSize(new Dimension(Short.MAX_VALUE, 10));
        bar.setPreferredSize(new Dimension(260, 10));
        add(bar);
        progressText.setFont(Theme.SMALL);
        progressText.setForeground(Theme.MUTED);
        progressText.setAlignmentX(LEFT_ALIGNMENT);
        add(progressText);
        subText.setFont(Theme.SMALL);
        subText.setForeground(Theme.MUTED);
        subText.setAlignmentX(LEFT_ALIGNMENT);
        add(subText);
    }

    /** Перерисовать блок по реальному состоянию. */
    public void refresh() {
        GameState st = app.state().get();
        shown = st;
        rows.removeAll();
        String detail = app.state().detail();
        subText.setText(Utils.isBlank(detail) ? " " : "<html><div style='width:300px'>" + detail
                + "</div></html>");
        switch (st) {
            case DOWNLOADING:
            case INSTALLING:
            case UPDATING:
            case CHECKING:
            case REPAIRING: {
                title.setText("Подготовка Common World...");
                title.setForeground(Theme.INFO);
                Operation op = app.currentOperation();
                Progress p = op == null ? null : op.progress();
                bar.setVisible(true);
                if (p == null) {
                    bar.setIndeterminate(true);
                    progressText.setText(" " + (op == null ? " " : op.name()));
                } else {
                    bar.setIndeterminate(p.indeterminate());
                    int filePct = p.total() > 0 ? (int) Math.min(100, p.done() * 100 / p.total()) : op.percent();
                    if (!p.indeterminate()) bar.setValue(Math.max(0, Math.min(100, filePct)));
                    else bar.setValue(0);
                    String line = p.stage();
                    if (!Utils.isBlank(p.text())) line += " · " + p.text();
                    if (!p.indeterminate()) line += " · " + filePct + "%";
                    if (p.speed() > 0) {
                        line += " · " + Utils.humanSize((long) p.speed()) + "/с";
                        if (p.etaSeconds() >= 0) line += " · осталось " + p.etaText();
                    }
                    progressText.setText("<html><div style='width:300px'>" + line + "</div></html>");
                }
                addRow("Minecraft", app.evaluate().mcReady() ? "готово" : "установка",
                        app.evaluate().mcReady() ? Tone.OK : Tone.RUN);
                String loader = app.fabric().installedLoader(app.cfg.minecraftVersion);
                addRow("Fabric", loader == null ? "установка" : loader,
                        loader == null ? Tone.RUN : Tone.OK);
                int n = app.mods().modCount(app.cfg.instanceId());
                addRow("Моды", n > 0 ? n + " шт." : "загрузка", n > 0 ? Tone.OK : Tone.RUN);
                addRow("Проверка", st == GameState.CHECKING ? "идёт..." : "ожидание", Tone.RUN);
                break;
            }
            case ERROR: {
                title.setText("Проблема сборки");
                title.setForeground(Theme.RED);
                bar.setVisible(false);
                progressText.setText(" ");
                List<String> problems = app.evaluate().modProblems();
                addRow("Статус", "требуется исправление", Tone.ERROR);
                int i = 0;
                for (String pr : problems) {
                    if (i++ >= 4) break;
                    addRow("Проблема", Utils.shorten(pr, 90), Tone.ERROR);
                }
                break;
            }
            case UPDATE_AVAILABLE: {
                title.setText("Доступно обновление");
                title.setForeground(Theme.AMBER);
                bar.setVisible(false);
                progressText.setText(" ");
                String installed = app.mods().installedBuildVersion(app.cfg.instanceId());
                Settings cfg = app.cfg;
                if (cfg.remoteLauncherVersion != null && !Log_VERSION().equals(cfg.remoteLauncherVersion)) {
                    addRow("CWLauncher", Log_VERSION() + "  →  " + cfg.remoteLauncherVersion, Tone.WARN);
                }
                addRow("Сборка", (installed == null ? "—" : installed) + "  →  "
                        + (cfg.remoteBuildVersion == null ? "—" : cfg.remoteBuildVersion), Tone.WARN);
                addRow("Minecraft " + cfg.minecraftVersion,
                        app.evaluate().mcReady() ? "установлен" : "не установлен",
                        app.evaluate().mcReady() ? Tone.OK : Tone.ERROR);
                break;
            }
            case OFFLINE: {
                title.setText("Оффлайн-режим");
                title.setForeground(Theme.AMBER);
                bar.setVisible(false);
                progressText.setText(" ");
                addRow("Локальная установка", "готова к запуску", Tone.OK);
                addRow("Сеть", "недоступна — обновление пропущено", Tone.WARN);
                break;
            }
            case NOT_INSTALLED: {
                title.setText("Сборка не установлена");
                title.setForeground(Theme.INFO);
                bar.setVisible(false);
                progressText.setText(" ");
                LauncherApp.GameStateSnapshot s = app.evaluate();
                addRow("Minecraft " + app.cfg.minecraftVersion, s.mcReady() ? "установлен" : "не установлен",
                        s.mcReady() ? Tone.OK : Tone.ERROR);
                addRow("Fabric", s.fabricReady() ? "установлен" : "не установлен",
                        s.fabricReady() ? Tone.OK : Tone.ERROR);
                addRow("Моды Common World", s.modsOk() ? "установлены" : "не установлены",
                        s.modsOk() ? Tone.OK : Tone.ERROR);
                break;
            }
            case RUNNING: {
                title.setText("Игра запущена");
                title.setForeground(Theme.GREEN);
                bar.setVisible(false);
                progressText.setText("Minecraft работает — лаунчер можно свернуть");
                break;
            }
            default: {
                title.setText("COMMON WORLD");
                title.setForeground(Theme.TEXT);
                bar.setVisible(false);
                progressText.setText(" ");
                for (Map<String, Object> r : app.statusCenter()) {
                    Tone t = switch (String.valueOf(r.get("tone"))) {
                        case "ok" -> Tone.OK;
                        case "warn" -> Tone.WARN;
                        case "error" -> Tone.ERROR;
                        default -> Tone.INFO;
                    };
                    addRow(String.valueOf(r.get("title")), String.valueOf(r.get("value")), t);
                }
                ServerStatus.Info srv = app.serverInfo();
                if (srv != null && srv.checkedAt() > 0) {
                    addRow("Проверено", Utils.timeAgo(srv.checkedAt()), Tone.INFO);
                }
                break;
            }
        }
        rows.revalidate();
        rows.repaint();
        revalidate();
        repaint();
    }

    private enum Tone {
        OK, WARN, ERROR, INFO, RUN
    }

    private void addRow(String name, String value, Tone tone) {
        JLabel dot = new JLabel("●");
        dot.setFont(Theme.BODY.deriveFont(11f));
        dot.setForeground(switch (tone) {
            case OK -> Theme.GREEN;
            case WARN -> Theme.AMBER;
            case ERROR -> Theme.RED;
            default -> Theme.INFO;
        });
        dot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        JLabel n = new JLabel(name);
        n.setFont(Theme.BODY);
        n.setForeground(Theme.MUTED);
        JLabel v = new JLabel(value);
        v.setFont(Theme.BODY.deriveFont(Font.BOLD));
        v.setForeground(Theme.TEXT);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, 22));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(n);
        row.add(left, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        rows.add(row);
        rows.add(Box.createVerticalStrut(3));
    }

    private static String Log_VERSION() {
        return ru.cw.launcher.util.Log.VERSION;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        g2.setColor(new Color(13, 17, 23, 214));
        g2.fill(Dialogs.CutBorder.shape(w, h, 12));
        g2.setColor(shown == GameState.ERROR ? new Color(Theme.RED.getRGB(), true) : new Color(0x2f4a6d));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(Dialogs.CutBorder.shape(w, h, 12));
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 320), Math.max(d.height, 150));
    }
}
