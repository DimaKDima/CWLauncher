package ru.cw.launcher.updates;

import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Безопасная распаковка информационного ZIP. Файлы из архива не запускаются. */
public final class UpdateArchiveExtractor {

    private UpdateArchiveExtractor() {
    }

    public static List<UpdateParser.Note> extractNotes(Path zip, Path targetDir) throws IOException {
        Utils.clearDir(targetDir);
        Files.createDirectories(targetDir);
        int n = Utils.extractZip(zip, targetDir);
        if (n < 0) throw new IOException("Архив обновлений не распакован");
        List<UpdateParser.Note> notes = new ArrayList<>();
        try (var walk = Files.walk(targetDir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (UpdateParser.versionOf(name) == null) {
                    Log.info("Информация об обновлениях: пропущен файл вне формата UpdateVX.Y.Z.txt — " + name);
                    continue;
                }
                notes.add(UpdateParser.note(name, UpdateTextReader.read(p)));
            }
        }
        return UpdateParser.newestFirst(notes);
    }
}
