package ru.cw.launcher.core;

import ru.cw.launcher.model.ProgressListener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Реальный прогресс операции: название, процент (если известен размер), скорость,
 * полученный объём, ETA. Если размер неизвестен — indeterminate, процент не выдумывается.
 */
public class Progress implements ProgressListener {

    private volatile String stage = "";
    private volatile String text = "";
    private final AtomicLong done = new AtomicLong();
    private final AtomicLong total = new AtomicLong(-1);
    private final AtomicLong startedAt = new AtomicLong(System.currentTimeMillis());
    private volatile long lastSampleTime = System.currentTimeMillis();
    private volatile long lastSampleBytes = 0;
    private volatile double bytesPerSecond = 0;

    @Override
    public void stage(String title) {
        synchronized (this) {
            this.stage = title == null ? "" : title;
            this.done.set(0);
            this.total.set(-1);
            resetRate(System.currentTimeMillis(), 0);
        }
    }

    @Override
    public void progress(long d, long t, String label) {
        synchronized (this) {
            long now = System.currentTimeMillis();
            long nextDone = Math.max(0, d);
            long nextTotal = t > 0 ? t : -1;
            long prevDone = done.get();
            long prevTotal = total.get();
            // Параллельные потоки иногда присылают старое значение. Его не учитываем:
            // иначе скорость падает до нуля и «осталось» раздувается до минут.
            if (nextTotal == prevTotal && nextDone < prevDone) return;
            boolean switched = prevTotal > 0 && nextTotal > 0 && nextTotal != prevTotal;
            if (switched || nextDone < prevDone) {
                resetRate(now, nextDone);
            }
            done.set(nextDone);
            total.set(nextTotal);
            if (label != null) this.text = label;
            sample(now);
        }
    }

    private void resetRate(long now, long bytes) {
        startedAt.set(now);
        lastSampleTime = now;
        lastSampleBytes = bytes;
        bytesPerSecond = 0;
    }

    /** Скорость по последней секунде. Если счётчик уже большой, а окно ещё пустое — по всему файлу. */
    private void sample(long now) {
        long cur = done.get();
        long dt = now - lastSampleTime;
        if (dt >= 250) {
            long delta = cur - lastSampleBytes;
            if (delta >= 0) {
                double instant = delta * 1000.0 / dt;
                bytesPerSecond = bytesPerSecond <= 0 ? instant : bytesPerSecond * 0.35 + instant * 0.65;
            }
            lastSampleBytes = cur;
            lastSampleTime = now;
        }
        long elapsed = now - startedAt.get();
        if (elapsed >= 400 && cur > 0) {
            double average = cur * 1000.0 / elapsed;
            if (bytesPerSecond < average * 0.5) bytesPerSecond = average;
        }
    }

    public String stage() {
        return stage;
    }

    public String text() {
        return text;
    }

    public long done() {
        return done.get();
    }

    public long total() {
        return total.get();
    }

    public boolean indeterminate() {
        return total.get() <= 0;
    }

    /** -1 если размер неизвестен. */
    public int percent() {
        long t = total.get();
        long d = done.get();
        if (t <= 0 || d <= 0) return -1;
        return (int) Math.min(100, d * 100 / t);
    }

    public double bytesPerSecond() {
        return bytesPerSecond;
    }

    public long etaSeconds() {
        long t = total.get();
        long d = done.get();
        if (t <= 0) return -1;
        if (d >= t) return 0;
        double bps = speed();
        if (bps <= 1) return -1;
        return (long) ((t - d) / bps);
    }

    /** Та же скорость, что уходит в подпись и в расчёт оставшегося времени. */
    public double speed() {
        long d = done.get();
        long elapsed = System.currentTimeMillis() - startedAt.get();
        double bps = bytesPerSecond;
        if (elapsed >= 400 && d > 0) {
            double average = d * 1000.0 / elapsed;
            if (bps < average * 0.5) bps = average;
        }
        return bps;
    }

    public String etaText() {
        long s = etaSeconds();
        if (s < 0) return "—";
        if (s < 60) return s + " с";
        if (s < 3600) return (s / 60) + " мин " + (s % 60) + " с";
        return (s / 3600) + " ч " + ((s % 3600) / 60) + " мин";
    }

    @Override
    public String toString() {
        return stage + " " + (indeterminate() ? ru.cw.launcher.util.Utils.humanSize(done.get())
                : percent() + "% (" + ru.cw.launcher.util.Utils.humanSize(done.get()) + "/"
                        + ru.cw.launcher.util.Utils.humanSize(total.get()) + ")");
    }
}
