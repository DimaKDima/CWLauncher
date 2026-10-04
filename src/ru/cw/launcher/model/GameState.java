package ru.cw.launcher.model;

/**
 * Единая модель состояний лаунчера (раздел 28 ТЗ).
 * UI обязан отображать только то состояние, которое реально достигнуто.
 */
public enum GameState {

    NOT_INSTALLED("СКАЧАТЬ", Kind.NEUTRAL),
    CHECKING("ПРОВЕРКА...", Kind.INFO),
    DOWNLOADING("ОТМЕНИТЬ", Kind.INFO),
    INSTALLING("УСТАНОВКА...", Kind.INFO),
    UPDATING("ОБНОВЛЕНИЕ...", Kind.INFO),
    REPAIRING("ВОССТАНОВЛЕНИЕ...", Kind.WARN),
    UPDATE_AVAILABLE("ОБНОВИТЬ", Kind.WARN),
    READY("ЗАПУСТИТЬ", Kind.OK),
    STARTING("ЗАПУСКАЕТСЯ", Kind.INFO),
    RUNNING("ЗАПУЩЕНО", Kind.INFO),
    ERROR("ИСПРАВИТЬ", Kind.ERROR),
    OFFLINE("ЗАПУСТИТЬ (ОФЛАЙН)", Kind.WARN);

    public enum Kind {
        OK, INFO, WARN, ERROR, NEUTRAL
    }

    public final String buttonLabel;
    public final Kind kind;

    GameState(String buttonLabel, Kind kind) {
        this.buttonLabel = buttonLabel;
        this.kind = kind;
    }

    public boolean isBusy() {
        return this == CHECKING || this == DOWNLOADING || this == INSTALLING || this == UPDATING
                || this == REPAIRING;
    }

    /** Состояния, в которых запуск разрешён. */
    public boolean canLaunch() {
        return this == READY || this == OFFLINE;
    }

    public boolean isOk() {
        return this == READY;
    }

    /** Занятые состояния блокируют основную кнопку (кроме отмены). */
    public boolean blocksButton() {
        return this == INSTALLING || this == CHECKING || this == REPAIRING;
    }
}
