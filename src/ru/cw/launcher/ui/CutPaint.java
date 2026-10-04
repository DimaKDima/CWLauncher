package ru.cw.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.GeneralPath;

/**
 * Форма кнопок CWLauncher: срез слева сверху и справа снизу.
 * Углы вне среза не закрашиваются, чтобы под ними был фон, а не чёрный прямоугольник.
 */
public final class CutPaint {

    private CutPaint() {
    }

    public static int cutFor(int w, int h) {
        return Math.max(8, Math.min(16, Math.min(w, h) / 4));
    }

    public static Shape shape(int w, int h, int cut) {
        int c = Math.max(6, Math.min(cut, Math.min(w, h) / 2 - 1));
        GeneralPath p = new GeneralPath();
        p.moveTo(c, 0);
        p.lineTo(w, 0);
        p.lineTo(w, h - c);
        p.lineTo(Math.max(c, w - c), h);
        p.lineTo(0, h);
        p.lineTo(0, c);
        p.closePath();
        return p;
    }

    public static void button(Graphics2D g2, int w, int h, Color fill, Color line) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape s = shape(Math.max(1, w - 1), Math.max(1, h - 1), cutFor(w, h));
        g2.setColor(fill);
        g2.fill(s);
        g2.setColor(line);
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(s);
    }
}
