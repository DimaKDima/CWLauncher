package ru.cw.launcher.background;

import ru.cw.launcher.ui.Theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Программно рисуемый фон CWLauncher (fallback, раздел 29 ТЗ).
 * Никаких файлов: графитовая основа, мягкие радиальные градиенты, синие световые
 * области, геометрические линии, лёгкие частицы, затемнение под UI.
 * Композиция пересчитывается при изменении размера; тяжёлые слои кешируются.
 */
public final class BackgroundRenderer {

    /** Кеш статичных слоёв — перерисовывается только при смене размера. */
    private java.awt.image.BufferedImage cached;
    private int cachedW = -1;
    private int cachedH = -1;
    private boolean light;

    public void setLightTheme(boolean light) {
        if (light != this.light) {
            this.light = light;
            cached = null;
        }
    }

    public boolean isLightTheme() {
        return light;
    }

    /** Полная перерисовка (например, при смене темы). */
    public void invalidate() {
        cached = null;
    }

    /**
     * Отрисовка фона.
     *
     * @param animate смещение «дыхания» световых пятен (0..1), чтобы оживить фон
     *                без нагрузки — анимируется только верхний слой
     */
    public void paint(Component c, Graphics g, int width, int height, double animate) {
        if (width <= 0 || height <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (cached == null || cachedW != width || cachedH != height) {
            cached = renderStatic(width, height);
            cachedW = width;
            cachedH = height;
        }
        g2.drawImage(cached, 0, 0, null);
        // «дыхание» центрального свечения — дешёвая операция поверх кеша
        float pulse = (float) (0.5 + 0.5 * Math.sin(animate * Math.PI * 2));
        float cx = width * 0.5f;
        float cy = height * 0.42f;
        float r = Math.max(width, height) * (0.30f + 0.03f * pulse);
        Color glow = light ? new Color(0x3d7fd6) : new Color(0x1f6feb);
        g2.setPaint(new RadialGradientPaint(new Rectangle2D.Float(cx - r, cy - r, r * 2, r * 2),
                new float[]{0f, 0.55f, 1f},
                new Color[]{withAlpha(glow, (int) (26 + 12 * pulse)), withAlpha(glow, 12),
                        withAlpha(glow, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g2.dispose();
    }

    private java.awt.image.BufferedImage renderStatic(int w, int h) {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(Math.max(1, w), Math.max(1, h),
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 1. база
        Color top = light ? new Color(0xe9eef5) : new Color(0x11151c);
        Color bottom = light ? new Color(0xcdd8e6) : new Color(0x05070b);
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, w, h);

        // 2. большие мягкие радиальные градиенты
        blob(g, w * 0.14f, h * 0.18f, Math.max(w, h) * 0.42f, light ? new Color(0x8ab6f0) : new Color(0x14335f));
        blob(g, w * 0.88f, h * 0.10f, Math.max(w, h) * 0.34f, light ? new Color(0xa8c9f5) : new Color(0x0f2a4d));
        blob(g, w * 0.78f, h * 0.92f, Math.max(w, h) * 0.40f, light ? new Color(0x9dc0ea) : new Color(0x102b47));
        blob(g, w * 0.30f, h * 0.95f, Math.max(w, h) * 0.30f, light ? new Color(0xbcd3ef) : new Color(0x0b1f38));
        blob(g, w * 0.50f, h * 0.40f, Math.max(w, h) * 0.26f, light ? new Color(0x7fb0ec) : new Color(0x1c4f8f));

        // 3. геометрические линии (диагональная сетка)
        g.setStroke(new BasicStroke(1f));
        g.setColor(light ? new Color(20, 40, 70, 16) : new Color(120, 175, 255, 14));
        double step = Math.max(64, Math.min(w, h) / 11.0);
        GeneralPath grid = new GeneralPath();
        for (double x = -h; x < w + h; x += step) {
            grid.moveTo(x, 0);
            grid.lineTo(x + h * 0.55, h);
        }
        for (double y = step; y < h; y += step * 1.7) {
            grid.moveTo(0, y);
            grid.lineTo(w, y - w * 0.12);
        }
        g.draw(grid);

        // 4. тонкие светящиеся дуги
        for (int i = 0; i < 3; i++) {
            float rr = Math.max(w, h) * (0.34f + i * 0.11f);
            float alpha = 26 - i * 7;
            g.setColor(light ? new Color(40, 90, 160, alpha) : new Color(90, 165, 255, alpha));
            g.setStroke(new BasicStroke(1.4f - i * 0.3f));
            Shape arc = new java.awt.geom.Arc2D.Float(w * 0.5f - rr, h * 0.44f - rr, rr * 2, rr * 2,
                    200 + i * 6, 120 + i * 10, java.awt.geom.Arc2D.OPEN);
            g.draw(arc);
        }

        // 5. «блоки» в духе Minecraft — очень деликатно, по краям
        drawBlocks(g, w, h);

        // 6. лёгкие частицы
        long seed = 20260928L;
        java.util.Random rnd = new java.util.Random(seed);
        int particles = Math.max(26, (w * h) / 26000);
        for (int i = 0; i < particles; i++) {
            float x = rnd.nextFloat() * w;
            float y = rnd.nextFloat() * h;
            float s = 0.6f + rnd.nextFloat() * 1.9f;
            int a = 18 + rnd.nextInt(60);
            g.setColor(light ? new Color(30, 70, 130, a / 2) : new Color(150, 200, 255, a));
            g.fill(new Ellipse2D.Float(x, y, s, s));
        }

        // 7. виньетка + затемнение под центральную область UI
        float vr = Math.max(w, h) * 0.78f;
        g.setPaint(new RadialGradientPaint(new Rectangle2D.Float(w * 0.5f - vr, h * 0.5f - vr, vr * 2, vr * 2),
                new float[]{0.35f, 1f},
                new Color[]{new Color(0, 0, 0, 0), light ? new Color(0, 0, 0, 40) : new Color(0, 0, 0, 150)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, w, h);

        // 8. спокойная центральная область: мягкая тёмная «подушка» под элементы
        float pw = Math.min(w * 0.62f, 760);
        float ph = Math.min(h * 0.60f, 460);
        Shape pad = new RoundRectangle2D.Float(w * 0.5f - pw / 2, h * 0.5f - ph / 2, pw, ph, 40, 40);
        g.setColor(light ? new Color(255, 255, 255, 120) : new Color(8, 11, 16, 118));
        g.fill(pad);
        g.setColor(light ? new Color(70, 110, 170, 60) : new Color(90, 165, 255, 46));
        g.setStroke(new BasicStroke(1f));
        g.draw(pad);

        // 9. нижняя линия горизонта
        g.setPaint(new GradientPaint(0, h * 0.80f, new Color(0, 0, 0, 0), 0, h,
                light ? new Color(120, 150, 190, 70) : new Color(0, 0, 0, 130)));
        g.fill(new Rectangle2D.Float(0, h * 0.80f, w, h * 0.20f));
        g.dispose();
        return img;
    }

    private void blob(Graphics2D g, float cx, float cy, float r, Color color) {
        if (r <= 0) return;
        g.setPaint(new RadialGradientPaint(new Rectangle2D.Float(cx - r, cy - r, r * 2, r * 2),
                new float[]{0f, 0.6f, 1f},
                new Color[]{withAlpha(color, 150), withAlpha(color, 60), withAlpha(color, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    /** Изолированные «кубы» у краёв экрана — отсылка к Minecraft, без навязчивости. */
    private void drawBlocks(Graphics2D g, int w, int h) {
        long s = 4242L;
        java.util.Random rnd = new java.util.Random(s);
        int size = Math.max(16, Math.min(w, h) / 34);
        int count = 12;
        for (int i = 0; i < count; i++) {
            float x;
            float y;
            if (i % 2 == 0) {
                x = rnd.nextFloat() * w * 0.20f;
                y = rnd.nextFloat() * h;
            } else {
                x = w - rnd.nextFloat() * w * 0.20f - size;
                y = rnd.nextFloat() * h;
            }
            float sz = size * (0.5f + rnd.nextFloat());
            int alpha = 8 + rnd.nextInt(16);
            Color face = light ? new Color(40, 90, 160, alpha) : new Color(110, 180, 255, alpha);
            g.setColor(face);
            g.fill(new Rectangle2D.Float(x, y, sz, sz));
            g.setColor(light ? new Color(255, 255, 255, alpha + 12) : new Color(180, 220, 255, alpha + 14));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Float(x, y, sz, sz));
            // верхняя грань
            GeneralPath top = new GeneralPath();
            top.moveTo(x, y);
            top.lineTo(x + sz * 0.28f, y - sz * 0.22f);
            top.lineTo(x + sz * 1.28f, y - sz * 0.22f);
            top.lineTo(x + sz, y);
            top.closePath();
            g.setColor(light ? new Color(60, 110, 180, alpha) : new Color(150, 200, 255, alpha));
            g.fill(top);
        }
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** Цвет текста поверх фона (используется окнами). */
    public Color textColor() {
        return light ? new Color(0x0d1117) : Theme.TEXT;
    }

    /** Тонкая линия поверх фона. */
    public Color lineColor() {
        return light ? new Color(0x9db4cf) : new Color(0x2f6fed);
    }

    /** Отрисовка «поверх» для окон, которым нужен собственный оверлей. */
    public void paintOverlay(Graphics g, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        Path2D p = new Path2D.Double();
        p.append(new Rectangle2D.Float(0, 0, w, h * 0.34f), false);
        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, light ? 20 : 70), 0, h * 0.34f,
                new Color(0, 0, 0, 0)));
        g2.fill(p);
        g2.dispose();
    }
}
