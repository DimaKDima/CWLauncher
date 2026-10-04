package ru.cw.launcher.updates;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** TXT читается как текст. Содержимое не выполняется. */
public final class UpdateTextReader {

    private UpdateTextReader() {
    }

    public static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String utf = new String(bytes, StandardCharsets.UTF_8);
        if (utf.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("windows-1251"));
        }
        if (utf.startsWith("\uFEFF")) utf = utf.substring(1);
        return utf.replace("\r\n", "\n").replace('\r', '\n');
    }
}
