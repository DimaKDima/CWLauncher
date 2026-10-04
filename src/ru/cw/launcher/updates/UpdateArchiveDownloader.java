package ru.cw.launcher.updates;

import ru.cw.launcher.network.GoogleDrive;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Path;

/** Скачивает UpdateInfo.zip. Это не архив модов. */
public final class UpdateArchiveDownloader {

    private UpdateArchiveDownloader() {
    }

    public static void download(String url, Path dest) throws IOException, InterruptedException {
        Utils.deleteQuietly(dest);
        GoogleDrive.download(url, dest, Utils::looksLikeZip, null, null);
        if (!Utils.looksLikeZip(dest)) {
            Utils.deleteQuietly(dest);
            throw new IOException("Архив обновлений повреждён или имеет неправильный формат.");
        }
    }
}
