package ru.cw.launcher.ui;

import javax.swing.Icon;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Векторные иконки интерфейса — рисуются кодом, без внешних файлов.
 * Единый стиль: тонкие линии, скругления, минимализм.
 */
public final class Icons {

    public enum Kind {
        SETTINGS, DISCORD, TELEGRAM, USER, PLAY, DOWNLOAD, UPDATE, CANCEL, FOLDER, MODS, EXIT,
        CHECK, CROSS, WARN, INFO, REFRESH, SEARCH, NEWS, HISTORY, DOCTOR, JAVA, MEMORY, NET, SERVER,
        COPY, TRASH, PLUS, EDIT, CLOSE, MIN, MAX, CHEVRON, GALLERY, PROFILE, SHIELD, CLOCK, PICKAXE, SKIN
    }

    private final Kind kind;
    private final int size;

    public Icons(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    public static Icons of(Kind k) {
        return new Icons(k, 16);
    }

    public static Icons of(Kind k, int size) {
        return new Icons(k, size);
    }

    /** Иконка окна, нарисованная в том же стиле. */
    public static Image appIcon() {
        int n = 64;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.ACCENT);
        g.fill(new RoundRectangle2D.Float(2, 2, n - 4, n - 4, 16, 16));
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 30));
        FontMetrics fm = g.getFontMetrics();
        String s = "CW";
        g.drawString(s, (n - fm.stringWidth(s)) / 2f, (n - fm.getHeight()) / 2f + fm.getAscent());
        g.dispose();
        return img;
    }

    public Icon getIconObject() {
        return new javax.swing.Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                paint(c.getForeground(), g, x, y, size, size);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    public void paint(Color color, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(color);
        float s = Math.min(w, h) / 24f;
        g2.translate(x + (w - 24 * s) / 2f, y + (h - 24 * s) / 2f);
        g2.scale(s, s);
        g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case SETTINGS -> gear(g2);
            case DISCORD -> discord(g2);
            case TELEGRAM -> telegram(g2);
            case USER, PROFILE -> user(g2);
            case PLAY -> play(g2);
            case DOWNLOAD -> download(g2);
            case UPDATE -> refresh(g2);
            case CANCEL -> cross(g2);
            case FOLDER -> folder(g2);
            case MODS -> cube(g2);
            case EXIT -> exit(g2);
            case CHECK -> check(g2);
            case CROSS, CLOSE -> cross(g2);
            case WARN -> warn(g2);
            case INFO -> info(g2);
            case REFRESH -> refresh(g2);
            case SEARCH -> search(g2);
            case NEWS -> news(g2);
            case HISTORY -> clock(g2);
            case DOCTOR -> shield(g2);
            case JAVA -> cup(g2);
            case MEMORY -> chip(g2);
            case NET, SERVER -> signal(g2);
            case COPY -> copy(g2);
            case TRASH -> trash(g2);
            case PLUS -> plus(g2);
            case EDIT -> pencil(g2);
            case MIN -> line(g2, 5, 12, 19, 12);
            case MAX -> square(g2);
            case CHEVRON -> chevron(g2);
            case GALLERY -> image(g2);
            case SHIELD -> shield(g2);
            case CLOCK -> clock(g2);
            case PICKAXE -> pickaxe(g2);
            case SKIN -> skin(g2);
        }
        g2.dispose();
    }

    // ------------------------------------------------------------------ рисунки

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
    }

    private void gear(Graphics2D g) {
        g.draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            double r1 = 8.5;
            double r2 = 11.2;
            g.draw(new java.awt.geom.Line2D.Double(12 + Math.cos(a) * r1, 12 + Math.sin(a) * r1,
                    12 + Math.cos(a) * r2, 12 + Math.sin(a) * r2));
        }
    }

    private void discord(Graphics2D g) {
        g.fill(new RoundRectangle2D.Double(3.5, 6, 17, 12.5, 6, 6));
        g.setColor(darker());
        g.fill(new Ellipse2D.Double(8, 11.5, 2.6, 3.2));
        g.fill(new Ellipse2D.Double(13.4, 11.5, 2.6, 3.2));
        g.setColor(brighter());
        g.draw(new java.awt.geom.Line2D.Double(6.5, 8.5, 8.5, 6.5));
        g.draw(new java.awt.geom.Line2D.Double(17.5, 8.5, 15.5, 6.5));
    }

    private void telegram(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(2.5, 11.5);
        p.lineTo(20.5, 4.2);
        p.lineTo(18.6, 19.4);
        p.lineTo(12.4, 15.4);
        p.lineTo(9.4, 18.4);
        p.lineTo(8.6, 13.6);
        p.closePath();
        g.draw(p);
        line(g, 8.6, 13.6, 16.2, 8.4);
    }

    private void user(Graphics2D g) {
        g.draw(new Ellipse2D.Double(8, 4, 8, 8));
        g.draw(new java.awt.geom.Arc2D.Double(3.5, 13, 17, 14, 180, 180, java.awt.geom.Arc2D.OPEN));
    }

    private void play(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(7, 5);
        p.lineTo(19, 12);
        p.lineTo(7, 19);
        p.closePath();
        g.fill(p);
    }

    /** Кирка, наклонённая влево примерно на 45 градусов. */
    private void pickaxe(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(Math.toRadians(-45), 12, 12);
        GeneralPath head = new GeneralPath();
        head.moveTo(3.2, 6.6);
        head.lineTo(20.8, 6.6);
        head.lineTo(20.8, 9.6);
        head.lineTo(14.4, 9.6);
        head.lineTo(14.4, 11.6);
        head.lineTo(9.6, 11.6);
        head.lineTo(9.6, 9.6);
        head.lineTo(3.2, 9.6);
        head.closePath();
        g2.fill(head);
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        line(g2, 12, 10.4, 12, 21.2);
        g2.dispose();
    }

    private void skin(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(6, 3.2, 12, 11.5, 2.5, 2.5));
        g.fill(new Rectangle2D.Double(8.3, 7.1, 2.1, 2.2));
        g.fill(new Rectangle2D.Double(13.5, 7.1, 2.1, 2.2));
        g.draw(new RoundRectangle2D.Double(7.2, 15.6, 9.6, 5.2, 2, 2));
    }

    private void download(Graphics2D g) {
        line(g, 12, 4, 12, 15);
        g.draw(new java.awt.geom.Path2D.Double(java.awt.geom.PathIterator.WIND_NON_ZERO, 6));
        GeneralPath a = new GeneralPath();
        a.moveTo(7.5, 11);
        a.lineTo(12, 15.6);
        a.lineTo(16.5, 11);
        g.draw(a);
        line(g, 5, 19.5, 19, 19.5);
    }

    private void refresh(Graphics2D g) {
        g.draw(new java.awt.geom.Arc2D.Double(5, 5, 14, 14, 40, 280, java.awt.geom.Arc2D.OPEN));
        GeneralPath head = new GeneralPath();
        head.moveTo(16.6, 3.6);
        head.lineTo(19.4, 6.6);
        head.lineTo(15.6, 8.4);
        head.closePath();
        g.fill(head);
    }

    private void folder(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(3.5, 7);
        p.lineTo(9.5, 7);
        p.lineTo(11.5, 9.2);
        p.lineTo(20.5, 9.2);
        p.lineTo(20.5, 18.5);
        p.lineTo(3.5, 18.5);
        p.closePath();
        g.draw(p);
    }

    private void cube(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(12, 3.4);
        p.lineTo(20, 7.8);
        p.lineTo(20, 16.2);
        p.lineTo(12, 20.6);
        p.lineTo(4, 16.2);
        p.lineTo(4, 7.8);
        p.closePath();
        g.draw(p);
        line(g, 4, 7.8, 12, 12.2);
        line(g, 20, 7.8, 12, 12.2);
        line(g, 12, 12.2, 12, 20.6);
    }

    private void exit(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(4, 4.5, 11, 15));
        line(g, 10, 12, 20.5, 12);
        GeneralPath a = new GeneralPath();
        a.moveTo(17, 8.6);
        a.lineTo(20.6, 12);
        a.lineTo(17, 15.4);
        g.draw(a);
    }

    private void check(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(4.5, 12.6);
        p.lineTo(9.6, 17.6);
        p.lineTo(19.5, 6.4);
        g.draw(p);
    }

    private void cross(Graphics2D g) {
        line(g, 6, 6, 18, 18);
        line(g, 18, 6, 6, 18);
    }

    private void warn(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(12, 3.6);
        p.lineTo(21, 19.4);
        p.lineTo(3, 19.4);
        p.closePath();
        g.draw(p);
        line(g, 12, 9.6, 12, 14.2);
        g.fill(new Ellipse2D.Double(11, 16.2, 2, 2));
    }

    private void info(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        line(g, 12, 11, 12, 16.4);
        g.fill(new Ellipse2D.Double(11, 7, 2, 2));
    }

    private void search(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4.5, 4.5, 10, 10));
        line(g, 14, 14, 19.5, 19.5);
    }

    private void news(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(3.5, 5.5, 17, 13));
        line(g, 6.5, 9, 13, 9);
        line(g, 6.5, 12, 16, 12);
        line(g, 6.5, 15, 16, 15);
    }

    private void clock(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        line(g, 12, 7.5, 12, 12.4);
        line(g, 12, 12.4, 15.6, 14.4);
    }

    private void shield(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(12, 3.4);
        p.lineTo(19.5, 6.4);
        p.lineTo(19.5, 12.6);
        p.quadTo(19.5, 18.4, 12, 20.8);
        p.quadTo(4.5, 18.4, 4.5, 12.6);
        p.lineTo(4.5, 6.4);
        p.closePath();
        g.draw(p);
        GeneralPath c = new GeneralPath();
        c.moveTo(8.6, 12.2);
        c.lineTo(11.2, 14.8);
        c.lineTo(15.6, 9.4);
        g.draw(c);
    }

    private void cup(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(5.5, 8, 11, 11));
        line(g, 16.5, 10.5, 19.5, 10.5);
        line(g, 19.5, 10.5, 19.5, 15);
        line(g, 19.5, 15, 16.5, 15);
        GeneralPath s = new GeneralPath();
        s.moveTo(8.5, 6.4);
        s.quadTo(10, 4.6, 8.5, 3);
        s.moveTo(12.5, 6.4);
        s.quadTo(14, 4.6, 12.5, 3);
        g.draw(s);
    }

    private void chip(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(6.5, 6.5, 11, 11));
        g.draw(new java.awt.geom.Rectangle2D.Double(10, 10, 4, 4));
        for (int i = 0; i < 3; i++) {
            double p = 8.5 + i * 3.5;
            line(g, p, 6.5, p, 3.6);
            line(g, p, 17.5, p, 20.4);
            line(g, 6.5, p, 3.6, p);
            line(g, 17.5, p, 20.4, p);
        }
    }

    private void signal(Graphics2D g) {
        for (int i = 0; i < 4; i++) {
            double h = 3.5 + i * 3.6;
            g.draw(new java.awt.geom.Rectangle2D.Double(4 + i * 4.2, 19.5 - h, 2.6, h));
        }
    }

    private void copy(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(8.5, 8.5, 11, 11));
        GeneralPath p = new GeneralPath();
        p.moveTo(15.5, 5.5);
        p.lineTo(5.5, 5.5);
        p.lineTo(5.5, 15.5);
        g.draw(p);
    }

    private void trash(Graphics2D g) {
        g.draw(new java.awt.geom.Path2D.Double());
        line(g, 4.5, 7, 19.5, 7);
        g.draw(new java.awt.geom.Path2D.Double());
        GeneralPath p = new GeneralPath();
        p.moveTo(6.5, 7);
        p.lineTo(7.6, 19.5);
        p.lineTo(16.4, 19.5);
        p.lineTo(17.5, 7);
        g.draw(p);
        line(g, 9.5, 4.5, 14.5, 4.5);
        line(g, 9.5, 4.5, 9.5, 7);
        line(g, 14.5, 4.5, 14.5, 7);
    }

    private void plus(Graphics2D g) {
        line(g, 12, 5, 12, 19);
        line(g, 5, 12, 19, 12);
    }

    private void pencil(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(4.5, 19.5);
        p.lineTo(6.2, 15.4);
        p.lineTo(15.6, 6);
        p.lineTo(19.6, 4.4);
        p.lineTo(18, 8.4);
        p.lineTo(8.6, 17.8);
        p.closePath();
        g.draw(p);
    }

    private void square(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(5.5, 5.5, 13, 13));
    }

    private void chevron(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(9, 5.5);
        p.lineTo(15.5, 12);
        p.lineTo(9, 18.5);
        g.draw(p);
    }

    private void image(Graphics2D g) {
        g.draw(new java.awt.geom.Rectangle2D.Double(3.5, 5.5, 17, 13));
        g.draw(new Ellipse2D.Double(6.5, 8.5, 3, 3));
        GeneralPath p = new GeneralPath();
        p.moveTo(4.5, 17.5);
        p.lineTo(9.5, 12.5);
        p.lineTo(13.5, 16);
        p.lineTo(16.5, 12);
        p.lineTo(19.5, 16);
        g.draw(p);
    }

    private Color darker() {
        return new Color(0x161b22);
    }

    private Color brighter() {
        return new Color(0xe6edf3);
    }
}
