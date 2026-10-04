package ru.cw.launcher.updates;

import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Информационный архив обновлений Common World.
 * Не заменяет cwVersion.txt, lVersion.txt и modsVersion.txt.
 */
public final class UpdateInfoManager {

    public static final String ARCHIVE_NAME = "UpdateInfo.zip";
    public static final String ARCHIVE_URL =
            "https://drive.google.com/file/d/1VNUrgWIhZZG84th81syjGBMbNnJk2JSR/view?usp=drive_link";

    private volatile String archiveUrl = ARCHIVE_URL;

    /** Адрес из patchCWL.txt. Пустая строка оставляет прежнюю ссылку. */
    public void setArchiveUrl(String url) {
        if (url != null && !url.isBlank()) archiveUrl = url.trim();
    }

    public enum Kind {
        OK, CACHED, NETWORK, CORRUPT, EMPTY, BUSY
    }

    public record Outcome(Kind kind, List<UpdateParser.Note> notes, String message) {
        public boolean ok() {
            return kind == Kind.OK || kind == Kind.CACHED;
        }
    }

    private final AtomicBoolean busy = new AtomicBoolean(false);

    public boolean isBusy() {
        return busy.get();
    }

    public List<UpdateParser.Note> cached() {
        return UpdateCache.load();
    }

    /**
     * Скачивает ZIP во временный каталог, проверяет его и только потом заменяет кэш.
     * Ошибка сети не удаляет уже сохранённые TXT.
     */
    public Outcome sync(boolean force) {
        if (!busy.compareAndSet(false, true)) {
            return new Outcome(Kind.BUSY, cached(), "Загрузка информации об обновлениях уже идёт.");
        }
        Path zip = Paths.tmp().resolve("update-info").resolve(ARCHIVE_NAME);
        Path stage = Paths.tmp().resolve("update-info").resolve("staging");
        try {
            if (!force && UpdateCache.exists()) {
                return new Outcome(Kind.CACHED, cached(), null);
            }
            try {
                UpdateArchiveDownloader.download(archiveUrl, zip);
            } catch (IOException e) {
                Log.warn("Информация об обновлениях: " + Log.reason(e));
                List<UpdateParser.Note> kept = cached();
                if (!kept.isEmpty()) {
                    return new Outcome(Kind.CACHED, kept, "Используется сохранённая информация об обновлениях.");
                }
                String msg = e.getMessage() != null && e.getMessage().contains("повреждён")
                        ? "Архив обновлений повреждён или имеет неправильный формат."
                        : "Не удалось загрузить информацию об обновлениях.";
                return new Outcome(msg.contains("повреждён") ? Kind.CORRUPT : Kind.NETWORK, kept, msg);
            }
            List<UpdateParser.Note> notes;
            try {
                notes = UpdateArchiveExtractor.extractNotes(zip, stage);
            } catch (IOException e) {
                Log.warn("Информация об обновлениях: архив не прочитан: " + Log.reason(e));
                return new Outcome(Kind.CORRUPT, cached(),
                        "Архив обновлений повреждён или имеет неправильный формат.");
            }
            if (notes.isEmpty()) {
                return new Outcome(Kind.EMPTY, cached(),
                        "В архиве обновлений не найдено файлов с информацией.");
            }
            UpdateCache.replace(notes, zip);
            Log.info("Информация об обновлениях: записей " + notes.size());
            return new Outcome(Kind.OK, notes, null);
        } catch (Exception e) {
            Log.warn("Информация об обновлениях: " + Log.reason(e));
            List<UpdateParser.Note> kept = cached();
            if (!kept.isEmpty()) {
                return new Outcome(Kind.CACHED, kept, "Используется сохранённая информация об обновлениях.");
            }
            return new Outcome(Kind.NETWORK, kept, "Не удалось загрузить информацию об обновлениях.");
        } finally {
            Utils.deleteQuietly(stage);
            busy.set(false);
        }
    }
}
