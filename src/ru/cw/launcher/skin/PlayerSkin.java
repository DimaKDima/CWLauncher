package ru.cw.launcher.skin;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Скин конкретного аккаунта: голова для профиля и файл для игры. */
public final class PlayerSkin {

    private static volatile BufferedImage face;
    private static volatile Path file;
    private static long stamp = -1;

    private PlayerSkin() {
    }

    public static Path directory(Account account) {
        return account == null ? null : Paths.accountSkinDir(account.id);
    }

    public static BufferedImage face() {
        return face;
    }

    public static Path file() {
        return file;
    }

    /** Голова скина этого аккаунта. Без файла — null, и тогда рисуется значок профиля. */
    public static BufferedImage headOfAccount(Account account) {
        if (account == null) return null;
        Path newest = newest(Paths.accountSkinDir(account.id));
        return newest == null ? null : headOf(newest);
    }

    /** Кладёт картинку скином этого аккаунта. Старые файлы в его папке убираются. */
    public static void assign(Account account, Path source) throws IOException {
        if (account == null || source == null) throw new IOException("Нет аккаунта или файла");
        Path dir = Paths.accountSkinDir(account.id);
        Files.createDirectories(dir);
        try (Stream<Path> list = Files.list(dir)) {
            for (Path old : list.filter(Files::isRegularFile).toList()) Files.deleteIfExists(old);
        }
        String n = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = n.endsWith(".jpg") || n.endsWith(".jpeg") ? ".jpg" : ".png";
        Files.copy(source, dir.resolve("skin" + ext), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Перечитывает папку скина аккаунта. Возвращает true, если голова изменилась. */
    public static boolean refresh(Account account) {
        if (account != null) adoptLegacy(account);
        Path newest = account == null ? null : newest(Paths.accountSkinDir(account.id));
        long next = stampOf(newest);
        if (next == stamp && (account != null || face == null)) return false;
        stamp = next;
        file = newest;
        face = newest == null ? null : headOf(newest);
        if (newest != null) Log.info("Скин " + account.username + ": " + newest.getFileName());
        return true;
    }

    /** Старый общий файл из папки игры один раз переносится активному аккаунту. */
    private static void adoptLegacy(Account account) {
        Path marker = Paths.root().resolve("skins").resolve(".legacy-adopted");
        if (Files.exists(marker)) return;
        Path own = Paths.accountSkinDir(account.id);
        if (newest(own) != null) {
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, account.id);
            } catch (IOException ignored) {
            }
            return;
        }
        Path legacy = newest(Paths.gameDir().resolve("skin"));
        if (legacy == null) return;
        try {
            Files.createDirectories(own);
            Files.copy(legacy, own.resolve(legacy.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, account.id);
            Log.info("Скин из общей папки закреплён за аккаунтом " + account.username);
        } catch (IOException e) {
            Log.warn("Скин не перенесён: " + Log.reason(e));
        }
    }

    private static long stampOf(Path path) {
        if (path == null) return 0;
        try {
            return Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
        } catch (IOException e) {
            return -2;
        }
    }

    private static Path newest(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isRegularFile)
                    .filter(PlayerSkin::image)
                    .max(Comparator.comparingLong(PlayerSkin::modified))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean image(Path path) {
        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** PNG 64×32 или 64×64, который Minecraft принимает без модов. */
    public static byte[] gamePng(Path path) {
        BufferedImage img = read(path);
        if (img == null) return null;
        BufferedImage skin = minecraftSize(img);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(skin, "png", out)) return null;
            return out.toByteArray();
        } catch (IOException e) {
            Log.warn("Скин не подготовлен для игры: " + Log.reason(e));
            return null;
        }
    }

    /** Узкие руки, если в файле модель Alex. */
    public static boolean slim(Path path) {
        BufferedImage img = read(path);
        if (img == null || img.getWidth() < 64 || img.getHeight() < 32) return false;
        int scale = Math.max(1, img.getWidth() / 64);
        int x = 54 * scale;
        int y = 20 * scale;
        if (x >= img.getWidth() || y >= img.getHeight()) return false;
        return (img.getRGB(x, y) >>> 24) == 0;
    }

    private static BufferedImage read(Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage minecraftSize(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w < 64 || w % 64 != 0 || h < 32) return img;
        int scale = w / 64;
        int targetH = h >= w ? 64 : 32;
        if (scale == 1 && (h == 32 || h == 64)) return img;
        BufferedImage out = new BufferedImage(64, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int srcH = Math.min(h, targetH * scale);
        g.drawImage(img, 0, 0, 64, targetH, 0, 0, w, Math.max(1, srcH), null);
        g.dispose();
        return out;
    }

    private static BufferedImage headOf(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) return null;
            int scale = img.getWidth() / 64;
            if (scale >= 1 && img.getWidth() == scale * 64 && img.getHeight() >= scale * 32) {
                BufferedImage face = new BufferedImage(8 * scale, 8 * scale, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = face.createGraphics();
                g.drawImage(img, 0, 0, 8 * scale, 8 * scale, 8 * scale, 8 * scale, 16 * scale, 16 * scale, null);
                if (img.getHeight() >= scale * 64) {
                    g.drawImage(img, 0, 0, 8 * scale, 8 * scale, 40 * scale, 8 * scale, 48 * scale, 16 * scale, null);
                }
                g.dispose();
                return face;
            }
            return img;
        } catch (IOException e) {
            Log.warn("Скин не прочитан: " + Log.reason(e));
            return null;
        }
    }

    public static void paint(Graphics2D g, BufferedImage face, int x, int y, int size) {
        if (face == null || size <= 0) return;
        Graphics2D cg = (Graphics2D) g.create();
        cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        cg.drawImage(face, x, y, size, size, null);
        cg.dispose();
    }
}
