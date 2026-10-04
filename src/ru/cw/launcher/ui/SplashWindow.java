package ru.cw.launcher.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Окно подготовки. Срез углов совпадает у заливки и синей обводки,
 * поэтому по краям не остаётся чёрного прямоугольника.
 */
public class SplashWindow extends JFrame {

    private static final int CUT = 22;

    private final JLabel title = new JLabel("CWLauncher", SwingConstants.CENTER);
    private final JLabel stage = new JLabel("Загрузка данных о лаунчере", SwingConstants.CENTER);
    private final JLabel percent = new JLabel("0%", SwingConstants.CENTER);
    private final JProgressBar bar = new JProgressBar(0, 100);

    public SplashWindow() {
        super("CWLauncher");
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setBackground(new Color(0x0B1220));
        try {
            setIconImage(Icons.appIcon());
        } catch (Exception ignored) {
        }
        JPanel root = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape fill = CutPaint.shape(getWidth(), getHeight(), CUT);
                g2.setColor(new Color(0x0B1220));
                g2.fill(fill);
                g2.setColor(new Color(0x3B82F6));
                g2.setStroke(new BasicStroke(1.8f));
                g2.draw(CutPaint.shape(Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), CUT));
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(26, 32, 24, 32));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        title.setFont(new Font(Theme.FAMILY, Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        root.add(title, c);
        c.gridy = 1;
        c.insets = new Insets(10, 0, 0, 0);
        stage.setFont(new Font(Theme.FAMILY, Font.PLAIN, 14));
        stage.setForeground(new Color(0xC5D4EA));
        root.add(stage, c);
        c.gridy = 2;
        c.insets = new Insets(18, 0, 0, 0);
        bar.setUI(new Theme.FlatProgressBarUI());
        bar.setForeground(new Color(0x3B82F6));
        bar.setBackground(new Color(0x162033));
        bar.setPreferredSize(new Dimension(360, 10));
        bar.setMaximumSize(new Dimension(360, 10));
        bar.setStringPainted(false);
        bar.setBorderPainted(false);
        JPanel barWrap = new JPanel(new GridBagLayout());
        barWrap.setOpaque(false);
        barWrap.add(bar);
        root.add(barWrap, c);
        c.gridy = 3;
        c.insets = new Insets(10, 0, 0, 0);
        percent.setFont(new Font(Theme.FAMILY, Font.BOLD, 14));
        percent.setForeground(Color.WHITE);
        root.add(percent, c);
        setContentPane(root);
        setSize(480, 210);
        setLocationRelativeTo(null);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                clip();
            }
        });
        clip();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                System.exit(0);
            }
        });
    }

    private void clip() {
        if (getWidth() < 8 || getHeight() < 8) return;
        setShape(CutPaint.shape(getWidth(), getHeight(), CUT));
    }

    /** Реальный шаг локальной проверки, 0–100. */
    public void step(int value, String text) {
        Runnable r = () -> {
            int v = Math.max(0, Math.min(100, value));
            bar.setIndeterminate(false);
            bar.setValue(v);
            percent.setText(v + "%");
            stage.setText(text == null || text.isBlank() ? " " : text);
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
