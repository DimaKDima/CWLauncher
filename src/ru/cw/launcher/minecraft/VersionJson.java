package ru.cw.launcher.minecraft;

import ru.cw.launcher.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Модель version.json Minecraft (Mojang формат, включая inheritsFrom для Fabric/Forge
 * и старые minecraftArguments для версий до 1.13).
 */
public class VersionJson {

    public String id;
    public String type = "release";
    public String inheritsFrom;
    public String mainClass;
    public String assets = "legacy";
    public String assetIndexUrl;
    public String assetIndexSha1;
    public long assetIndexSize;
    public long assetIndexTotalSize;
    public int javaMajor = 17;
    public String javaComponent = "java-runtime-gamma";
    public String clientUrl;
    public String clientSha1;
    public long clientSize;
    public final List<Lib> libraries = new ArrayList<>();
    public final List<String> gameArgs = new ArrayList<>();
    public final List<String> jvmArgs = new ArrayList<>();

    public static class Lib {
        public String name;
        public String url = "https://libraries.minecraft.net/";
        /** Полный адрес файла из version.json. Если есть — скачивается он, без склейки пути. */
        public String artifactUrl;
        public String path;
        public String sha1;
        public long size;
        /** null — обычная библиотека; иначе — нативный классификатор для текущей ОС. */
        public String nativeClassifier;

        public boolean isNative() {
            return nativeClassifier != null;
        }

        /** скачивать ли вообще (учитывая правила) — поле заполняется при разборе */
        public boolean download = true;

        public String fullUrl() {
            if (artifactUrl != null && artifactUrl.startsWith("http")) return artifactUrl;
            String base = url != null && url.startsWith("http") ? url : "https://libraries.minecraft.net/";
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String p = path != null ? path : mavenPath(name, nativeClassifier);
            if (p.startsWith("/")) p = p.substring(1);
            return base + "/" + p;
        }

        public static String mavenPath(String coords, String classifier) {
            String[] p = coords.split(":");
            if (p.length < 3) return coords;
            String c = classifier == null || classifier.isEmpty() ? "" : "-" + classifier;
            return p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + c + ".jar";
        }
    }

    // ------------------------------------------------------------------ parse

    public static VersionJson load(java.nio.file.Path jsonFile) throws java.io.IOException {
        return parse(ru.cw.launcher.util.Json.parseObject(ru.cw.launcher.util.Utils.readString(jsonFile)));
    }

    public static VersionJson parse(Map<String, Object> root) {
        VersionJson v = new VersionJson();
        v.id = Json.str(root, "id", null);
        v.type = Json.str(root, "type", "release");
        v.inheritsFrom = Json.str(root, "inheritsFrom", null);
        v.mainClass = Json.str(root, "mainClass", null);
        v.assets = Json.str(root, "assets", "legacy");
        Map<String, Object> ai = Json.obj(root, "assetIndex");
        if (ai != null) {
            v.assetIndexUrl = Json.str(ai, "url", null);
            v.assetIndexSha1 = Json.str(ai, "sha1", null);
            v.assetIndexSize = (long) Json.num(ai, "size", 0);
            v.assets = Json.str(ai, "id", v.assets);
            v.assetIndexTotalSize = (long) Json.num(ai, "totalSize", 0);
        }
        Map<String, Object> jv = Json.obj(root, "javaVersion");
        if (jv != null) {
            v.javaComponent = Json.str(jv, "component", v.javaComponent);
            v.javaMajor = (int) Json.num(jv, "majorVersion", v.javaMajor);
        }
        Map<String, Object> dl = Json.obj(root, "downloads");
        if (dl != null) {
            Map<String, Object> client = Json.obj(dl, "client");
            if (client != null) {
                v.clientUrl = Json.str(client, "url", null);
                v.clientSha1 = Json.str(client, "sha1", null);
                v.clientSize = (long) Json.num(client, "size", 0);
            }
        }
        List<Object> libs = Json.arr(root, "libraries");
        if (libs != null) {
            for (Object o : libs) {
                if (o instanceof Map<?, ?> lm) {
                    v.libraries.addAll(parseLibs((Map<String, Object>) lm));
                }
            }
        }
        Map<String, Object> args = Json.obj(root, "arguments");
        if (args != null) {
            readArgs(Json.arr(args, "game"), v.gameArgs);
            readArgs(Json.arr(args, "jvm"), v.jvmArgs);
        } else {
            String old = Json.str(root, "minecraftArguments", null);
            if (old != null) {
                for (String t : old.split("\\s+")) if (!t.isBlank()) v.gameArgs.add(t);
            }
        }
        return v;
    }

    /**
     * Старые версии кладут классы и natives в одну библиотеку.
     * Оба файла нужны: классы в classpath, natives — рядом с игрой.
     */
    @SuppressWarnings("unchecked")
    private static java.util.List<Lib> parseLibs(Map<String, Object> m) {
        java.util.List<Lib> out = new java.util.ArrayList<>();
        String name = Json.str(m, "name", null);
        if (name == null) return out;
        boolean allow = rulesAllow(Json.arr(m, "rules")) && Json.bool(m, "client", true);
        Map<String, Object> dl = Json.obj(m, "downloads");
        Map<String, Object> art = dl == null ? null : Json.obj(dl, "artifact");
        String nativeKey = null;
        Map<String, Object> nat = null;
        Map<String, Object> natives = Json.obj(m, "natives");
        if (natives != null && natives.containsKey(currentOs())) {
            nativeKey = String.valueOf(natives.get(currentOs())).replace("${arch}", is64() ? "64" : "32");
            Map<String, Object> cls = dl == null ? null : Json.obj(dl, "classifiers");
            nat = cls == null ? null : Json.obj(cls, nativeKey);
        }
        if (art != null || nativeKey == null) {
            Lib lib = baseLib(name, m, allow);
            if (art != null) fillDownload(lib, art, null);
            if (lib.path == null) lib.path = Lib.mavenPath(name, null);
            out.add(lib);
        }
        if (nativeKey != null) {
            Lib lib = baseLib(name, m, allow);
            lib.nativeClassifier = nativeKey;
            if (nat != null) fillDownload(lib, nat, nativeKey);
            if (lib.path == null) lib.path = Lib.mavenPath(name, nativeKey);
            out.add(lib);
        }
        return out;
    }

    private static Lib baseLib(String name, Map<String, Object> m, boolean allow) {
        Lib lib = new Lib();
        lib.name = name;
        lib.url = Json.str(m, "url", lib.url);
        lib.download = allow;
        return lib;
    }

    private static void fillDownload(Lib lib, Map<String, Object> art, String classifier) {
        lib.path = Json.str(art, "path", Lib.mavenPath(lib.name, classifier));
        lib.sha1 = Json.str(art, "sha1", null);
        lib.size = (long) Json.num(art, "size", 0);
        String u = Json.str(art, "url", null);
        if (u != null && u.startsWith("http")) lib.artifactUrl = u;
    }

    @SuppressWarnings("unchecked")
    private static boolean rulesAllow(List<Object> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allow = false;
        for (Object o : rules) {
            if (!(o instanceof Map<?, ?> rm)) continue;
            Map<String, Object> rule = (Map<String, Object>) rm;
            if (osMatches(Json.obj(rule, "os"), Json.obj(rule, "features"))) {
                allow = "allow".equals(Json.str(rule, "action", "allow"));
            }
        }
        return allow;
    }

    private static boolean osMatches(Map<String, Object> os, Map<String, Object> features) {
        if (features != null && !features.isEmpty()) return false;
        if (os == null || os.isEmpty()) return true;
        String name = Json.str(os, "name", null);
        if (name != null && !name.isBlank() && !name.equalsIgnoreCase(currentOs())) return false;
        String arch = Json.str(os, "arch", null);
        if (arch != null && !arch.isBlank()) {
            if (arch.equals("x86") && is64()) return false;
            if (arch.equals("x64") && !is64()) return false;
        }
        String ver = Json.str(os, "version", null);
        if (ver != null && !ver.isBlank()) {
            try {
                if (!System.getProperty("os.version", "").matches(ver)) return false;
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void readArgs(List<Object> list, List<String> out) {
        if (list == null) return;
        for (Object o : list) {
            if (o instanceof String s) {
                out.add(s);
            } else if (o instanceof Map<?, ?> mm) {
                Map<String, Object> m = (Map<String, Object>) mm;
                if (!rulesAllow(Json.arr(m, "rules"))) continue;
                Object value = m.get("value");
                if (value instanceof String s) out.add(s);
                else if (value instanceof List<?> l) {
                    for (Object x : l) if (x instanceof String s) out.add(s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ merge

    /** Профиль надстройки (Fabric/Forge) поверх vanilla. */
    public static VersionJson merge(VersionJson child, VersionJson parent) {
        if (child.inheritsFrom == null || parent == null) return child;
        VersionJson m = new VersionJson();
        m.id = child.id;
        m.type = child.type;
        m.mainClass = child.mainClass != null ? child.mainClass : parent.mainClass;
        m.assets = child.assetIndexUrl != null ? child.assets : parent.assets;
        m.assetIndexUrl = child.assetIndexUrl != null ? child.assetIndexUrl : parent.assetIndexUrl;
        m.assetIndexSha1 = child.assetIndexSha1 != null ? child.assetIndexSha1 : parent.assetIndexSha1;
        m.assetIndexSize = child.assetIndexSize > 0 ? child.assetIndexSize : parent.assetIndexSize;
        m.assetIndexTotalSize = child.assetIndexTotalSize > 0 ? child.assetIndexTotalSize
                : parent.assetIndexTotalSize;
        m.javaMajor = child.rawHasJava() ? child.javaMajor : parent.javaMajor;
        m.javaComponent = parent.javaComponent;
        m.clientUrl = child.clientUrl != null ? child.clientUrl : parent.clientUrl;
        m.clientSha1 = child.clientSha1 != null ? child.clientSha1 : parent.clientSha1;
        m.clientSize = child.clientSize > 0 ? child.clientSize : parent.clientSize;
        m.libraries.addAll(child.libraries);
        for (Lib p : parent.libraries) {
            boolean dup = false;
            for (Lib c : m.libraries) {
                if (c.name != null && c.name.equals(p.name)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) m.libraries.add(p);
        }
        m.gameArgs.addAll(child.gameArgs.isEmpty() ? parent.gameArgs : child.gameArgs);
        m.jvmArgs.addAll(!child.jvmArgs.isEmpty() ? child.jvmArgs : parent.jvmArgs);
        return m;
    }

    private boolean rawHasJava() {
        return javaComponent != null || javaMajor != 17;
    }

    // ------------------------------------------------------------------ misc

    public static boolean is64() {
        String model = System.getProperty("sun.arch.data.model", "64");
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return model.contains("64") || arch.contains("64");
    }

    public static String currentOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    public List<Lib> classpathLibraries() {
        List<Lib> out = new ArrayList<>();
        for (Lib l : libraries) {
            if (l.download && !l.isNative()) out.add(l);
        }
        return out;
    }

    public List<Lib> nativeLibraries() {
        List<Lib> out = new ArrayList<>();
        for (Lib l : libraries) {
            if (l.download && l.isNative()) out.add(l);
        }
        return out;
    }

    public String describe() {
        return "VersionJson[" + id + ", main=" + mainClass + ", java>=" + javaMajor + ", libs=" + libraries.size()
                + ", assets=" + assets + "]";
    }

    /** Картина профиля для диагностики и Developer Mode. */
    public Map<String, Object> describeMap() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("inheritsFrom", inheritsFrom);
        m.put("mainClass", mainClass);
        m.put("assets", assets);
        m.put("assetIndexUrl", assetIndexUrl);
        m.put("assetIndexSize", assetIndexSize);
        m.put("assetIndexTotalSize", assetIndexTotalSize);
        m.put("javaMajor", javaMajor);
        m.put("javaComponent", javaComponent);
        m.put("clientUrl", clientUrl);
        m.put("clientSha1", clientSha1);
        m.put("clientSize", clientSize);
        m.put("libraries", libraries.size());
        m.put("downloadableLibraries", classpathLibraries().size() + nativeLibraries().size());
        m.put("natives", nativeLibraries().size());
        m.put("gameArgs", gameArgs.size());
        m.put("jvmArgs", jvmArgs.size());
        return m;
    }
}
