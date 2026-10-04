package ru.cw.launcher.net;

import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Параллельная загрузка набора файлов с общим счётчиком байт.
 * Уже лежащие файлы нужного размера пропускаются.
 */
public final class Downloads {

    public record Item(String url, Path dest, String sha1, long size) {
    }

    private Downloads() {
    }

    /**
     * @return сколько файлов не удалось скачать (отмена пробрасывается исключением)
     */
    public static int fetch(List<Item> items, ProgressListener pl, String label)
            throws IOException, InterruptedException {
        List<Item> pending = new ArrayList<>();
        long total = 0;
        for (Item it : items) {
            if (it.dest != null && Files.isRegularFile(it.dest)) {
                long sz = Files.size(it.dest);
                if (sz > 0 && (it.size <= 0 || sz == it.size)) continue;
            }
            pending.add(it);
            total += Math.max(it.size, 0);
        }
        if (pending.isEmpty()) {
            if (pl != null) pl.progress(1, 1, label);
            return 0;
        }
        for (Item it : pending) {
            if (it.dest != null && it.dest.getParent() != null) {
                Files.createDirectories(it.dest.getParent());
            }
        }
        long grand = total > 0 ? total : pending.size();
        AtomicLong got = new AtomicLong();
        AtomicLong published = new AtomicLong();
        AtomicInteger failed = new AtomicInteger();
        AtomicReference<Exception> cancel = new AtomicReference<>();
        int threads = NetPolicy.threads(pending.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "cw-download");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Item it : pending) {
                futures.add(pool.submit(() -> one(it, pl, label, grand, got, published, failed, cancel)));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    if (cancel.get() == null) {
                        failed.incrementAndGet();
                        Log.warn("Загрузка прервана ожиданием: " + Log.reason(e));
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
        if (pl != null) pl.checkCancel();
        Exception stopped = cancel.get();
        if (stopped instanceof IOException io) throw io;
        if (stopped instanceof InterruptedException ie) throw ie;
        if (stopped != null) throw new IOException(stopped.getMessage(), stopped);
        if (pl != null) pl.progress(grand, grand, label);
        return failed.get();
    }

    private static void one(Item it, ProgressListener pl, String label, long grand,
                            AtomicLong got, AtomicLong published, AtomicInteger failed,
                            AtomicReference<Exception> cancel) {
        if (cancel.get() != null || (pl != null && pl.isCancelled())) return;
        AtomicLong local = new AtomicLong();
        long[] lastUi = {0L};
        try {
            Http.download(it.url, it.dest, it.sha1, it.size, (done, fileTotal) -> {
                long prev = local.getAndSet(Math.max(0, done));
                long add = Math.max(0, done - prev);
                long sum = got.addAndGet(add);
                long now = System.currentTimeMillis();
                if (pl != null && now - lastUi[0] > 100 && sum >= published.get()) {
                    lastUi[0] = now;
                    published.set(sum);
                    pl.progress(Math.min(sum, grand), grand, label);
                }
            }, () -> cancel.get() != null || (pl != null && pl.isCancelled()));
            long prev = local.get();
            long size = it.size > 0 ? it.size : (it.dest != null && Files.isRegularFile(it.dest) ? Files.size(it.dest) : prev);
            if (size > prev) got.addAndGet(size - prev);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Отменено") || msg.contains("Cancelled") || (pl != null && pl.isCancelled())) {
                cancel.compareAndSet(null, e);
                return;
            }
            failed.incrementAndGet();
            Log.warn("Не скачан " + (it.dest == null ? it.url : it.dest.getFileName()) + ": " + Log.reason(e));
        }
    }
}
