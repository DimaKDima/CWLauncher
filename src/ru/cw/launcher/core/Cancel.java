package ru.cw.launcher.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Токен отмены длительной операции (раздел 19 доп. требований). */
public class Cancel {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public void reset() {
        cancelled.set(false);
    }

    public void check() throws CancellationException {
        if (cancelled.get()) throw new CancellationException("Операция отменена пользователем");
    }

    /** Исключение "отменено" — не является ошибкой для UI. */
    public static boolean isCancel(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof CancellationException) return true;
            String m = c.getMessage();
            if (m != null && (m.contains("Отменено") || m.contains("Cancelled"))) return true;
            if (c.getClass().getName().contains("Cancel")) return true;
        }
        return false;
    }
}
