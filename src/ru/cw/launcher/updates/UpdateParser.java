package ru.cw.launcher.updates;

import ru.cw.launcher.util.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Имя файла UpdateV&lt;MAJOR&gt;.&lt;MINOR&gt;.&lt;PATCH&gt;.txt → заголовок «Обновление X.Y.Z».
 * Другие имена не становятся записями истории.
 */
public final class UpdateParser {

    public static final Pattern NAME = Pattern.compile("^UpdateV(\\d+\\.\\d+\\.\\d+)\\.txt$");

    public record Note(String fileName, String version, String title, String text) {
    }

    private UpdateParser() {
    }

    /** Версия из имени или null, если формат не UpdateVX.Y.Z.txt. */
    public static String versionOf(String fileName) {
        if (fileName == null) return null;
        Matcher m = NAME.matcher(fileName);
        return m.matches() ? m.group(1) : null;
    }

    public static String titleOf(String version) {
        return "Обновление " + version;
    }

    public static Note note(String fileName, String text) {
        String version = versionOf(fileName);
        if (version == null) return null;
        return new Note(fileName, version, titleOf(version), text == null ? "" : text);
    }

    /** Сначала более новая версия. Некорректные элементы в список не попадают. */
    public static List<Note> newestFirst(List<Note> notes) {
        List<Note> copy = new ArrayList<>();
        if (notes != null) {
            for (Note n : notes) {
                if (n != null && versionOf(n.fileName()) != null) copy.add(n);
            }
        }
        copy.sort(Comparator.comparing((Note n) -> n.version(), Utils::compareVersions).reversed());
        return copy;
    }
}
