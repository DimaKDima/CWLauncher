package ru.cw.launcher.profiles;

import ru.cw.launcher.util.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Профиль лаунчера (раздел 16 и доп. функция 1): имя, версия Minecraft, версия Fabric,
 * аккаунт, RAM, дополнительные аргументы, графические настройки, каталог-экземпляр.
 * Профиль и аккаунт — отдельные сущности: профиль только ссылается на аккаунт.
 */
public class LauncherProfile {

    public String id = UUID.randomUUID().toString();
    public String name = "Common World";
    public String minecraftVersion = "1.20.1";
    public String fabricLoader = "";                     // пусто → актуальная/последняя
    public String accountId = "";
    public int ramMb = -1;                               // -1 → общее значение из настроек
    public String jvmArgs = "";
    public String instanceId = "CommonWorld";
    public boolean useGlobalSettings = true;

    // настройки графики Minecraft (безопасные параметры options.txt)
    public String resolution = "";                       // "1280x720" или "" → стандартно
    public boolean fullscreen = false;
    public boolean vsync = false;
    public int fpsLimit = 120;
    public String renderDistance = "";                   // "10" или ""
    public String guiScale = "";                         // "" → авто

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("minecraftVersion", minecraftVersion);
        m.put("fabricLoader", fabricLoader);
        m.put("accountId", accountId);
        m.put("ramMb", ramMb);
        m.put("jvmArgs", jvmArgs);
        m.put("instanceId", instanceId);
        m.put("useGlobalSettings", useGlobalSettings);
        m.put("resolution", resolution);
        m.put("fullscreen", fullscreen);
        m.put("vsync", vsync);
        m.put("fpsLimit", fpsLimit);
        m.put("renderDistance", renderDistance);
        m.put("guiScale", guiScale);
        return m;
    }

    public static LauncherProfile fromMap(Map<String, Object> m) {
        LauncherProfile p = new LauncherProfile();
        p.id = Json.str(m, "id", p.id);
        p.name = Json.str(m, "name", p.name);
        p.minecraftVersion = Json.str(m, "minecraftVersion", p.minecraftVersion);
        p.fabricLoader = Json.str(m, "fabricLoader", "");
        p.accountId = Json.str(m, "accountId", "");
        p.ramMb = (int) Json.num(m, "ramMb", -1);
        p.jvmArgs = Json.str(m, "jvmArgs", "");
        p.instanceId = Json.str(m, "instanceId", p.instanceId);
        p.useGlobalSettings = Json.bool(m, "useGlobalSettings", true);
        p.resolution = Json.str(m, "resolution", "");
        p.fullscreen = Json.bool(m, "fullscreen", false);
        p.vsync = Json.bool(m, "vsync", false);
        p.fpsLimit = (int) Json.num(m, "fpsLimit", 120);
        p.renderDistance = Json.str(m, "renderDistance", "");
        p.guiScale = Json.str(m, "guiScale", "");
        return p;
    }

    public LauncherProfile copy() {
        return fromMap(toMap());
    }
}
