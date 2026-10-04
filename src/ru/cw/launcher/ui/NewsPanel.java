package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.news.NewsManager;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Панель новостей Common World (дополнительная функция 7).
 * Показывает реальные записи из news.json (источник или кэш) и время последней проверки.
 * При недоступности источника не ломается: показывает кэш либо понятное сообщение.
 */
public class NewsPanel extends JPanel {

    private final LauncherApp app;
    private final Box box = Box.createVerticalBox();
    private final JLabel header = new JLabel("НОВОСТИ COMMON WORLD");
    private final JLabel stamp = new JLabel(" ");
    private int shownHash;

    public NewsPanel(LauncherApp app) {
        this.app = app;
        setOpaque(false);
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        header.setFont(Theme.H3);
        header.setForeground(Theme.INFO);
        stamp.setFont(Theme.SMALL);
        stamp.setForeground(Theme.MUTED);
        add(header, BorderLayout.NORTH);
        add(stamp, BorderLayout.SOUTH);
        JScrollPane sp = new JScrollPane(box);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(null);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(14);
        add(sp, BorderLayout.CENTER);
    }

    /** Обновление только когда данные реально изменились — без лишней нагрузки на EDT. */
    public void refresh() {
        NewsManager n = app.news();
        int h = hash(n);
        if (h == shownHash) return;
        shownHash = h;
        box.removeAll();
        List<NewsManager.Item> items = n.items();
        if (items.isEmpty()) {
            box.add(card("Новости недоступны",
                    n.failed() ? "Источник news.json не отвечает. Проверьте интернет или адрес в настройках."
                            : "Источник новостей не настроен. Укажите newsUrl в настройках.",
                    Theme.MUTED, null));
            stamp.setText(n.failed() ? "Источник недоступен" : "Не настроено");
        } else {
            int i = 0;
            for (NewsManager.Item it : items) {
                if (i++ >= 3) break;
                box.add(card(it.title(), it.text(), i == 1 ? Theme.TEXT : Theme.MUTED, it.url()));
            }
            stamp.setText((n.fromCache() ? "Из кэша · " : "Проверено ") + Utils.timeAgo(n.checkedAt()));
        }
        box.revalidate();
        box.repaint();
    }

    /** Обновляет относительное время без пересборки карточек. */
    public void tick() {
        NewsManager n = app.news();
        if (n.checkedAt() > 0 && !n.items().isEmpty()) {
            stamp.setText((n.fromCache() ? "Из кэша · " : "Проверено ") + Utils.timeAgo(n.checkedAt()));
        }
    }

    private JComponent card(String title, String text, Color color, String url) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Short.MAX_VALUE, 96));
        JLabel t = new JLabel("<html><div style='width:290px'>" + esc(title) + "</div></html>");
        t.setFont(Theme.H3);
        t.setForeground(color);
        t.setAlignmentX(LEFT_ALIGNMENT);
        JTextPane c = new JTextPane();
        c.setText(Utils.isBlank(text) ? " " : text);
        c.setEditable(false);
        c.setOpaque(false);
        c.setForeground(Theme.MUTED);
        c.setFont(Theme.SMALL);
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(290, 40));
        c.setMaximumSize(new Dimension(Short.MAX_VALUE, 44));
        if (!Utils.isBlank(url)) {
            c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            c.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Utils.openUrl(url);
                }
            });
        }
        p.add(t);
        p.add(c);
        return p;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static int hash(NewsManager n) {
        int h = (n.fromCache() ? 1 : 0) * 31 + (n.failed() ? 7 : 0);
        for (NewsManager.Item it : n.items()) {
            h = h * 31 + it.title().hashCode();
            h = h * 31 + it.url().hashCode();
        }
        return h * 31 + (int) (n.checkedAt() / 60000);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        g2.setColor(new Color(13, 17, 23, 200));
        g2.fill(Dialogs.CutBorder.shape(w, h, 10));
        g2.setColor(new Color(0x2a3546));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(Dialogs.CutBorder.shape(w, h, 10));
        g2.dispose();
    }
}
