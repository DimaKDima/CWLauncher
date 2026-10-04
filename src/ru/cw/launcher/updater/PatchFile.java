package ru.cw.launcher.updater;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.network.GoogleDrive;
import ru.cw.launcher.updates.UpdateInfoManager;
import ru.cw.launcher.util.Log;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * patchCWL.txt — единственный список актуальных ссылок.
 * Лаунчер скачивает его раньше остальных файлов и подставляет адреса по имени файла.
 */
public final class PatchFile {

    public static final String URL =
            "https://drive.google.com/file/d/191WVmeWzJfScBzpG-XAIQPU7GFaMamlB/view?usp=drivesdk";

    private final Map<String, String> links = new LinkedHashMap<>();

    private PatchFile() {
    }

    public int size() {
        return links.size();
    }

    public static PatchFile download() throws IOException, InterruptedException {
        String text = GoogleDrive.readText(URL);
        return parse(text);
    }

    static PatchFile parse(String text) {
        PatchFile patch = new PatchFile();
        if (text == null || text.isBlank()) return patch;
        String body = text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
        for (String raw : body.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int bar = line.indexOf('|');
            if (bar <= 0 || bar >= line.length() - 1) continue;
            String name = line.substring(0, bar).trim();
            String url = line.substring(bar + 1).trim();
            if (name.isEmpty() || !url.startsWith("https://")) continue;
            patch.links.put(name.toLowerCase(Locale.ROOT), url);
            Log.info("patchCWL.txt: " + name);
        }
        return patch;
    }

    /** Подставляет ссылки из файла. Имя слева должно совпадать с именем файла. */
    public void apply(Settings cfg) {
        String launcherVersion = url("lVersion.txt");
        if (launcherVersion != null) cfg.lVersionUrl = launcherVersion;
        String modsVersion = url("cwVersion", "cwVersion.txt");
        if (modsVersion != null) cfg.cwVersionUrl = modsVersion;
        String news = url("News.txt");
        if (news != null) cfg.newsUrl = news;
        String updates = url("UpdateInfo.zip");
        if (updates != null) cfg.updateInfoUrl = updates;
        String launcher = url("CWLauncher.zip");
        if (launcher != null) cfg.launcherDownloadUrl = launcher;
        String mods = url("modsCWL.zip");
        if (mods != null) cfg.modpackUrl = mods;
        if (cfg.updateInfoUrl == null || cfg.updateInfoUrl.isBlank()) {
            cfg.updateInfoUrl = UpdateInfoManager.ARCHIVE_URL;
        }
    }

    public String url(String... names) {
        for (String name : names) {
            if (name == null) continue;
            String found = links.get(name.toLowerCase(Locale.ROOT));
            if (found != null) return found;
        }
        return null;
    }
}
