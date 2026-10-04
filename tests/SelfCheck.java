import ru.cw.launcher.accounts.ElyAuthManager;
import ru.cw.launcher.core.Progress;
import ru.cw.launcher.core.Settings;
import ru.cw.launcher.mods.ModManager;
import ru.cw.launcher.security.Secrets;
import ru.cw.launcher.updates.UpdateParser;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Проверки без сети и без окна: версии, ZIP Slip, битый config, редактирование секретов. */
public final class SelfCheck {

    public static void main(String[] args) throws Exception {
        check(Utils.compareVersions("1.0.1", "1.0.0") > 0, "1.0.1 новее 1.0.0");
        check(Utils.compareVersions("1.0.0", "1.0.0") == 0, "равные версии");
        check(Utils.compareVersions("1.2", "1.10") < 0, "1.2 старше 1.10");
        check(Utils.compareVersions("3.2.1", "3.2.0") > 0, "3.2.1 новее 3.2.0");
        check(Utils.compareVersions("3.10.0", "3.9.9") > 0, "3.10.0 новее 3.9.9");
        check("3.2.1".equals(UpdateParser.versionOf("UpdateV3.2.1.txt")), "UpdateV3.2.1");
        check(UpdateParser.versionOf("Update3.2.1.txt") == null, "без V не читается");
        check(UpdateParser.versionOf("Update_3.2.1.txt") == null, "подчёркивание не читается");
        check(UpdateParser.versionOf("Update-3.2.1.txt") == null, "дефис не читается");
        check("Обновление 3.2.1".equals(UpdateParser.titleOf("3.2.1")), "название без V и txt");
        check(ModManager.isModsVersionName("modsVersion.txt"), "modsVersion.txt");
        check(ModManager.isModsVersionName("modsVersion.txt.txt"), "двойное .txt от Windows");
        check(ModManager.isModsVersionName("version.txt"), "version.txt");
        check("3.1.1".equals(ModManager.versionToken("3.1.1")), "токен 3.1.1");
        check("3.1.1".equals(ModManager.versionToken("\uFEFF3.1.1\r\n")), "BOM и перевод строки");
        check(Utils.compareVersions("3.1.1", "3.1.1") == 0, "совпадающие версии модов");
        Progress pace = new Progress();
        long mb = 1024L * 1024L;
        pace.progress(40 * mb, 56 * mb, "загрузка");
        check(pace.percent() == 71, "40 из 56 МБ это 71%");

        Settings broken = Settings.fromMap(null);
        broken.validate();
        check("1.20.1".equals(broken.minecraftVersion), "пустой config даёт 1.20.1");
        check(broken.autoUpdateBuild && broken.autoUpdateLauncher, "два автообновления включены по умолчанию");
        broken.ramMb = 1;
        broken.validate();
        check(broken.ramMb >= 512, "слишком маленький RAM поднят до минимума");

        String red = Secrets.redact("accessToken=secret-value password=qwerty");
        check(red != null && !red.contains("secret-value") && !red.contains("qwerty"), "секреты вырезаются из текста");

        Path dir = Files.createTempDirectory("cw-zip");
        Path zip = dir.resolve("bad.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../escape.txt"));
            zos.write("no".getBytes());
            zos.closeEntry();
        }
        Path target = dir.resolve("out");
        boolean slipped = false;
        try {
            Utils.extractZip(zip, target);
        } catch (Exception e) {
            slipped = e.getMessage() != null && e.getMessage().contains("ZIP Slip");
        }
        String written = Json.write(java.util.Map.of("minecraftVersion", "1.20.1", "autoUpdateBuild", true));
        check(written.contains("\"minecraftVersion\":") && !written.contains("\"minecraftVersion\"\""), "JSON без лишней кавычки");
        check("1.20.1".equals(Json.str(Json.parseObject(written), "minecraftVersion", "")), "JSON читается обратно");

        String auth = ElyAuthManager.authorizeUrl("cw-id", "https://cw.example/oauth/ely", "state-1");
        check(auth.startsWith("https://account.ely.by/oauth2/v1?"), "OAuth URL Ely.by");
        check(auth.contains("response_type=code"), "response_type=code");
        check(auth.contains("minecraft_server_session"), "scope minecraft_server_session");
        check(auth.contains("offline_access") && auth.contains("account_info"), "scope account_info и offline_access");
        check(!auth.contains("client_secret") && !auth.contains("password"), "в ссылке нет секрета и пароля");
        check(ElyAuthManager.redirectAllowed("https://cw.example/oauth/ely"), "https redirect допустим");
        check(!ElyAuthManager.redirectAllowed("http://127.0.0.1:47622/callback"), "localhost запрещён");
        check(!ElyAuthManager.backendAllowed("http://cw.example/oauth/ely"), "сервер CW только https");
        check(ElyAuthManager.sameState("abc", "abc") && !ElyAuthManager.sameState("abc", "abd"), "state сравнивается");
        check(!ElyAuthManager.sameState("abc", null), "пустой state отклоняется");
        check("Вход отменён".equals(ElyAuthManager.explainOauthError("access_denied", "denied")), "отмена входа");

        check(slipped, "ZIP Slip отклонён");
        check(!Files.exists(dir.resolve("escape.txt")), "файл за пределами каталога не создан");

        System.out.println("SelfCheck OK");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        System.out.println("OK  " + name);
    }
}
