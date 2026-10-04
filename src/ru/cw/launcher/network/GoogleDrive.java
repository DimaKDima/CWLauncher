package ru.cw.launcher.network;

import ru.cw.launcher.net.Http;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Корректное получение файлов из Google Drive: Drive часто отдаёт HTML-страницу
 * (предупреждение о размере файла), поэтому используем прямые ссылки usercontent
 * и проверяем содержимое. HTML-страницу успешной загрузкой считать нельзя.
 */
public final class GoogleDrive {

    private static final Pattern ID = Pattern.compile("[-_A-Za-z0-9]{10,60}");
    private static final Pattern ID_IN_URL =
            Pattern.compile("(?:/d/|id=)([-_A-Za-z0-9]{10,60})");

    private GoogleDrive() {
    }

    /** Извлекает file id из ссылки вида .../file/d/<ID>/view или ?id=<ID>. */
    public static String fileId(String linkOrId) {
        if (linkOrId == null) return null;
        String s = linkOrId.trim();
        if (s.isEmpty()) return null;
        Matcher m = ID_IN_URL.matcher(s);
        if (m.find()) return m.group(1);
        if (ID.matcher(s).matches()) return s;
        return null;
    }

    /** Набор прямых ссылок, которые стоит попробовать по порядку. */
    public static String[] downloadUrls(String fileId) {
        return new String[]{
                "https://drive.usercontent.google.com/download?id=" + fileId
                        + "&export=download&confirm=t",
                "https://drive.google.com/uc?export=download&id=" + fileId + "&confirm=t",
                "https://drive.google.com/uc?export=download&id=" + fileId,
        };
    }

    /**
     * Скачивание файла из Drive с проверкой, что это действительно нужный тип содержимого.
     *
     * @param validator проверка скачанного файла (например «это ZIP»)
     */
    public static void download(String linkOrId, Path dest, Validator validator,
                                Http.Progress progress, Http.Cancel cancel)
            throws IOException, InterruptedException {
        String id = fileId(linkOrId);
        if (id == null) throw new IOException("Не удалось определить идентификатор файла Google Drive");
        Utils.ensureDirectories(dest.getParent());
        IOException last = null;
        for (String url : downloadUrls(id)) {
            Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
            try {
                Log.debug("Google Drive: пробую " + Utils.shorten(url, 80));
                Http.download(url, tmp, null, 0, progress, cancel);
                byte[] head = headOf(tmp);
                if (isHtml(head)) {
                    Utils.deleteQuietly(tmp);
                    throw new IOException("Google Drive вернул HTML-страницу вместо файла");
                }
                if (validator != null && !validator.valid(tmp)) {
                    Utils.deleteQuietly(tmp);
                    throw new IOException("Скачанный файл не является ожидаемым содержимым (не ZIP)");
                }
                Utils.replaceFile(tmp, dest);
                Log.info("Google Drive: файл получен (" + Utils.humanSize(Utils.size(dest)) + ")");
                return;
            } catch (IOException e) {
                last = e;
                Utils.deleteQuietly(tmp);
                Log.warn("Google Drive: " + Log.reason(e));
            }
        }
        throw last != null ? last : new IOException("Не удалось скачать файл из Google Drive");
    }

    /** Текстовый файл с Диска (News.txt, lVersion.txt). HTML-страница не считается текстом. */
    public static String readText(String linkOrId) throws IOException, InterruptedException {
        Path tmp = java.nio.file.Files.createTempFile("cw-drive", ".txt");
        try {
            download(linkOrId, tmp, null, null, null);
            String text = java.nio.file.Files.readString(tmp);
            if (text != null && (text.toLowerCase().contains("<html") || text.toLowerCase().contains("<!doctype"))) {
                throw new IOException("Google Drive вернул страницу вместо текста");
            }
            return text == null ? "" : text;
        } finally {
            Utils.deleteQuietly(tmp);
        }
    }

    public interface Validator {
        boolean valid(Path file);
    }

    private static byte[] headOf(Path file) throws IOException {
        try (var in = java.nio.file.Files.newInputStream(file)) {
            return in.readNBytes(64);
        }
    }

    private static boolean isHtml(byte[] head) {
        String s = new String(head, java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
        return s.contains("<!doctype html") || s.contains("<html") || s.contains("<head")
                || s.contains("googleb" + "ot") || s.contains("our servers can")
                || s.contains("can't get access to it automatically") || s.contains("virus-scan");
    }
}
