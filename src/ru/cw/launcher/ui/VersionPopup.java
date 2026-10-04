package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.profiles.LauncherProfile;
import ru.cw.launcher.settings.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Выбор версии Minecraft. Список в столбик, ширина по тексту, без горизонтальной прокрутки.
 * Установленная версия рисуется другим, более ярким фоном.
 */
public class VersionPopup extends JWindow {

    private final LauncherApp app;
    private final Runnable onChange;
    private final JPanel list = new JPanel();
    private final JLabel hint = new JLabel(" ");
    private final JScrollPane scroll;
    private final List<Component> ignored = new ArrayList<>();

    public VersionPopup(Window owner, LauncherApp app, Runnable onChange) {
        super(owner);
        this.app = app;
        this.onChange = onChange;
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                new Dialogs.CutBorder(Theme.INFO, 8),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        hint.setFont(Theme.BODY);
        hint.setForeground(Theme.MUTED);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(true);
        list.setBackground(Theme.PANEL);
        scroll = new JScrollPane(list);
        Theme.style(scroll);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setOpaque(true);
        scroll.setBackground(Theme.PANEL);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(Theme.PANEL);
        CutButton close = new CutButton("Закрыть", CutButton.Accent.GHOST, false);
        close.addActionListener(e -> dismiss());
        close.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                e.consume();
                dismiss();
            }
        });
        JPanel south = new JPanel(null) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, 30);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 30);
            }

            @Override
            public void doLayout() {
                int bw = 112;
                int bh = 26;
                close.setBounds(Math.max(0, getWidth() - bw), Math.max(0, (getHeight() - bh) / 2), bw, bh);
            }
        };
        south.setOpaque(false);
        south.add(close);
        root.add(hint, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        root.registerKeyboardAction(e -> dismiss(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setContentPane(root);
        WindowBackground.dim(this);
        Toolkit.getDefaultToolkit().addAWTEventListener(clicks(), AWTEvent.MOUSE_EVENT_MASK);
    }

    /** Клики по этим кнопкам не закрывают список: ими список открывается и закрывается. */
    public void ignoreOutside(Component... anchors) {
        ignored.clear();
        for (Component c : anchors) if (c != null) ignored.add(c);
    }

    public void dismiss() {
        setVisible(false);
    }

    private AWTEventListener clicks() {
        return event -> {
            if (!isVisible() || !(event instanceof MouseEvent me)) return;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;
            Point screen;
            try {
                screen = me.getLocationOnScreen();
            } catch (Exception ex) {
                return;
            }
            Component src = me.getComponent();
            if (src instanceof Row) return;
            if (isScroll(src)) return;
            if (!getBounds().contains(screen)) {
                for (Component anchor : ignored) {
                    if (src == anchor || (src != null && SwingUtilities.isDescendingFrom(src, anchor))) return;
                }
            }
            dismiss();
        };
    }

    private static boolean isScroll(Component c) {
        for (Component n = c; n != null; n = n.getParent()) {
            if (n instanceof JScrollBar) return true;
        }
        return false;
    }

    private int topLimit = -1;
    private int bottomLimit = Integer.MAX_VALUE;

    /** Список от кнопки «Выбор версии» до кнопки «Тех. Поддержка». */
    public void showBelow(Component anchor, int topLimit, int bottomLimit) {
        this.topLimit = topLimit;
        this.bottomLimit = bottomLimit;
        Point anchorAt = anchor.getLocationOnScreen();
        int w = Math.max(520, anchor.getWidth());
        Rectangle area = band();
        setSize(Math.min(w, area.width), area.height);
        place(anchorAt.x, area.y);
        hint.setText("Загрузка списка версий…");
        list.removeAll();
        setVisible(true);
        toFront();
        requestFocus();
        app.io().submit(() -> {
            List<Map<String, Object>> versions;
            String error = null;
            try {
                versions = new ArrayList<>(app.mc().selectableVersions());
            } catch (Exception e) {
                versions = new ArrayList<>();
                error = ru.cw.launcher.util.Log.reason(e);
            }
            boolean hasCurrent = false;
            for (Map<String, Object> v : versions) {
                if (app.cfg.minecraftVersion.equals(String.valueOf(v.get("id")))) hasCurrent = true;
            }
            if (!hasCurrent) {
                Map<String, Object> cur = new java.util.LinkedHashMap<>();
                cur.put("id", app.cfg.minecraftVersion);
                cur.put("installed", app.mc().versionReady(app.cfg.minecraftVersion));
                cur.put("label", "Minecraft " + app.cfg.minecraftVersion);
                versions.add(0, cur);
            }
            List<Map<String, Object>> ready = versions;
            String err = error;
            SwingUtilities.invokeLater(() -> fill(ready, err));
        });
    }

    /**
     * Полоса внутри лаунчера: сверху остаётся ряд с настройками,
     * снизу — «Тех. Поддержка» и нижние кнопки. Список не вылезает за окно.
     */
    private Rectangle band() {
        Window owner = getOwner();
        if (owner != null && owner.isShowing()) {
            Point o = owner.getLocationOnScreen();
            int top = topLimit > 0 ? topLimit : o.y + 74;
            int maxBottom = bottomLimit < Integer.MAX_VALUE / 2
                    ? bottomLimit : o.y + owner.getHeight() - 16;
            if (maxBottom < top + 120) top = Math.max(o.y + 8, maxBottom - 240);
            int height = Math.max(120, maxBottom - top);
            return new Rectangle(o.x + 12, top, Math.max(280, owner.getWidth() - 24), height);
        }
        Rectangle screen = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        return new Rectangle(screen.x + 12, screen.y + 74,
                Math.max(280, screen.width - 24), Math.max(220, screen.height - 202));
    }

    private void place(int x, int y) {
        Rectangle band = band();
        int w = Math.min(getWidth(), band.width);
        int h = Math.min(getHeight(), band.height);
        setSize(w, h);
        x = band.x + Math.max(0, (band.width - w) / 2);
        if (y + h > band.y + band.height) y = band.y + band.height - h;
        if (y < band.y) y = band.y;
        setLocation(x, y);
    }

    private void fill(List<Map<String, Object>> versions, String error) {
        list.removeAll();
        if (versions.isEmpty()) {
            hint.setText(error == null ? "Список версий пуст" : "Список недоступен: " + error);
        } else {
            hint.setText(error == null
                    ? "Сервер Common World ставит 1.20.1 Fabric и моды. Остальные строки — обычный Minecraft."
                    : "Показаны локальные версии");
        }
        Font font = Theme.BODY.deriveFont(Font.BOLD, 15f);
        FontMetrics fm = getFontMetrics(font);
        int widest = 560;
        int fabricExtra = 28 + fm.stringWidth("Fabric");
        boolean serverInstalled = app.mc().versionReady("1.20.1");
        Row server = new Row("common-world", "Сервер Common World",
                serverInstalled ? "установлена" : "не установлена",
                serverInstalled, app.cfg.commonWorld, false, font, true);
        widest = Math.max(widest, 36 + fm.stringWidth("Сервер Common World") + 28 + fm.stringWidth("установлена") + 24);
        list.add(server);
        list.add(Box.createVerticalStrut(6));
        for (Map<String, Object> v : versions) {
            String id = String.valueOf(v.get("id"));
            boolean installed = Boolean.TRUE.equals(v.get("installed"));
            boolean current = !app.cfg.commonWorld && id.equalsIgnoreCase(app.cfg.minecraftVersion);
            String left = "Minecraft " + id;
            String right = installed ? "установлена" : "не установлена";
            boolean fabricOn = app.cfg.fabricFor(id);
            Row row = new Row(id, left, right, installed, current, fabricOn, font, false);
            widest = Math.max(widest, 36 + fm.stringWidth(left) + fabricExtra + 28 + fm.stringWidth(right) + 24);
            list.add(row);
            list.add(Box.createVerticalStrut(6));
        }
        Rectangle band = band();
        int width = Math.min(widest + 18, band.width);
        int height = Math.min(118 + Math.max(1, versions.size() + 1) * 52, band.height);
        Point where = getLocation();
        setSize(width, height);
        place(where.x, where.y);
        list.revalidate();
        list.repaint();
        scroll.getVerticalScrollBar().setValue(0);
    }

    private void chooseServer() {
        SettingsManager.update(s -> {
            s.commonWorld = true;
            s.minecraftVersion = "1.20.1";
            s.loader = "fabric";
        });
        LauncherProfile profile = app.profile();
        if (profile != null) {
            profile.minecraftVersion = "1.20.1";
            app.profiles().update(profile);
        }
        dismiss();
        app.bindPaths();
        app.recheckMinecraftFiles();
        if (onChange != null) onChange.run();
        ru.cw.launcher.util.Log.info("Выбрана сборка сервера Common World");
    }

    private void choose(String id) {
        SettingsManager.update(s -> {
            s.commonWorld = false;
            s.minecraftVersion = id;
            s.loader = s.fabricFor(id) ? "fabric" : "vanilla";
        });
        LauncherProfile profile = app.profile();
        if (profile != null) {
            profile.minecraftVersion = id;
            app.profiles().update(profile);
        }
        dismiss();
        app.bindPaths();
        app.recheckMinecraftFiles();
        if (onChange != null) onChange.run();
        ru.cw.launcher.util.Log.info("Выбрана версия Minecraft " + id
                + (app.cfg.fabricFor(id) ? " с Fabric" : " без Fabric"));
    }

    private void toggleFabric(Row row) {
        SettingsManager.update(s -> s.fabricVersions.put(row.id, !s.fabricFor(row.id)));
        row.fabricOn = app.cfg.fabricFor(row.id);
        row.repaint();
        if (row.id.equalsIgnoreCase(app.cfg.minecraftVersion)) {
            app.refreshState();
            if (onChange != null) onChange.run();
        }
        ru.cw.launcher.util.Log.info("Fabric для " + row.id + (row.fabricOn ? " включён" : " выключен"));
    }

    /** Строка версии на всю ширину списка. Установленная — яркий зелёный фон. */
    private final class Row extends JComponent {
        private final String id;
        private final String left;
        private final String right;
        private final boolean installed;
        private final boolean current;
        private boolean fabricOn;
        private final boolean server;
        private boolean hover;

        Row(String id, String left, String right, boolean installed, boolean current, boolean fabricOn, Font font,
            boolean server) {
            this.id = id;
            this.left = left;
            this.right = right;
            this.installed = installed;
            this.current = current;
            this.fabricOn = fabricOn;
            this.server = server;
            setFont(font);
            setOpaque(false);
            setAlignmentX(0f);
            setPreferredSize(new Dimension(10, 46));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (server) {
                        chooseServer();
                        return;
                    }
                    if (fabricHit().contains(e.getPoint())) {
                        toggleFabric(Row.this);
                        return;
                    }
                    choose(id);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fill;
            Color line;
            if (installed) {
                fill = hover ? new Color(0x4A82F0) : new Color(0x2F6FE0);
                line = new Color(0x8EB4FF);
            } else {
                fill = hover ? new Color(0x2C5288) : new Color(0x173056);
                line = current ? new Color(0x3D7AD4) : new Color(0x2A4E78);
            }
            CutPaint.button(g2, getWidth(), getHeight(), fill, line);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(Color.WHITE);
            g2.drawString(left, 18, ty);
            if (server) {
                String status = right;
                int rx = getWidth() - 16 - fm.stringWidth(status);
                g2.setColor(Color.WHITE);
                g2.drawString(status, Math.max(18 + fm.stringWidth(left) + 16, rx), ty);
                g2.dispose();
                return;
            }
            Rectangle box = fabricBox(fm);
            g2.setColor(new Color(0x0B1C36));
            g2.fillRoundRect(box.x, box.y, box.width, box.height, 4, 4);
            g2.setColor(fabricOn ? new Color(0x3DDC97) : new Color(0x8EA6CC));
            g2.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 4, 4);
            if (fabricOn) {
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(box.x + 3, box.y + 8, box.x + 7, box.y + 12);
                g2.drawLine(box.x + 7, box.y + 12, box.x + 13, box.y + 4);
            }
            g2.setColor(Color.WHITE);
            g2.drawString("Fabric", box.x + box.width + 8, ty);
            String status = right;
            int rx = getWidth() - 16 - fm.stringWidth(status);
            int minRight = box.x + box.width + 8 + fm.stringWidth("Fabric") + 12;
            g2.setColor(Color.WHITE);
            g2.drawString(status, Math.max(minRight, rx), ty);
            g2.dispose();
        }

        private Rectangle fabricBox(FontMetrics fm) {
            int size = 16;
            int x = 18 + fm.stringWidth(left) + 14;
            int y = (getHeight() - size) / 2;
            return new Rectangle(x, y, size, size);
        }

        private Rectangle fabricHit() {
            FontMetrics fm = getFontMetrics(getFont());
            Rectangle box = fabricBox(fm);
            int label = fm.stringWidth("Fabric");
            return new Rectangle(box.x - 4, 0, box.width + 12 + label, getHeight());
        }
    }
}
