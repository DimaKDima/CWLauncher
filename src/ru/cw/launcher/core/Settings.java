package ru.cw.launcher.core;

import ru.cw.launcher.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки лаунчера (раздел 19 ТЗ). Хранятся в %APPDATA%\CWLauncher\config.json.
 * Секреты (токены, пароли) здесь не хранятся — они в secrets.dpx (DPAPI).
 */
public class Settings {

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    // --- общее ---
    public String minecraftVersion = "1.20.1";
    /** Выбрана сборка сервера Common World: Minecraft 1.20.1, Fabric и моды. */
    public boolean commonWorld = true;
    public String loader = "fabric";                 // fabric | vanilla
    /** Галочка Fabric у версии. Нет записи — включено только у 1.20.1. */
    public Map<String, Boolean> fabricVersions = new LinkedHashMap<>();
    public String fabricLoaderVersion = "";          // пусто → последняя стабильная
    public int ramMb = 0;                            // 0 → 50% доступной RAM
    public boolean autoStart = true;                 // запуск лаунчера вместе с Windows
    public boolean autoUpdateBuild = true;           // автообновление сборки Common World
    public boolean autoUpdateLauncher = true;        // автообновление CWLauncher
    public boolean internetLeader = false;           // повышенный приоритет сетевого трафика игры
    public String theme = THEME_DARK;
    public boolean useCustomBackground = false;
    public String backgroundPath = "";
    public String telegramUrl = "https://t.me/CommonWorldNews";
    public String discordUrl = "https://discord.gg/V6HnUWWj3J";
    public String supportUrl = "https://discord.gg/vEkuksnUQ4";

    // --- профили и аккаунты ---
    public String activeProfileId = "";
    public String activeAccountId = "";

    // --- графика игры (безопасные параметры) ---
    public int screenWidth = 1280;
    public int screenHeight = 720;
    public boolean screenFullscreen = false;
    public int framerateLimit = 240;                 // 0 → без ограничения
    public boolean vsync = true;

    // --- дополнительные параметры запуска ---
    public String extraJvmArgs = "";
    public String extraGameArgs = "";
    public String serverAddress = "";                // авто-подключение при запуске (опционально)

    // --- сеть / источники ---
    public static final String CW_VERSION_URL =
            "https://drive.google.com/file/d/1BM_LzV7hhjvwEMsozMjZVPFUEg1nQP93/view?usp=drivesdk";
    public static final String L_VERSION_URL =
            "https://drive.google.com/file/d/1mxxkPgndfSy0HiJ31sy2q1SYauZx8L83/view?usp=drivesdk";
    public static final String NEWS_URL =
            "https://drive.google.com/file/d/1NdVMevnHKWKcg07_zGrtaeF0G_otdXBz/view?usp=drivesdk";
    public static final String LAUNCHER_URL =
            "https://drive.google.com/file/d/1fnfLPRYOZE8ZiDArl6Bjunu3hLUnEEra/view?usp=drivesdk";
    public static final String MODPACK_URL =
            "https://drive.google.com/file/d/1yPV7USzhlWKNdBdqypyyB2huFLlBxmAF/view?usp=drive_link";
    public static final String SERVER = "commonworld.pterohost.ru:25965";

    public String cwVersionUrl = CW_VERSION_URL;
    public String lVersionUrl = L_VERSION_URL;
    public String launcherDownloadUrl = LAUNCHER_URL;
    public String modpackUrl = MODPACK_URL;
    public String newsUrl = NEWS_URL;
    public String updateInfoUrl = ru.cw.launcher.updates.UpdateInfoManager.ARCHIVE_URL;
    public String updateTxtUrl = "";
    public String galleryUrl = "";
    public String serverIp = SERVER;
    /** Client id приложения Ely.by. Client secret в лаунчер не записывается. */
    public String elyClientId = "";
    /** Точный redirect URI, зарегистрированный в приложении Ely.by. */
    public String elyRedirectUri = "";
    /** HTTPS-адрес сервера CW, который обменивает код Ely.by на токен. */
    public String elyBackendUrl = "";
    public int checkIntervalSec = 120;               // планировщик проверок

    // --- техпараметры ---
    public String javaPath = "";                     // пусто → автовыбор
    /** Язык Minecraft, например ru_ru. Пусто — язык Windows. */
    public String gameLanguage = "";
    public String gameDir = "";
    /** Папка игры для каждой версии Minecraft: версия → полный путь. */
    public Map<String, String> versionDirs = new LinkedHashMap<>();
    /** Затемнение фона во всех окнах, кроме главного меню. 0 — без затемнения, 80 — сильно. */
    public int backgroundDim = 48;
    public boolean offlineAllowed = true;
    public boolean closeLauncherOnLaunch = false;
    public boolean developerMode = false;
    public boolean verboseLogging = false;
    public boolean notificationsEnabled = true;
    /** Точность строки времени: sec, min, hour, day, week. */
    public String playTimeUnit = "sec";
    /** Проверка целостности модов перед тем, как показать «Проверить сборку». */
    public boolean verifyBuildOnLaunch = true;
    public boolean modBackups = true;
    public int modBackupCount = 5;
    public boolean uiEffects = true;
    public boolean uiBlur = true;
    public boolean uiAnim = true;
    public boolean uiTransitions = false;
    public String uiStyle = "standard";
    public boolean showFps = false;
    public boolean checkDiskSpace = true;
    public boolean useSystemProxy = false;
    public boolean limitBandwidth = false;
    public int bandwidthMbit = 0;
    public boolean disableFullscreenOpt = false;
    public boolean highPriority = false;
    public boolean disableRealtimeOpt = false;
    public String launcherUpdateEvery = "day";
    public String modsUpdateEvery = "day";
    public String launchBehavior = "menu";
    public int maxAttempts = 3;                      // попыток получить корректную сборку
    public Map<String, Object> raw = new LinkedHashMap<>();

    // --- рантайм (не сохраняется) ---
    /** Актуальная версия сборки из cwVersion.txt (null — источник недоступен). */
    public transient String remoteBuildVersion = null;
    /** Установленная версия сборки (из build-state.json). */
    public transient String buildVersion = null;
    /** Актуальная версия лаунчера из lVersion.txt. */
    public transient String remoteLauncherVersion = null;

    /** Каталог экземпляра для выбранной версии Minecraft. */
    public String instanceId() {
        return ru.cw.launcher.util.Paths.sanitize(minecraftVersion);
    }

    /** Галочка Fabric у обычной версии Minecraft. У сборки сервера Fabric включается отдельно. */
    public boolean fabricFor(String version) {
        if (version == null || version.isBlank()) return false;
        if (fabricVersions.containsKey(version)) return Boolean.TRUE.equals(fabricVersions.get(version));
        return false;
    }

    /** Моды Common World ставятся только когда выбрана сборка сервера. */
    public boolean commonWorldMods() {
        return commonWorld;
    }

    public Settings copy() {
        return fromMap(toMap());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("minecraftVersion", minecraftVersion);
        m.put("commonWorld", commonWorld);
        m.put("loader", loader);
        m.put("fabricVersions", new LinkedHashMap<>(fabricVersions));
        m.put("fabricLoaderVersion", fabricLoaderVersion);
        m.put("ramMb", ramMb);
        m.put("autoStart", autoStart);
        m.put("autoUpdateBuild", autoUpdateBuild);
        m.put("autoUpdateLauncher", autoUpdateLauncher);
        m.put("internetLeader", internetLeader);
        m.put("theme", theme);
        m.put("useCustomBackground", useCustomBackground);
        m.put("backgroundPath", backgroundPath);
        m.put("telegramUrl", telegramUrl);
        m.put("discordUrl", discordUrl);
        m.put("supportUrl", supportUrl);
        m.put("activeProfileId", activeProfileId);
        m.put("activeAccountId", activeAccountId);
        Map<String, Object> gfx = new LinkedHashMap<>();
        gfx.put("width", screenWidth);
        gfx.put("height", screenHeight);
        gfx.put("fullscreen", screenFullscreen);
        gfx.put("framerateLimit", framerateLimit);
        gfx.put("vsync", vsync);
        m.put("graphics", gfx);
        m.put("extraJvmArgs", extraJvmArgs);
        m.put("extraGameArgs", extraGameArgs);
        m.put("serverAddress", serverAddress);
        Map<String, Object> net = new LinkedHashMap<>();
        net.put("cwVersionUrl", cwVersionUrl);
        net.put("lVersionUrl", lVersionUrl);
        net.put("launcherDownloadUrl", launcherDownloadUrl);
        net.put("modpackUrl", modpackUrl);
        net.put("newsUrl", newsUrl);
        net.put("updateInfoUrl", updateInfoUrl);
        net.put("updateTxtUrl", updateTxtUrl);
        net.put("galleryUrl", galleryUrl);
        net.put("serverIp", serverIp);
        net.put("elyClientId", elyClientId);
        net.put("elyRedirectUri", elyRedirectUri == null ? "" : elyRedirectUri);
        net.put("elyBackendUrl", elyBackendUrl == null ? "" : elyBackendUrl);
        net.put("checkIntervalSec", checkIntervalSec);
        m.put("network", net);
        Map<String, Object> tech = new LinkedHashMap<>();
        tech.put("javaPath", javaPath);
        tech.put("gameLanguage", gameLanguage == null ? "" : gameLanguage);
        tech.put("gameDir", gameDir);
        tech.put("versionDirs", new LinkedHashMap<>(versionDirs));
        tech.put("backgroundDim", backgroundDim);
        tech.put("offlineAllowed", offlineAllowed);
        tech.put("closeLauncherOnLaunch", false);
        tech.put("developerMode", developerMode);
        tech.put("verboseLogging", verboseLogging);
        tech.put("notificationsEnabled", notificationsEnabled);
        tech.put("playTimeUnit", playTimeUnit == null ? "sec" : playTimeUnit);
        tech.put("verifyBuildOnLaunch", verifyBuildOnLaunch);
        tech.put("modBackups", modBackups);
        tech.put("modBackupCount", modBackupCount);
        tech.put("uiEffects", uiEffects);
        tech.put("uiBlur", uiBlur);
        tech.put("uiAnim", uiAnim);
        tech.put("uiTransitions", uiTransitions);
        tech.put("uiStyle", uiStyle);
        tech.put("showFps", showFps);
        tech.put("checkDiskSpace", checkDiskSpace);
        tech.put("useSystemProxy", useSystemProxy);
        tech.put("limitBandwidth", limitBandwidth);
        tech.put("bandwidthMbit", bandwidthMbit);
        tech.put("disableFullscreenOpt", disableFullscreenOpt);
        tech.put("highPriority", highPriority);
        tech.put("disableRealtimeOpt", disableRealtimeOpt);
        tech.put("launcherUpdateEvery", launcherUpdateEvery);
        tech.put("modsUpdateEvery", modsUpdateEvery);
        tech.put("launchBehavior", launchBehavior);
        tech.put("maxAttempts", maxAttempts);
        m.put("technical", tech);
        m.put("firstLaunch", raw.get("firstLaunch"));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Settings fromMap(Map<String, Object> m) {
        Settings s = new Settings();
        s.raw = m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        if (m == null) return s;
        s.minecraftVersion = Json.str(m, "minecraftVersion", s.minecraftVersion);
        s.commonWorld = !m.containsKey("commonWorld") || Json.bool(m, "commonWorld", true);
        s.loader = Json.str(m, "loader", s.loader);
        Map<String, Object> fabricMap = Json.obj(m, "fabricVersions");
        if (fabricMap != null) {
            for (Map.Entry<String, Object> e : fabricMap.entrySet()) {
                Object value = e.getValue();
                boolean on = Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
                s.fabricVersions.put(e.getKey(), on);
            }
        }
        s.fabricLoaderVersion = Json.str(m, "fabricLoaderVersion", s.fabricLoaderVersion);
        s.ramMb = (int) Json.num(m, "ramMb", s.ramMb);
        s.autoStart = Json.bool(m, "autoStart", s.autoStart);
        s.autoUpdateBuild = Json.bool(m, "autoUpdateBuild", s.autoUpdateBuild);
        s.autoUpdateLauncher = Json.bool(m, "autoUpdateLauncher", s.autoUpdateLauncher);
        s.internetLeader = Json.bool(m, "internetLeader", s.internetLeader);
        s.theme = Json.str(m, "theme", s.theme);
        s.useCustomBackground = Json.bool(m, "useCustomBackground", s.useCustomBackground);
        s.backgroundPath = Json.str(m, "backgroundPath", s.backgroundPath);
        s.telegramUrl = Json.str(m, "telegramUrl", s.telegramUrl);
        s.discordUrl = Json.str(m, "discordUrl", s.discordUrl);
        s.supportUrl = Json.str(m, "supportUrl", s.supportUrl);
        s.activeProfileId = Json.str(m, "activeProfileId", s.activeProfileId);
        s.activeAccountId = Json.str(m, "activeAccountId", s.activeAccountId);
        Map<String, Object> gfx = Json.obj(m, "graphics");
        if (gfx != null) {
            s.screenWidth = (int) Json.num(gfx, "width", s.screenWidth);
            s.screenHeight = (int) Json.num(gfx, "height", s.screenHeight);
            s.screenFullscreen = Json.bool(gfx, "fullscreen", s.screenFullscreen);
            s.framerateLimit = (int) Json.num(gfx, "framerateLimit", s.framerateLimit);
            s.vsync = Json.bool(gfx, "vsync", s.vsync);
        }
        s.extraJvmArgs = Json.str(m, "extraJvmArgs", s.extraJvmArgs);
        s.extraGameArgs = Json.str(m, "extraGameArgs", s.extraGameArgs);
        s.serverAddress = Json.str(m, "serverAddress", s.serverAddress);
        Map<String, Object> net = Json.obj(m, "network");
        if (net != null) {
            s.cwVersionUrl = Json.str(net, "cwVersionUrl", s.cwVersionUrl);
            s.lVersionUrl = Json.str(net, "lVersionUrl", s.lVersionUrl);
            s.modpackUrl = Json.str(net, "modpackUrl", s.modpackUrl);
            s.launcherDownloadUrl = Json.str(net, "launcherDownloadUrl", s.launcherDownloadUrl);
        s.newsUrl = Json.str(net, "newsUrl", s.newsUrl);
            s.updateInfoUrl = Json.str(net, "updateInfoUrl", s.updateInfoUrl);
            s.updateTxtUrl = Json.str(net, "updateTxtUrl", s.updateTxtUrl);
            s.galleryUrl = Json.str(net, "galleryUrl", s.galleryUrl);
            s.serverIp = Json.str(net, "serverIp", s.serverIp);
            s.elyClientId = Json.str(net, "elyClientId", s.elyClientId);
            s.elyRedirectUri = Json.str(net, "elyRedirectUri", s.elyRedirectUri);
            s.elyBackendUrl = Json.str(net, "elyBackendUrl", s.elyBackendUrl);
            if (legacyServer(s.serverIp)) s.serverIp = SERVER;
            s.checkIntervalSec = (int) Json.num(net, "checkIntervalSec", s.checkIntervalSec);
        }
        Map<String, Object> tech = Json.obj(m, "technical");
        if (tech != null) {
            s.javaPath = Json.str(tech, "javaPath", s.javaPath);
            s.gameLanguage = Json.str(tech, "gameLanguage", s.gameLanguage);
            s.gameDir = Json.str(tech, "gameDir", s.gameDir);
            s.backgroundDim = (int) Json.num(tech, "backgroundDim", s.backgroundDim);
            Map<String, Object> dirs = Json.obj(tech, "versionDirs");
            if (dirs != null) {
                for (Map.Entry<String, Object> e : dirs.entrySet()) {
                    if (e.getValue() != null) s.versionDirs.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
            s.offlineAllowed = Json.bool(tech, "offlineAllowed", s.offlineAllowed);
            s.closeLauncherOnLaunch = false;
            s.developerMode = Json.bool(tech, "developerMode", s.developerMode);
            s.verboseLogging = Json.bool(tech, "verboseLogging", s.verboseLogging);
            s.notificationsEnabled = Json.bool(tech, "notificationsEnabled", s.notificationsEnabled);
            s.playTimeUnit = Json.str(tech, "playTimeUnit", s.playTimeUnit);
            s.maxAttempts = (int) Json.num(tech, "maxAttempts", s.maxAttempts);
            s.verifyBuildOnLaunch = Json.bool(tech, "verifyBuildOnLaunch", s.verifyBuildOnLaunch);
            s.modBackups = Json.bool(tech, "modBackups", s.modBackups);
            s.modBackupCount = (int) Json.num(tech, "modBackupCount", s.modBackupCount);
            s.uiEffects = Json.bool(tech, "uiEffects", s.uiEffects);
            s.uiBlur = Json.bool(tech, "uiBlur", s.uiBlur);
            s.uiAnim = Json.bool(tech, "uiAnim", s.uiAnim);
            s.uiTransitions = Json.bool(tech, "uiTransitions", s.uiTransitions);
            s.uiStyle = Json.str(tech, "uiStyle", s.uiStyle);
            s.showFps = Json.bool(tech, "showFps", s.showFps);
            s.checkDiskSpace = Json.bool(tech, "checkDiskSpace", s.checkDiskSpace);
            s.useSystemProxy = Json.bool(tech, "useSystemProxy", s.useSystemProxy);
            s.limitBandwidth = Json.bool(tech, "limitBandwidth", s.limitBandwidth);
            s.bandwidthMbit = (int) Json.num(tech, "bandwidthMbit", s.bandwidthMbit);
            s.disableFullscreenOpt = Json.bool(tech, "disableFullscreenOpt", s.disableFullscreenOpt);
            s.highPriority = Json.bool(tech, "highPriority", s.highPriority);
            s.disableRealtimeOpt = Json.bool(tech, "disableRealtimeOpt", s.disableRealtimeOpt);
            s.launcherUpdateEvery = Json.str(tech, "launcherUpdateEvery", s.launcherUpdateEvery);
            s.modsUpdateEvery = Json.str(tech, "modsUpdateEvery", s.modsUpdateEvery);
            s.launchBehavior = Json.str(tech, "launchBehavior", s.launchBehavior);
        }
        s.validate();
        return s;
    }

    /** Старый IP и адрес без порта заменяются на текущий сервер. */
    private static boolean legacyServer(String ip) {
        if (ip == null || ip.isBlank()) return true;
        String s = ip.trim();
        return s.contains("45.138.173.229")
                || s.equalsIgnoreCase("commonworld.pterohost.ru")
                || !s.equalsIgnoreCase(SERVER) && s.contains("pterohost");
    }

    /** Приведение значений в допустимый диапазон (защита от битого конфига). */
    public void validate() {
        if (minecraftVersion == null || minecraftVersion.isBlank() || "1.20.3".equals(minecraftVersion)) {
            minecraftVersion = "1.20.1";
        }
        if (commonWorld) minecraftVersion = "1.20.1";
        if (lVersionUrl == null || lVersionUrl.isBlank() || lVersionUrl.contains("common-world.example")) {
            lVersionUrl = L_VERSION_URL;
        }
        if (cwVersionUrl == null || cwVersionUrl.isBlank() || cwVersionUrl.contains("common-world.example")) {
            cwVersionUrl = CW_VERSION_URL;
        }
        if (launcherDownloadUrl == null || launcherDownloadUrl.isBlank()
                || launcherDownloadUrl.contains("common-world.example")) {
            launcherDownloadUrl = LAUNCHER_URL;
        }
        if (modpackUrl == null || modpackUrl.isBlank()
                || modpackUrl.contains("1wgie2kNdWdB9SAWZkNDWTbUFLwu7AuJ3")
                || modpackUrl.contains("modsCommonWorld_1_20_1Fabric")) {
            modpackUrl = MODPACK_URL;
        }
        if (newsUrl == null || newsUrl.isBlank()) newsUrl = NEWS_URL;
        if (updateInfoUrl == null || updateInfoUrl.isBlank()) {
            updateInfoUrl = ru.cw.launcher.updates.UpdateInfoManager.ARCHIVE_URL;
        }
        if (playTimeUnit == null || playTimeUnit.isBlank()) playTimeUnit = "sec";
        switch (playTimeUnit) {
            case "sec", "min", "hour", "day", "week" -> {
            }
            default -> playTimeUnit = "sec";
        }
        if (legacyServer(serverIp)) serverIp = SERVER;
        if (elyClientId == null) elyClientId = "";
        else elyClientId = elyClientId.trim();
        if (elyRedirectUri == null) elyRedirectUri = "";
        else elyRedirectUri = elyRedirectUri.trim();
        if (elyBackendUrl == null) elyBackendUrl = "";
        else elyBackendUrl = elyBackendUrl.trim();
        if (backgroundDim < 0) backgroundDim = 0;
        if (backgroundDim > 80) backgroundDim = 80;
        if (modBackupCount < 1) modBackupCount = 1;
        if (modBackupCount > 20) modBackupCount = 20;
        if (bandwidthMbit < 0) bandwidthMbit = 0;
        if (bandwidthMbit > 1000) bandwidthMbit = 1000;
        if (uiStyle == null || uiStyle.isBlank()) uiStyle = "standard";
        if (launcherUpdateEvery == null || launcherUpdateEvery.isBlank()) launcherUpdateEvery = "day";
        if (modsUpdateEvery == null || modsUpdateEvery.isBlank()) modsUpdateEvery = "day";
        if (launchBehavior == null || launchBehavior.isBlank()) launchBehavior = "menu";
        closeLauncherOnLaunch = false;
        versionDirs.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isBlank()
                || ru.cw.launcher.util.Paths.forbiddenGameDir(java.nio.file.Paths.get(e.getValue())));
        loader = commonWorld || fabricFor(minecraftVersion) ? "fabric" : "vanilla";
        theme = THEME_DARK;
        if (ramMb < 0) ramMb = 0;
        if (ramMb > 0 && ramMb < 512) ramMb = 512;
        int ramCap = ru.cw.launcher.settings.SettingsManager.maxGameRamMb();
        if (ramMb > ramCap) ramMb = ramCap;
        if (screenWidth < 854) screenWidth = 854;
        if (screenHeight < 480) screenHeight = 480;
        if (screenWidth > 7680) screenWidth = 7680;
        if (screenHeight > 4320) screenHeight = 4320;
        if (framerateLimit < 0) framerateLimit = 0;
        if (framerateLimit > 1000) framerateLimit = 1000;
        if (checkIntervalSec < 30) checkIntervalSec = 30;
        if (maxAttempts < 1) maxAttempts = 1;
        if (maxAttempts > 5) maxAttempts = 5;
        String telegram = telegramUrl == null ? "" : telegramUrl.trim().toLowerCase(java.util.Locale.ROOT);
        if (telegram.isBlank() || (telegram.contains("t.me/commonworld") && !telegram.contains("commonworldnews"))) {
            telegramUrl = "https://t.me/CommonWorldNews";
        }
        if (discordUrl == null || discordUrl.isBlank()) discordUrl = "https://discord.gg/V6HnUWWj3J";
        if (supportUrl == null || supportUrl.isBlank()) supportUrl = "https://discord.gg/vEkuksnUQ4";
    }

    /** Шаблон ссылки на файл лаунчера; подстановка {version}. */
    public String launcherDownloadUrlFor(String version) {
        return launcherDownloadUrl.replace("{version}", version == null ? "" : version);
    }

    public boolean isLightTheme() {
        return THEME_LIGHT.equals(theme);
    }

    /** Код языка Minecraft: выбор в настройках или язык Windows. */
    public String resolvedLanguage() {
        if (gameLanguage != null && gameLanguage.matches("[a-z]{2}_[a-z]{2}")) return gameLanguage;
        java.util.Locale locale = java.util.Locale.getDefault();
        String language = locale.getLanguage().toLowerCase(java.util.Locale.ROOT);
        String country = locale.getCountry().toLowerCase(java.util.Locale.ROOT);
        if (country.isBlank()) {
            country = switch (language) {
                case "en" -> "us";
                case "uk" -> "ua";
                case "be" -> "by";
                case "kk" -> "kz";
                case "pt" -> "br";
                case "zh" -> "cn";
                case "ja" -> "jp";
                case "ko" -> "kr";
                default -> language;
            };
        }
        if (language.isBlank()) return "ru_ru";
        return language + "_" + country;
    }

    public static String[][] languages() {
        return new String[][]{
                {"", "Как в Windows"},
                {"ru_ru", "Русский"},
                {"en_us", "English"},
                {"uk_ua", "Українська"},
                {"be_by", "Беларуская"},
                {"kk_kz", "Қазақша"},
                {"de_de", "Deutsch"},
                {"fr_fr", "Français"},
                {"es_es", "Español"},
                {"es_mx", "Español (México)"},
                {"pt_br", "Português (Brasil)"},
                {"pt_pt", "Português"},
                {"pl_pl", "Polski"},
                {"it_it", "Italiano"},
                {"tr_tr", "Türkçe"},
                {"nl_nl", "Nederlands"},
                {"sv_se", "Svenska"},
                {"fi_fi", "Suomi"},
                {"cs_cz", "Čeština"},
                {"sk_sk", "Slovenčina"},
                {"hu_hu", "Magyar"},
                {"ro_ro", "Română"},
                {"bg_bg", "Български"},
                {"da_dk", "Dansk"},
                {"nb_no", "Norsk"},
                {"el_gr", "Ελληνικά"},
                {"ja_jp", "日本語"},
                {"ko_kr", "한국어"},
                {"zh_cn", "简体中文"},
                {"zh_tw", "繁體中文"},
                {"id_id", "Bahasa Indonesia"},
                {"ar_sa", "العربية"}
        };
    }
}
