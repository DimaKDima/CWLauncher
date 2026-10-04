package ru.cw.launcher.core;

import ru.cw.launcher.model.GameState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Наблюдаемое состояние приложения. Все изменения публикуются в EDT,
 * потому что слушатели — это UI.
 */
public final class State {

    public interface Listener {
        void onChanged(GameState state, String detail);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile GameState state = GameState.NOT_INSTALLED;
    private volatile String detail = "";

    public void add(Listener l) {
        listeners.add(l);
    }

    public GameState get() {
        return state;
    }

    public String detail() {
        return detail;
    }

    public void set(GameState next) {
        set(next, "");
    }

    public void set(GameState next, String detail) {
        this.state = next;
        this.detail = detail == null ? "" : detail;
        var evt = new Object[]{next, this.detail};
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (Listener l : listeners) {
                try {
                    l.onChanged((GameState) evt[0], (String) evt[1]);
                } catch (Exception ignored) {
                    // UI-слушатель не должен ронять приложение
                }
            }
        });
    }

    /** Разрешён ли запуск прямо сейчас. */
    public boolean canLaunch() {
        return state == GameState.READY || state == GameState.OFFLINE;
    }
}
