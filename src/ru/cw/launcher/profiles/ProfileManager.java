package ru.cw.launcher.profiles;

import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ProfileManager: избранное игровых профилей (раздел 1 доп. функций).
 * Хранение — profiles.json рядом с config.json.
 */
public final class ProfileManager {

    private final List<LauncherProfile> profiles = new ArrayList<>();
    private String activeId;

    public ProfileManager() {
        load();
    }

    public List<LauncherProfile> all() {
        return List.copyOf(profiles);
    }

    public Optional<LauncherProfile> byId(String id) {
        return profiles.stream().filter(p -> p.id.equals(id)).findFirst();
    }

    public LauncherProfile active() {
        return byId(activeId).orElse(profiles.isEmpty() ? null : profiles.get(0));
    }

    public String activeId() {
        return activeId;
    }

    public void setActive(String id) {
        if (byId(id).isPresent()) {
            activeId = id;
            save();
            Log.info("Активен профиль " + byId(id).get().name);
        }
    }

    public LauncherProfile create(String name) {
        LauncherProfile p = new LauncherProfile();
        p.name = name;
        profiles.add(p);
        activeId = p.id;
        save();
        Log.info("Создан профиль " + name);
        return p;
    }

    public void rename(String id, String name) {
        byId(id).ifPresent(p -> {
            p.name = name;
            save();
            Log.info("Профиль переименован в " + name);
        });
    }

    /**
     * Удаление профиля. Данные экземпляра (миры, скриншоты, ресурспаки) НЕ удаляются —
     * за это отвечает отдельная операция с подтверждением.
     */
    public boolean delete(String id, boolean deleteInstanceFiles) {
        Optional<LauncherProfile> p = byId(id);
        if (p.isEmpty()) return false;
        profiles.remove(p.get());
        if (id.equals(activeId)) activeId = profiles.isEmpty() ? null : profiles.get(0).id;
        save();
        if (deleteInstanceFiles) {
            Path dir = Paths.instance(p.get().instanceId);
            Utils.deleteRecursive(dir);
            Log.warn("Удалён каталог экземпляра " + dir);
        } else {
            Log.info("Профиль удалён, файлы экземпляра сохранены");
        }
        return true;
    }

    public void update(LauncherProfile p) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(p.id)) {
                profiles.set(i, p);
                break;
            }
        }
        save();
    }

    public int count() {
        return profiles.size();
    }

    private void load() {
        try {
            if (!Files.exists(Paths.profilesFile())) {
                LauncherProfile p = new LauncherProfile();
                profiles.add(p);
                activeId = p.id;
                save();
                return;
            }
            Map<String, Object> root = Json.parseObject(Utils.readString(Paths.profilesFile()));
            activeId = Json.str(root, "active", null);
            List<Object> list = Json.arr(root, "profiles");
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mm = (Map<String, Object>) m;
                        profiles.add(LauncherProfile.fromMap(mm));
                    }
                }
            }
            if (profiles.isEmpty()) {
                profiles.add(new LauncherProfile());
                activeId = profiles.get(0).id;
            }
            if (activeId == null || byId(activeId).isEmpty()) activeId = profiles.get(0).id;
            Log.info("Загружено профилей: " + profiles.size());
        } catch (Exception e) {
            Log.error("profiles.json повреждён — создан профиль по умолчанию: " + Log.reason(e), e);
            profiles.add(new LauncherProfile());
            activeId = profiles.get(0).id;
            save();
        }
    }

    public void save() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 1);
            root.put("active", activeId);
            List<Object> list = new ArrayList<>();
            for (LauncherProfile p : profiles) list.add(p.toMap());
            root.put("profiles", list);
            Utils.writeString(Paths.profilesFile(), Json.write(root));
        } catch (IOException e) {
            Log.error("Не удалось сохранить profiles.json: " + Log.reason(e), e);
        }
    }
}
