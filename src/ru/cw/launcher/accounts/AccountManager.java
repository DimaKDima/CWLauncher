package ru.cw.launcher.accounts;

import ru.cw.launcher.security.Secrets;
import ru.cw.launcher.util.Json;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;
import ru.cw.launcher.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AccountManager: хранение Minecraft-аккаунтов (Offline / Ely.by) и защита токенов.
 * Пароли не сохраняются вообще, токены — только в DPAPI-файле secret.dat.
 */
public final class AccountManager {

    private final List<Account> accounts = new ArrayList<>();
    private String activeId;

    public AccountManager() {
        load();
    }

    public List<Account> all() {
        return List.copyOf(accounts);
    }

    public Optional<Account> byId(String id) {
        return accounts.stream().filter(a -> a.id.equals(id)).findFirst();
    }

    public Account active() {
        return byId(activeId).orElse(accounts.isEmpty() ? null : accounts.get(0));
    }

    public String activeId() {
        return activeId;
    }

    public void setActive(String id) {
        if (byId(id).isPresent()) {
            activeId = id;
            save();
            Log.info("Активен аккаунт " + id);
        }
    }

    public Account addOffline(String username) {
        String name = username == null ? "" : username.trim();
        if (!Account.validNick(name)) return null;
        Optional<Account> exists = accounts.stream()
                .filter(a -> a.type == Account.Type.OFFLINE && a.username.equalsIgnoreCase(name))
                .findFirst();
        if (exists.isPresent()) {
            setActive(exists.get().id);
            return exists.get();
        }
        Account a = new Account(Account.Type.OFFLINE, name);
        a.clientToken = java.util.UUID.randomUUID().toString();
        accounts.add(a);
        activeId = a.id;
        save();
        Log.info("Создан offline-аккаунт " + name + " (uuid " + a.uuid + ")");
        return a;
    }

    /** Новое имя оффлайн-аккаунта. Имя Ely.by задаётся на сайте. */
    public boolean renameOffline(String id, String username) {
        String name = username == null ? "" : username.trim();
        if (!Account.validNick(name)) return false;
        Account a = byId(id).orElse(null);
        if (a == null || a.type != Account.Type.OFFLINE) return false;
        boolean taken = accounts.stream().anyMatch(o -> !o.id.equals(a.id)
                && o.type == Account.Type.OFFLINE && o.username.equalsIgnoreCase(name));
        if (taken) return false;
        a.username = name;
        a.login = name;
        a.uuid = Account.offlineUuidOf(name).toString();
        save();
        Log.info("Оффлайн-аккаунт переименован в " + name);
        return true;
    }

    public Account addEly(String login, String username, String uuid) {
        Optional<Account> exists = accounts.stream()
                .filter(a -> a.type == Account.Type.ELY && a.username.equalsIgnoreCase(username))
                .findFirst();
        Account a = exists.orElseGet(Account::new);
        a.type = Account.Type.ELY;
        a.username = username;
        a.login = login;
        if (!Utils.isBlank(uuid)) a.uuid = uuid;
        if (Utils.isBlank(a.clientToken)) a.clientToken = java.util.UUID.randomUUID().toString();
        if (!exists.isPresent()) accounts.add(a);
        activeId = a.id;
        save();
        return a;
    }

    public void storeToken(Account a, String accessToken) {
        Secrets.put(a.secretKey(), accessToken);
    }

    public String tokenOf(Account a) {
        String t = Secrets.get(a.secretKey());
        return t == null ? "" : t;
    }

    public boolean remove(String id) {
        Optional<Account> a = byId(id);
        if (a.isEmpty()) return false;
        if (a.get().type == Account.Type.ELY) {
            ElyAuthManager.logout(a.get());
        } else {
            ElyAuth.logout(a.get().clientToken);
            Secrets.remove(a.get().secretKey());
        }
        accounts.remove(a.get());
        if (id.equals(activeId)) activeId = accounts.isEmpty() ? null : accounts.get(0).id;
        save();
        Log.info("Аккаунт удалён: " + a.get().username);
        return true;
    }

    public int count() {
        return accounts.size();
    }

    // ------------------------------------------------------------------ storage

    private void load() {
        try {
            if (!java.nio.file.Files.exists(Paths.accountsFile())) {
                activeId = null;
                Log.info("Аккаунтов нет: профиль создаётся вручную");
                return;
            }
            Map<String, Object> root = Json.parseObject(Utils.readString(Paths.accountsFile()));
            activeId = Json.str(root, "active", null);
            List<Object> list = Json.arr(root, "accounts");
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mm = (Map<String, Object>) m;
                        accounts.add(Account.fromMap(mm));
                    }
                }
            }
            if (accounts.isEmpty()) {
                activeId = null;
            } else if (activeId == null || byId(activeId).isEmpty()) {
                activeId = accounts.get(0).id;
            }
            Log.info("Загружено аккаунтов: " + accounts.size());
        } catch (Exception e) {
            Log.error("accounts.json повреждён — список очищен: " + Log.reason(e), e);
            accounts.clear();
            activeId = null;
            save();
        }
    }

    public void save() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 1);
            root.put("active", activeId);
            List<Object> list = new ArrayList<>();
            for (Account a : accounts) list.add(a.toMap());
            root.put("accounts", list);
            Utils.writeString(Paths.accountsFile(), Json.write(root));
        } catch (IOException e) {
            Log.error("Не удалось сохранить accounts.json: " + Log.reason(e), e);
        }
    }
}
