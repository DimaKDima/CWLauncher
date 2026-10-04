package ru.cw.launcher.net;

import ru.cw.launcher.core.Settings;
import ru.cw.launcher.minecraft.GameTuning;
import ru.cw.launcher.util.Log;

/**
 * «Весь интернет на себя».
 * Пока открыт только лаунчер, его пакеты помечены как приоритетные и загрузки идут широким фронтом.
 * Как только запущен Minecraft, Windows режет весь трафик лаунчера до 8 КБ/с, а приоритет пакетов
 * переходит игре.
 */
public final class NetPolicy {

    private static final int GAME_CAP_BPS = 8 * 1024;

    private static volatile boolean leader;
    private static volatile boolean limit;
    private static volatile int mbit;
    private static volatile boolean proxy;
    private static volatile boolean gameOwns;
    private static volatile long gamePid;
    private static final java.util.Set<Long> gamePids = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean held;
    private static long nextNs;

    private NetPolicy() {
    }

    public static void apply(Settings cfg) {
        leader = cfg != null && cfg.internetLeader;
        limit = cfg != null && cfg.limitBandwidth && cfg.bandwidthMbit > 0;
        mbit = cfg == null ? 0 : Math.max(0, cfg.bandwidthMbit);
        proxy = cfg != null && cfg.useSystemProxy;
        reshape();
        Http.reconfigure();
    }

    /** Minecraft уже запущен: канал отдаётся ему, лаунчер остаётся на узкой полосе. */
    public static void focusGame(long pid) {
        if (pid > 0) gamePids.add(pid);
        gameOwns = !gamePids.isEmpty();
        gamePid = pid;
        reshape();
    }

    /** Одно окно игры закрыто. Канал возвращается лаунчеру, когда окон не осталось. */
    public static void gameClosed(long pid) {
        if (pid > 0) gamePids.remove(pid);
        gameOwns = !gamePids.isEmpty();
        if (!gameOwns) gamePid = 0;
        reshape();
    }

    /** Все окна закрыты: лаунчер снова забирает канал, если режим включён. */
    public static void focusLauncher() {
        gamePids.clear();
        gameOwns = false;
        gamePid = 0;
        reshape();
    }

    private static void reshape() {
        long self = ProcessHandle.current().pid();
        if (!leader) {
            if (held) {
                OsNet.set(self, 0, 0, true);
                for (long pid : gamePids) OsNet.set(pid, 0, 0, false);
                held = false;
            }
            GameTuning.priority(self, "Normal", false);
            for (long pid : gamePids) GameTuning.priority(pid, "Normal", false);
            return;
        }
        held = true;
        if (gameOwns) {
            OsNet.set(self, GAME_CAP_BPS, 0, true);
            GameTuning.priority(self, "BelowNormal", false);
            for (long pid : gamePids) {
                OsNet.set(pid, 0, 46, false);
                GameTuning.priority(pid, "High", false);
            }
            Log.info("Интернет отдан Minecraft, лаунчер ограничен до 8 КБ/с");
        } else {
            OsNet.set(self, 0, 46, true);
            GameTuning.priority(self, "High", false);
            Log.info("Интернет у лаунчера");
        }
    }

    public static boolean leader() {
        return leader;
    }

    public static boolean proxy() {
        return proxy;
    }

    public static int threads(int pending) {
        if (leader && gameOwns) return 1;
        int cap = leader ? 32 : 4;
        return Math.min(cap, Math.max(1, pending));
    }

    public static int bufferSize() {
        if (leader && gameOwns) return 16 * 1024;
        return leader ? 1024 * 1024 : 64 * 1024;
    }

    public static int attempts() {
        return leader && !gameOwns ? 6 : 3;
    }

    public static int readTimeoutSec() {
        return leader && !gameOwns ? 180 : 120;
    }

    /** Притормаживает чтение: лимит из настроек, а при запущенной игре — ещё сильнее. */
    public static void acquire(int bytes) {
        if (bytes <= 0) return;
        if (leader && gameOwns) {
            throttle(bytes, GAME_CAP_BPS);
            return;
        }
        if (!limit || mbit <= 0) return;
        throttle(bytes, Math.max(1024L, mbit * 1_000_000L / 8L));
    }

    private static void throttle(int bytes, long bps) {
        if (bps <= 0) return;
        synchronized (NetPolicy.class) {
            long now = System.nanoTime();
            long need = bytes * 1_000_000_000L / bps;
            long start = Math.max(now, nextNs);
            nextNs = start + need;
            long wait = start - now;
            if (wait <= 0) return;
            try {
                Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
