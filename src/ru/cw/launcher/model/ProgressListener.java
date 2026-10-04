package ru.cw.launcher.model;

/**
 * Единый слушатель прогресса для всех долгих операций.
 * Реализации должны быть потокобезопасными и не выполнять тяжёлую работу в вызывающем потоке.
 */
public interface ProgressListener {

    ProgressListener NOOP = new ProgressListener() {
        @Override
        public void stage(String title) {
        }

        @Override
        public void progress(long done, long total, String text) {
        }
    };

    /** Смена текущего этапа (например "Установка Minecraft"). */
    void stage(String title);

    /**
     * Прогресс этапа.
     *
     * @param done  выполнено байт/единиц
     * @param total всего (<= 0 — неизвестно, индетерминированный прогресс)
     * @param text  человекочитаемая подпись
     */
    void progress(long done, long total, String text);

    default void checkCancel() throws java.io.IOException {
    }

    /** Признак отмены для HTTP-загрузок. */
    default boolean isCancelled() {
        return false;
    }
}
