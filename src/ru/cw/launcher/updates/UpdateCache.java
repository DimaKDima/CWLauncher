package ru.cw.launcher.updates;

import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Кэш TXT отдельно от Minecraft и модов. Старая копия удаляется только после успешной новой. */
public final class UpdateCache {

    private UpdateCache() {
    }

    public static Path root() {
        return Paths.cache().resolve("update-info");
    }

    public static Path notesDir() {
        return root().resolve("notes");
    }

    public static boolean exists() {
        return Files.isDirectory(notesDir());
    }

    public static List<UpdateParser.Note> load() {
        List<UpdateParser.Note> notes = new ArrayList<>();
        Path dir = notesDir();
        if (!Files.isDirectory(dir)) return notes;
        try (var list = Files.list(dir)) {
            for (Path p : list.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (UpdateParser.versionOf(name) == null) continue;
                try {
                    notes.add(UpdateParser.note(name, UpdateTextReader.read(p)));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return UpdateParser.newestFirst(notes);
    }

    /** Подменяет кэш только если новый набор TXT уже прочитан. */
    public static void replace(List<UpdateParser.Note> notes, Path zip) throws IOException {
        Path root = root();
        Path next = root.resolve("notes.next");
        Path current = notesDir();
        Path prev = root.resolve("notes.prev");
        Utils.clearDir(next);
        Files.createDirectories(next);
        for (UpdateParser.Note note : notes) {
            Path out = next.resolve(note.fileName()).normalize();
            if (!out.startsWith(next.toAbsolutePath().normalize())) {
                throw new IOException("Недопустимое имя файла обновления");
            }
            Files.writeString(out, note.text());
        }
        Utils.deleteTree(prev);
        if (Files.exists(current)) {
            moveReplace(current, prev);
        }
        try {
            moveReplace(next, current);
        } catch (IOException e) {
            if (Files.exists(prev) && !Files.exists(current)) {
                moveReplace(prev, current);
            }
            throw e;
        }
        Utils.deleteTree(prev);
        if (zip != null && Files.isRegularFile(zip)) {
            Files.createDirectories(root);
            Path kept = root.resolve(UpdateInfoManager.ARCHIVE_NAME);
            Files.copy(zip, kept, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveReplace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
