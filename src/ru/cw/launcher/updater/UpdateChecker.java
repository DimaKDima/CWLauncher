package ru.cw.launcher.updater;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.net.Http;
import ru.cw.launcher.network.GoogleDrive;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Utils;

import java.io.IOException;

/**
 * Проверка актуальности по трём файлам источника (разделы 11-12 и финальное уточнение ТЗ):
 *  cwVersion.txt  — актуальная версия сборки Common World;
 *  lVersion.txt   — актуальная версия CWLauncher;
 *  modsVersion.txt внутри ZIP должен совпадать с cwVersion.txt (проверяет ModManager).
 * Никаких cwlVersion.txt / version.txt.
 */
public final class UpdateChecker {

    public record Remote(String value, boolean fromCache, boolean available, String error) {
        public boolean ok() {
            return available && value != null && !value.isBlank();
        }
    }

    public static final String DEFAULT_CW_VERSION_URL =
            "https://drive.google.com/uc?export=download&id=0B8nS9YJ1c5Q7YjF3Z3l6Z3l6Z3l2Z3l";
    public static final String DEFAULT_L_VERSION_URL =
            "https://drive.google.com/uc?export=download&id=0B8nS9YJ1c5Q7ZGJ3Z3l6Z3l6Z3l3Z3k";

    private UpdateChecker() {
    }

    /** Чтение текстового файла версии: только цифры и точки, без HTML-страниц. */
    public static Remote readVersionFile(String url, String cacheKey) {
        try {
            String raw;
            boolean fromCache = false;
            if (url != null && url.contains("drive.google.com")) {
                raw = GoogleDrive.readText(url).trim();
            } else {
                Http.Cached c = Http.get(url, cacheKey);
                raw = c.body() == null ? "" : c.body().trim();
                fromCache = c.fromCache();
            }
            if (raw.isBlank()) {
                return new Remote(null, fromCache, false, "пустой ответ источника");
            }
            String lower = raw.toLowerCase();
            if (lower.contains("<html") || lower.contains("<!doctype")) {
                return new Remote(null, fromCache, false,
                        "источник вернул HTML-страницу вместо текста версии");
            }
            String first = raw.split("\r?\n")[0].trim();
            if (!first.matches("[0-9]+(\\.[0-9A-Za-z\\-]+)*")) {
                return new Remote(null, fromCache, false, "некорректный формат версии: "
                        + Utils.shorten(first, 40));
            }
            return new Remote(first, fromCache, true, null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Remote(null, false, false, Log.reason(e));
        }
    }

    public static Remote cwVersion(Settings cfg) {
        return readVersionFile(cfg.cwVersionUrl, "cwVersion.txt");
    }

    public static Remote lVersion(Settings cfg) {
        return readVersionFile(cfg.lVersionUrl, "lVersion.txt");
    }

    /** Версии различаются — нужно обновить. Совпадение (3.1.1 и 3.1.1) обновления не требует. */
    public static boolean buildUpdateNeeded(String installed, String remote) {
        if (remote == null || remote.isBlank()) return false;
        if (installed == null || installed.isBlank()) return true;
        return Utils.compareVersions(remote, installed) != 0;
    }

    public static boolean launcherUpdateNeeded(String installed, String remote) {
        return buildUpdateNeeded(installed, remote);
    }
}
