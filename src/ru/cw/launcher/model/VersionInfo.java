package ru.cw.launcher.model;

/** Версия Minecraft из манифеста Mojang + локальный статус. */
public class VersionInfo {

    public final String id;
    public final String type;
    public final String url;
    public final String releaseTime;
    public boolean installed;
    /** Имя установленного профиля (для Fabric это, например, "1.20.1_Fabric"). */
    public String installedProfileId;
    public boolean fabricInstalled;
    public String fabricVersion;
    public int modCount;
    public String buildVersion;

    public VersionInfo(String id, String type, String url, String releaseTime) {
        this.id = id;
        this.type = type == null ? "release" : type;
        this.url = url;
        this.releaseTime = releaseTime == null ? "" : releaseTime;
    }

    public boolean isRelease() {
        return "release".equals(type);
    }

    public String typeLabel() {
        return switch (type) {
            case "release" -> "релиз";
            case "snapshot" -> "снапшот";
            case "old_beta" -> "старая бета";
            case "old_alpha" -> "старая альфа";
            default -> type;
        };
    }

    @Override
    public String toString() {
        return id + " (" + typeLabel() + ")";
    }
}
