package ru.cw.launcher.core;

import ru.cw.launcher.model.GameState;
import ru.cw.launcher.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Последовательность именованных задач с реальным прогрессом, отменой и состоянием.
 * Используется установкой Minecraft, Fabric, сборки модов, восстановлением и диагностикой.
 */
public class Operation {

    /** Тело задачи: бросает любые исключения, они идут в обработчик ошибки. */
    public interface Body {
        void run() throws Exception;
    }

    public static class Task {
        public final String title;
        public final double weight;
        public final Body body;
        public volatile boolean done;
        public volatile boolean failed;
        public volatile String error;

        public Task(String title, double weight, Body body) {
            this.title = title;
            this.weight = Math.max(0.1, weight);
            this.body = body;
        }
    }

    private final String name;
    private final State state;
    private final List<Task> tasks = new ArrayList<>();
    private final Progress progress = new Progress();
    private final Cancel cancel = new Cancel();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile double totalWeight;
    private volatile double completedWeight;
    private volatile double currentWeight;
    private GameState runningState = GameState.INSTALLING;
    private GameState finishState = GameState.READY;
    private Runnable onSuccess;
    private Consumer<Throwable> onError;

    public Operation(String name, State state) {
        this.name = name;
        this.state = state;
    }

    public Operation add(String title, double weight, Body body) {
        tasks.add(new Task(title, weight, body));
        return this;
    }

    public Operation runningAs(GameState gs) {
        this.runningState = gs;
        return this;
    }

    public Operation finishAs(GameState gs) {
        this.finishState = gs;
        return this;
    }

    public Operation onSuccess(Runnable r) {
        this.onSuccess = r;
        return this;
    }

    public Operation onError(Consumer<Throwable> c) {
        this.onError = c;
        return this;
    }

    public String name() {
        return name;
    }

    public Progress progress() {
        return progress;
    }

    public Cancel cancel() {
        return cancel;
    }

    public List<Task> tasks() {
        return tasks;
    }

    /** Итоговый процент операции по весам задач; -1 если оценить нельзя. */
    public int percent() {
        if (totalWeight <= 0) return progress.percent();
        double frac = progress.indeterminate() ? 0 : Math.max(0, Math.min(1, progress.percent() / 100.0));
        double done = completedWeight + currentWeight * frac;
        return (int) Math.max(0, Math.min(100, done * 100 / totalWeight));
    }

    /** Индекс текущей задачи (0-based) и её название. */
    public String currentTask() {
        return progress.stage();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void cancelOperation() {
        cancel.cancel();
    }

    /**
     * Выполнение в текущем потоке (запускать из worker-потока, не из EDT).
     *
     * @return true, если все задачи выполнены успешно
     */
    public boolean run() {
        if (!running.compareAndSet(false, true)) {
            Log.warn("Операция «" + name + "» уже выполняется — повторный запуск отклонён");
            return false;
        }
        double totalWeight = 0;
        for (Task t : tasks) totalWeight += t.weight;
        this.totalWeight = totalWeight;
        this.completedWeight = 0;
        double acc = 0;
        cancel.reset();
        state.set(runningState, name);
        Log.info("Операция «" + name + "»: задач " + tasks.size());
        try {
            for (Task t : tasks) {
                cancel.check();
                currentWeight = t.weight;
                progress.stage(t.title);
                Log.stage(name, t.title);
                try {
                    t.body.run();
                    t.done = true;
                } catch (Exception e) {
                    t.failed = true;
                    t.error = Log.reason(e);
                    throw e;
                }
                acc += t.weight;
                completedWeight = acc;
            }
            cancel.check();
            if (onSuccess != null) onSuccess.run();
            state.set(finishState, name + ": завершено");
            Log.info("Операция «" + name + "» завершена успешно");
            return true;
        } catch (Throwable e) {
            if (Cancel.isCancel(e)) {
                Log.warn("Операция «" + name + "» отменена пользователем");
                state.set(GameState.NOT_INSTALLED, "Отменено");
            } else {
                Log.error("Операция «" + name + "» провалилась: " + Log.reason(e), e);
                state.set(GameState.ERROR, friendly(e));
                if (onError != null) onError.accept(e);
            }
            return false;
        } finally {
            running.set(false);
        }
    }

    /** Человекочитаемая причина ошибки. */
    public static String friendly(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.nio.file.AccessDeniedException) {
                return "Нет прав записать файлы игры. Папка в Program Files закрыта. Выберите другой диск.";
            }
        }
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.io.IOException || c instanceof IllegalStateException) {
                String m = c.getMessage();
                if (m != null && !m.isBlank()) return m;
            }
        }
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
