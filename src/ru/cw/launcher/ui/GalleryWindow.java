package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Необязательная галерея. Без настроенного источника и при сетевой ошибке лаунчер не ломается.
 * Качаются только HTTPS-картинки, не больше 8 штук и 3 МБ каждая.
 */
public final class GalleryWindow {

    private static final int MAX_IMAGES = 8;
    private static final long MAX_BYTES = 3L * 1024 * 1024;

    private GalleryWindow() {
    }

    public static void show(Window owner, LauncherApp app) {
        JDialog d = new JDialog(owner, "Галерея Common World", Dialog.ModalityType.MODELESS);
        d.setSize(720, 520);
        d.setLocationRelativeTo(owner);
        d.getContentPane().setBackground(Theme.BG);
        d.setLayout(new BorderLayout());
        JLabel caption = new JLabel("Загрузка…", SwingConstants.CENTER);
        caption.setForeground(Theme.TEXT);
        JLabel picture = new JLabel("", SwingConstants.CENTER);
        picture.setForeground(Theme.MUTED);
        d.add(picture, BorderLayout.CENTER);
        d.add(caption, BorderLayout.NORTH);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        nav.setOpaque(false);
        CutButton prev = new CutButton("Назад", CutButton.Accent.NEUTRAL, false);
        CutButton next = new CutButton("Далее", CutButton.Accent.NEUTRAL, false);
        CutButton close = new CutButton("Закрыть", CutButton.Accent.GHOST, false);
        nav.add(prev);
        nav.add(next);
        nav.add(close);
        d.add(nav, BorderLayout.SOUTH);
        close.addActionListener(e -> d.dispose());
        WindowBackground.dim(d);
        d.setVisible(true);

        String url = app.cfg.galleryUrl;
        if (Utils.isBlank(url)) {
            caption.setText("Галерея не настроена. Укажите адрес JSON в настройках.");
            picture.setText("Источник galleryUrl пуст");
            prev.setEnabled(false);
            next.setEnabled(false);
            return;
        }
        app.io().submit(() -> {
            List<Item> items = load(url);
            SwingUtilities.invokeLater(() -> {
                if (items.isEmpty()) {
                    caption.setText("Галерея недоступна. Лаунчер продолжает работать.");
                    picture.setText("Нет изображений в кэше и источник не ответил");
                    prev.setEnabled(false);
                    next.setEnabled(false);
                    return;
                }
                int[] index = {0};
                Runnable paint = () -> {
                    Item it = items.get(index[0]);
                    caption.setText((index[0] + 1) + " / " + items.size() + "   " + it.title);
                    if (it.image != null) {
                        Image scaled = it.image.getScaledInstance(640, 400, Image.SCALE_SMOOTH);
                        picture.setIcon(new ImageIcon(scaled));
                        picture.setText("");
                    } else {
                        picture.setIcon(null);
                        picture.setText(it.title);
                    }
                };
                prev.addActionListener(e -> {
                    index[0] = (index[0] + items.size() - 1) % items.size();
                    paint.run();
                });
                next.addActionListener(e -> {
                    index[0] = (index[0] + 1) % items.size();
                    paint.run();
                });
                paint.run();
            });
        });
    }

    private record Item(String title, BufferedImage image) {
    }

    @SuppressWarnings("unchecked")
    private static List<Item> load(String url) {
        List<Item> out = new ArrayList<>();
        Path cache = Paths.cache().resolve("gallery.json");
        String body = null;
        try {
            body = Http.get(url, "gallery.json").body();
            if (body != null) {
                Utils.ensureDirectories(cache.getParent());
                Utils.writeString(cache, body);
            }
        } catch (Exception e) {
            Log.warn("Галерея недоступна: " + Log.reason(e));
            try {
                if (Files.exists(cache)) body = Utils.readString(cache);
            } catch (Exception ignored) {
            }
        }
        if (body == null || body.isBlank()) return out;
        try {
            Object root = Json.parse(body);
            List<Object> arr = null;
            if (root instanceof List<?> l) arr = new ArrayList<>(l);
            else if (root instanceof Map<?, ?> m) arr = Json.arr((Map<String, Object>) m, "images");
            if (arr == null) return out;
            int n = 0;
            for (Object o : arr) {
                if (n >= MAX_IMAGES || !(o instanceof Map<?, ?> raw)) break;
                Map<String, Object> m = (Map<String, Object>) raw;
                String title = Json.str(m, "title", "Изображение");
                String link = Json.str(m, "url", Json.str(m, "image", ""));
                BufferedImage img = fetch(link, n);
                if (img != null) {
                    out.add(new Item(title, img));
                    n++;
                }
            }
        } catch (Exception e) {
            Log.warn("Галерея: не удалось разобрать источник: " + Log.reason(e));
        }
        return out;
    }

    private static BufferedImage fetch(String link, int index) {
        if (link == null || !link.toLowerCase().startsWith("https://")) return null;
        Path file = Paths.cache().resolve("gallery").resolve("img-" + index + ".bin");
        try {
            boolean fresh = Files.isRegularFile(file) && Files.size(file) >= 32 && Files.size(file) <= MAX_BYTES;
            if (!fresh) {
                Utils.ensureDirectories(file.getParent());
                Http.download(link, file, null, 0, null, null);
            }
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) {
                Utils.deleteQuietly(file);
                return null;
            }
            return ImageIO.read(file.toFile());
        } catch (Exception e) {
            Log.warn("Галерея: изображение пропущено: " + Log.reason(e));
            Utils.deleteQuietly(file);
            return null;
        }
    }
}
