package ru.cw.launcher.background;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * BackgroundManager: выбор источника фона (раздел 5/30/80 ТЗ).
 *
 * Приоритет:
 *  1) пользовательский файл (если указан и читается);
 *  2) assets/background/default_background.png (рядом с EXE/JAR и внутри JAR);
 *  3) программно отрисованный BackgroundRenderer.
 *
 * Ошибка загрузки изображения никогда не приводит к crash — всегда fallback.
 */
public final class BackgroundManager {

    public enum Source {
        USER_FILE("пользовательское изображение"),
        MAIN_FILE("assets/background/main.png"),
        DEFAULT_FILE("assets/background/default_background.png"),
        RENDERER("программно отрисованный фон");
        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    private final BackgroundRenderer renderer = new BackgroundRenderer();
    private volatile BufferedImage image;
    private volatile Source source = Source.RENDERER;
    private volatile String statusMessage = "";
    private volatile String usedPath = "";
    private Settings settings;

    public BackgroundRenderer renderer() {
        return renderer;
    }

    public Source source() {
        return source;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public String usedPath() {
        return usedPath;
    }

    public boolean isRendererFallback() {
        return source == Source.RENDERER;
    }

    public BufferedImage picture() {
        return image;
    }

    /** Пересчёт источника по настройкам. Вызывается при старте и после смены фона. */
    public synchronized void reload(Settings settings) {
        this.settings = settings;
        renderer.setLightTheme(settings != null && settings.isLightTheme());
        statusMessage = "";
        usedPath = "";
        try {
        // 1. пользовательский файл
        String user = settings == null ? "" : settings.backgroundPath;
        if (settings != null && settings.useCustomBackground && !Utils.isBlank(user)) {
            Path p = Path.of(user);
            if (!Files.isRegularFile(p)) {
                image = null;
                source = Source.RENDERER;
                statusMessage = "Выбранный файл фона не найден. Используется встроенный программно отрисованный фон.";
                Log.warn("Фон: файл не найден — " + p);
                return;
            }
            BufferedImage img = tryRead(p);
            if (img != null) {
                image = img;
                source = Source.USER_FILE;
                usedPath = p.toString();
                Log.info("Фон: пользовательское изображение " + p);
                return;
            }
            image = null;
            source = Source.RENDERER;
            statusMessage = "Не удалось загрузить выбранное изображение (повреждён или неподдерживаемый "
                    + "формат). Используется стандартный фон.";
            Log.warn("Фон: не удалось загрузить " + p);
            return;
        }
        Path main = Paths.mainBackground();
        try {
            if (main.getParent() != null) Files.createDirectories(main.getParent());
        } catch (Exception e) {
            Log.warn("Фон: не удалось подготовить папку main.png: " + Log.reason(e));
        }
        if (Files.isRegularFile(main)) {
            BufferedImage img = tryRead(main);
            if (img != null) {
                image = img;
                source = Source.MAIN_FILE;
                usedPath = main.toString();
                statusMessage = "Используется main.png";
                Log.info("Фон: " + main);
                return;
            }
            statusMessage = "Файл main.png не читается — используется программный фон.";
            Log.warn("Фон: не удалось прочитать " + main);
        }
        // 2. default_background.png
        Path def = Paths.bundledDefaultBackground();
        if (def != null) {
            BufferedImage img = tryRead(def);
            if (img != null) {
                image = img;
                source = Source.DEFAULT_FILE;
                usedPath = def.toString();
                statusMessage = "Используется стандартное изображение.";
                Log.info("Фон: " + def);
                return;
            }
            statusMessage = "Стандартное изображение не читается — используется программный фон.";
        } else if (readBundledResource() != null) {
            image = readBundledResource();
            source = Source.DEFAULT_FILE;
            usedPath = "assets/background/default_background.png (внутри приложения)";
            statusMessage = "Используется встроенное стандартное изображение.";
            return;
        }
        // 3. программный fallback
        image = null;
        source = Source.RENDERER;
        if (statusMessage.isEmpty()) {
            statusMessage = "Файл assets/background/default_background.png не найден — "
                    + "фон рисуется программно.";
        }
        Log.info("Фон: программный fallback (BackgroundRenderer)");
        } finally {
            sharedImage = image;
            int stored = settings == null ? 48 : settings.backgroundDim;
            setDimPercent(stored * 100 / 80);
        }
    }

    /** 0–100. На окнах, кроме главного меню, это заметное затемнение, не лёгкая вуаль. */
    public static void setDimPercent(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        sharedDim = p * 220 / 100;
    }

    private static volatile BufferedImage sharedImage;
    private static volatile int sharedDim = 48;

    /** Фон для окон, кроме главного меню: картинка и выбранное затемнение. */
    public static void paintShared(Graphics g, int w, int h, boolean dim) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0x0B1220));
        g2.fillRect(0, 0, w, h);
        BufferedImage img = sharedImage;
        if (dim && img != null && ru.cw.launcher.ui.UiLook.blur()) img = soften(img);
        if (img != null && w > 0 && h > 0) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
            int dw = (int) Math.ceil(img.getWidth() * scale);
            int dh = (int) Math.ceil(img.getHeight() * scale);
            g2.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            if (dim && sharedDim > 0) {
                g2.setColor(new Color(0, 0, 0, sharedDim));
                g2.fillRect(0, 0, w, h);
            }
        }
        g2.dispose();
    }

    private static BufferedImage softImage;
    private static BufferedImage softSource;

    /** Сильное уменьшение и растягивание обратно — размытие фона в окнах настроек. */
    private static BufferedImage soften(BufferedImage src) {
        if (src == softSource && softImage != null) return softImage;
        int w = Math.max(8, src.getWidth() / 10);
        int h = Math.max(8, src.getHeight() / 10);
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        softImage = small;
        softSource = src;
        return small;
    }

    private BufferedImage readBundledResource() {
        try (var in = BackgroundManager.class.getResourceAsStream("/assets/background/default_background.png")) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage tryRead(Path p) {
        try {
            File f = p.toFile();
            if (!f.canRead() || f.length() < 32) return null;
            BufferedImage img = ImageIO.read(f);
            if (img == null) return null;
            if (img.getWidth() < 64 || img.getHeight() < 64) return null;
            return img;
        } catch (Exception e) {
            Log.warn("Background: ошибка чтения " + p + ": " + Log.reason(e));
            return null;
        }
    }

    /** Обычный проводник Windows. Свой список файлов внутри лаунчера не показывается. */
    public static String chooseImage(Component parent) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        Frame frame = owner instanceof Frame f ? f : new Frame();
        FileDialog dialog = new FileDialog(frame, "Выберите изображение", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String n = name.toLowerCase(java.util.Locale.ROOT);
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                    || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp");
        });
        dialog.setFile("*.png;*.jpg;*.jpeg;*.webp;*.bmp");
        dialog.setVisible(true);
        if (dialog.getFile() == null || dialog.getDirectory() == null) return null;
        return new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
    }

    /** Итоговое состояние для настроек/диагностики. */
    public String describe() {
        return source.label + (usedPath.isBlank() ? "" : " · " + Utils.shorten(usedPath, 70));
    }

    public Settings settings() {
        return settings;
    }

    /** JPanel, рисующий фон: картинка (aspect-fill) или BackgroundRenderer. */
    public final class View extends JPanel {
        private Timer ticker;
        private double phase;

        public View() {
            setOpaque(true);
            setBackground(new Color(0x0B1220));
            setLayout(null);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            setOpaque(true);
            setBackground(new Color(0x0B1220));
        }

        /** Плавное «дыхание» световых пятен (только для программного фона). */
        public void startAnimation() {
            if (ticker != null) return;
            ticker = new Timer(70, e -> {
                if (source != Source.RENDERER || !isShowing()) return;
                phase += 0.0035;
                if (phase > 1) phase -= 1;
                repaint(0, 0, getWidth(), getHeight());
            });
            ticker.setCoalesce(true);
            ticker.start();
        }

        public void stopAnimation() {
            if (ticker != null) {
                ticker.stop();
                ticker = null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.Src);
            g2.setColor(new Color(0x0B1220));
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.SrcOver);
            BufferedImage img = image;
            if (img != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                // aspect-fill (cover) с центрированием
                double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
                int dw = (int) Math.ceil(img.getWidth() * scale);
                int dh = (int) Math.ceil(img.getHeight() * scale);
                g2.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                g2.dispose();
            } else {
                g2.dispose();
                renderer.paint(this, g, w, h, phase);
            }
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
        }
    }
}
