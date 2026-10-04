package ru.cw.launcher.ui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;

/**
 * Тёмная тема CWLauncher и глобальные настройки отрисовки.
 * Включает antialiasing по умолчанию для всего лаунчера.
 */
public final class Theme {

    private Theme() {
    }

    public static final Color BG = new Color(0x0B1220);
    public static final Color PANEL = new Color(0x101C33);
    public static final Color PANEL_2 = new Color(0x163056);
    public static final Color FIELD = new Color(0x0E1A30);
    public static final Color LINE = new Color(0x30363d);
    public static final Color TEXT = new Color(0xe6edf3);
    public static final Color MUTED = new Color(0x8b949e);
    public static final Color GREEN = new Color(0x3fb950);
    public static final Color AMBER = new Color(0xd29922);
    public static final Color RED = new Color(0xf85149);
    public static final Color INFO = new Color(0x58a6ff);
    public static final Color DISABLED = new Color(0x484f58);

    /** Семейство, в котором точно есть кириллица. Иначе буквы рисуются квадратами. */
    public static final String FAMILY = pickFamily();
    public static final Font H1 = new Font(FAMILY, Font.BOLD, 26);
    public static final Font H2 = new Font(FAMILY, Font.BOLD, 17);
    public static final Font H3 = new Font(FAMILY, Font.BOLD, 13);
    public static final Font BODY = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 11);
    public static final Font MONO = pickMono();

    private static String pickFamily() {
        String probe = "ОбновлениеЁёЖж";
        for (String name : new String[]{"Segoe UI", "Arial", "Tahoma", "Microsoft Sans Serif", "Dialog"}) {
            Font f = new Font(name, Font.PLAIN, 14);
            if (f.canDisplayUpTo(probe) == -1) return name;
        }
        return "Dialog";
    }

    private static Font pickMono() {
        Font f = new Font("Consolas", Font.PLAIN, 12);
        if (f.canDisplayUpTo("Обновление") == -1) return f;
        return new Font(FAMILY, Font.PLAIN, 12);
    }

    /** Применяет тему ко всему приложению (должно вызываться до создания окон). */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        FontUIResource uiFont = new FontUIResource(BODY);
        UIManager.put("defaultFont", uiFont);
        try {
            // Nimbus даёт предсказуемую тёмную палитру без зависимости от системной темы Windows
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(i.getName())) {
                    UIManager.setLookAndFeel(new NimbusLookAndFeel());
                    break;
                }
            }
        } catch (Exception ignored) {
            // остаёмся на дефолтном LaF — кастомные компоненты отрисованы сами
        }
        installNimbusOverrides();

        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.background", PANEL);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.selectionBackground", INFO);
        UIManager.put("TextField.selectionForeground", BG);
        UIManager.put("FormattedTextField.background", FIELD);
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("PasswordField.background", FIELD);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("TextArea.background", FIELD);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextPane.background", FIELD);
        UIManager.put("TextPane.foreground", TEXT);
        UIManager.put("EditorPane.background", FIELD);
        UIManager.put("EditorPane.foreground", TEXT);
        UIManager.put("ComboBox.background", FIELD);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", PANEL_2);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.background", FIELD);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", PANEL_2);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("Table.background", FIELD);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("TableHeader.background", PANEL_2);
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", PANEL_2);
        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("RadioButton.background", PANEL);
        UIManager.put("RadioButton.foreground", TEXT);
        UIManager.put("Button.background", PANEL_2);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("ToggleButton.background", PANEL_2);
        UIManager.put("ToggleButton.foreground", TEXT);
        UIManager.put("Separator.foreground", LINE);
        UIManager.put("ToolTip.background", PANEL_2);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new LineBorder(LINE, 1));
        UIManager.put("ProgressBar.background", FIELD);
        UIManager.put("ProgressBar.foreground", GREEN);
        UIManager.put("ProgressBar.selectionBackground", TEXT);
        UIManager.put("ProgressBar.selectionForeground", TEXT);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Panel.font", BODY);
        UIManager.put("Label.font", BODY);
        UIManager.put("TextField.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("Button.font", BODY);
        UIManager.put("List.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("ToolTip.font", SMALL);
        UIManager.put("Tree.background", FIELD);
        UIManager.put("Tree.foreground", TEXT);
        UIManager.put("ScrollBarUI", DarkScrollBarUI.class.getName());
        forceFonts();
    }

    /** Nimbus подставляет свой шрифт без части букв. Все шрифты темы заменяются на кириллический. */
    private static void forceFonts() {
        Font base = BODY;
        UIDefaults defs = UIManager.getLookAndFeelDefaults();
        for (Object key : new java.util.HashSet<>(defs.keySet())) {
            Object v = defs.get(key);
            if (v instanceof Font f) {
                defs.put(key, new FontUIResource(base.deriveFont(f.getStyle(), f.getSize2D())));
            }
        }
        UIManager.put("defaultFont", new FontUIResource(base));
    }

    private static void installNimbusOverrides() {
        try {
            Object control = UIManager.get("control");
            if (control != null) {
                UIManager.put("control", new ColorUIResource(PANEL));
            }
            UIManager.put("nimbusBase", new ColorUIResource(PANEL_2));
            UIManager.put("nimbusBlueGrey", new ColorUIResource(LINE));
            UIManager.put("nimbusLightBackground", new ColorUIResource(FIELD));
            UIManager.put("nimbusSelectionBackground", new ColorUIResource(PANEL_2));
            UIManager.put("text", new ColorUIResource(TEXT));
        } catch (Throwable ignored) {
        }
    }

    /** Цвет статуса по тексту (для подписей «Установлено» / «Требуется установка»). */
    public static Color forState(String state) {
        if (state == null) return MUTED;
        String s = state.toLowerCase();
        if (s.contains("не найден") || s.contains("ошиб") || s.contains("не установлен")
                || s.contains("поврежд")) return RED;
        if (s.contains("требуется") || s.contains("не совпадает") || s.contains("устар")) return AMBER;
        if (s.contains("установлен") || s.contains("запущен") || s.contains("в порядке")
                || s.contains("активен")) return GREEN;
        return MUTED;
    }

    /** Прогресс-бар в стиле лаунчера: тёмный фон, синяя/зелёная заливка, скругления. */
    public static final class FlatProgressBarUI extends javax.swing.plaf.basic.BasicProgressBarUI {

        private int percentDone() {
            int v = progressBar.getValue();
            int min = progressBar.getMinimum();
            int max = progressBar.getMaximum();
            if (max <= min) return 0;
            return (int) Math.max(0, Math.min(100, Math.round((v - min) * 100.0 / (max - min))));
        }

        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            int frac = percentDone();
            g2.setColor(c.getBackground());
            g2.fillRoundRect(0, 0, w, h, h, h);
            if (frac > 0) {
                int fw = Math.max(h, w * frac / 100);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x1f6feb), fw, h,
                        c.getForeground().darker()));
                g2.fillRoundRect(0, 0, fw, h, h, h);
                g2.setColor(new Color(255, 255, 255, 36));
                g2.fillRoundRect(0, 0, fw, Math.max(2, h / 2), h, h);
            }
            g2.setColor(new Color(0x30363d));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.dispose();
        }

        @Override
        protected void paintIndeterminate(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            g2.setColor(c.getBackground());
            g2.fillRoundRect(0, 0, w, h, h, h);
            int bw = Math.max(24, w / 5);
            double t = (System.currentTimeMillis() % 2400) / 2400.0;
            int x = (int) Math.round(-bw + t * (w + bw));
            g2.setPaint(new GradientPaint(x, 0, new Color(0x1f6feb), x + bw, h, new Color(0x58a6ff)));
            g2.fillRoundRect(Math.max(0, x), 0, Math.min(bw, w), h, h, h);
            g2.setColor(new Color(0x30363d));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.dispose();
        }
    }

    /** Не даёт подписи и кнопке растянуться на всю высоту и наехать на соседа. */
    public static void limit(JComponent c) {
        if (c == null) return;
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = c.getPreferredSize();
        int h = Math.max(22, pref.height);
        if (c instanceof JTextField || c instanceof JComboBox || c instanceof JSpinner) h = Math.max(h, 34);
        if (c instanceof JButton) h = Math.max(h, 38);
        if (c instanceof JSlider) h = Math.max(h, 46);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    /** Подпись поля/строки текста. */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(BODY);
        l.setOpaque(false);
        limit(l);
        return l;
    }

    /** Второстепенная подпись. */
    public static JLabel muted(String text) {
        JLabel l = label(text);
        l.setForeground(MUTED);
        l.setFont(SMALL);
        return l;
    }

    /** Однострочное поле ввода в стиле лаунчера. */
    public static JTextField field(String value, int columns) {
        JTextField f = new JTextField(value, columns);
        f.setBackground(FIELD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(BODY);
        f.setBorder(BorderFactory.createCompoundBorder(
                new Dialogs.CutBorder(LINE, 6), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        limit(f);
        return f;
    }

    /** Чекбокс (переключатель настройки). */
    public static JCheckBox check(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setOpaque(false);
        cb.setForeground(TEXT);
        cb.setFont(BODY);
        cb.setBackground(PANEL);
        cb.setIcon(new BlueCheck(false));
        cb.setSelectedIcon(new BlueCheck(true));
        cb.setDisabledIcon(new BlueCheck(false));
        cb.setPressedIcon(new BlueCheck(cb.isSelected()));
        limit(cb);
        return cb;
    }

    /** Панель-карточка с заголовком раздела. */
    public static JPanel section(String title, java.util.function.Consumer<JPanel> fill) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                new CardBorder(LINE, 10, 2), BorderFactory.createEmptyBorder(8, 10, 12, 10)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JLabel t = new JLabel(title);
        t.setForeground(INFO);
        t.setFont(H3);
        p.add(t, c);
        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        JPanel inner = new JPanel(new GridBagLayout());
        inner.setOpaque(false);
        p.add(inner, c);
        fill.accept(inner);
        return p;
    }

    /** Строка «подпись — поле» внутри GridBagLayout. */
    public static void row(JPanel panel, String label, JComponent field, JComponent... extra) {
        GridBagConstraints c = new GridBagConstraints();
        int y = panel.getComponentCount();
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 12);
        JLabel l = new JLabel(label);
        l.setForeground(MUTED);
        l.setFont(BODY);
        panel.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        panel.add(field, c);
        for (int i = 0; i < extra.length; i++) {
            c.gridx = 2 + i;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(4, 6, 4, 0);
            panel.add(extra[i], c);
        }
    }

    /** Ровная рамка в 1 пиксель. */
    public static final class LineBorder extends javax.swing.border.LineBorder {
        public LineBorder(Color c, int thickness) {
            super(c, thickness);
        }
    }

    /** Внутренний скруглённый бордюр для панелей (визуальный отступ + линия). */
    public static final class CardBorder extends AbstractBorder {
        private final Color line;
        private final int radius;
        private final int inset;

        public CardBorder(Color line, int radius, int inset) {
            this.line = line;
            this.radius = radius;
            this.inset = inset;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(line);
            g2.drawRoundRect(x + inset / 2, y + inset / 2, width - inset - 1, height - inset - 1, radius,
                    radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(inset + 6, inset + 10, inset + 6, inset + 10);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            return getBorderInsets(c);
        }
    }

    /** Основной акцентный цвет (синий). */
    public static final Color ACCENT = new Color(0x2F80ED);
    /** Мягкий голубой для подсветки заголовков. */
    public static final Color ACCENT_SOFT = new Color(0x56A8FF);

    /** Антиaliasing для произвольного компонента. */
    public static void install(JComponent c) {
        c.putClientProperty("swing.paintantialiasing", "on");
        c.putClientProperty("swing.textantialiasing", "on");
    }

    /** Стилизует прогресс-бар. */
    public static void style(JProgressBar bar) {
        bar.setUI(new FlatProgressBarUI());
        bar.setForeground(ACCENT);
        bar.setBackground(FIELD);
        bar.setFont(SMALL);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    /** Стилизует текстовое поле. */
    public static void style(JTextField f) {
        f.setBackground(FIELD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(BODY);
        f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    /** Стилизует область текста. */
    public static void style(JTextArea t) {
        t.setBackground(FIELD);
        t.setForeground(TEXT);
        t.setCaretColor(TEXT);
        t.setFont(MONO);
        t.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    /** Стилизует список. */
    public static void style(JList<?> l) {
        l.setBackground(FIELD);
        l.setForeground(TEXT);
        l.setSelectionBackground(ACCENT);
        l.setSelectionForeground(Color.WHITE);
        l.setFont(BODY);
    }

    /** Основной текст. */
    public static JLabel text(String value) {
        JLabel l = new JLabel(value);
        l.setFont(BODY);
        l.setForeground(TEXT);
        l.setOpaque(false);
        return l;
    }

    /** Однотонная панель-карточка. */
    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(new CardBorder(LINE, 12, 2),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        return p;
    }

    /** Скруглённая форма для pop-up окон. */
    public static java.awt.Shape roundRect(int w, int h, int r) {
        return new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, r, r);
    }

    /** Сохраняет выбор темы (dark/light) в настройках. */
    public static void setThemeLight(boolean light) {
        ru.cw.launcher.settings.SettingsManager.update(s -> s.theme = light ? "light" : "dark");
    }

    public static boolean isLight() {
        return ru.cw.launcher.settings.SettingsManager.get().isLightTheme();
    }

    /** Палитра с учётом темы: светлая тема сохраняет синий акцент. */
    public static Color bg(boolean light) {
        return light ? new Color(0xEEF3FA) : BG;
    }

    public static Color panel(boolean light) {
        return light ? Color.WHITE : PANEL;
    }

    public static Color text(boolean light) {
        return light ? new Color(0x111827) : TEXT;
    }

    public static void style(JScrollPane sp) {
        sp.setBorder(null);
        sp.getViewport().setBackground(PANEL);
        sp.setBackground(PANEL);
        sp.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setBackground(FIELD);
    }

    public static void style(JSpinner spinner) {
        limit(spinner);
        spinner.setUI(new BasicSpinnerUI() {
            @Override
            protected Component createNextButton() {
                return arrow(true);
            }

            @Override
            protected Component createPreviousButton() {
                return arrow(false);
            }

            private JButton arrow(boolean up) {
                JButton b = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(0x1A3F73));
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(Color.WHITE);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        int[] xs = {cx - 4, cx + 4, cx};
                        int[] ys = up ? new int[]{cy + 2, cy + 2, cy - 3} : new int[]{cy - 2, cy - 2, cy + 3};
                        g2.fillPolygon(xs, ys, 3);
                        g2.dispose();
                    }
                };
                b.setBackground(new Color(0x1A3F73));
                b.setBorder(BorderFactory.createEmptyBorder());
                b.setFocusable(false);
                return b;
            }
        });
        spinner.setFont(BODY);
        spinner.setBorder(BorderFactory.createLineBorder(new Color(0x4C8DFF)));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JTextField f = de.getTextField();
            f.setBackground(FIELD);
            f.setForeground(TEXT);
            f.setCaretColor(TEXT);
            f.setFont(BODY);
            f.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            de.setBackground(FIELD);
        }
    }

    public static void style(JComboBox<?> box) {
        box.setBackground(FIELD);
        box.setForeground(TEXT);
        box.setFont(BODY);
        limit(box);
        box.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(0x1A3F73));
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(Color.WHITE);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        g2.fillPolygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
                        g2.dispose();
                    }
                };
                b.setBackground(new Color(0x1A3F73));
                b.setBorder(BorderFactory.createEmptyBorder());
                b.setFocusable(false);
                return b;
            }
        });
    }

    public static void style(JTabbedPane tabs) {
        tabs.setUI(new BlueTabUI());
        tabs.setFont(BODY);
        tabs.setForeground(TEXT);
        tabs.setBackground(PANEL);
        tabs.setOpaque(true);
    }

    public static final class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0x3B82F6);
            trackColor = new Color(0x0E1A30);
            thumbDarkShadowColor = thumbColor;
            thumbHighlightColor = new Color(0x4C8DFF);
            thumbLightShadowColor = thumbColor;
            trackHighlightColor = trackColor;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zero();
        }

        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setFocusable(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(new Color(0x0E1A30));
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.width < 2 || r.height < 2) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(0x4C8DFF) : new Color(0x2F6FE0));
            g2.fillRoundRect(r.x + 2, r.y + 2, Math.max(4, r.width - 4), Math.max(4, r.height - 4), 8, 8);
            g2.dispose();
        }
    }

    public static final class BlueTabUI extends BasicTabbedPaneUI {
        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            g.setColor(isSelected ? new Color(0x1A3F73) : new Color(0x101C33));
            g.fillRect(x, y, w, h);
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            g.setColor(new Color(0x4C8DFF));
            g.drawLine(x, y + h - 1, x + w, y + h - 1);
            if (isSelected) g.drawRect(x, y, w - 1, h - 1);
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            int w = tabPane.getWidth();
            int h = tabPane.getHeight();
            g.setColor(new Color(0x163056));
            g.drawRect(0, 0, w - 1, h - 1);
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                 int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            g.setFont(BODY);
            g.setColor(isSelected ? Color.WHITE : new Color(0xC5D2E8));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, textRect.x, textRect.y + fm.getAscent());
        }
    }

    private static final class BlueCheck implements Icon {
        private final boolean on;

        BlueCheck(boolean on) {
            this.on = on;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0x0E1A30));
            g2.fillRoundRect(x, y, 16, 16, 4, 4);
            g2.setColor(new Color(0x4C8DFF));
            g2.drawRoundRect(x, y, 15, 15, 4, 4);
            if (on) {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{x + 3, x + 7, x + 12}, new int[]{y + 8, y + 12, y + 4}, 3);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
}
