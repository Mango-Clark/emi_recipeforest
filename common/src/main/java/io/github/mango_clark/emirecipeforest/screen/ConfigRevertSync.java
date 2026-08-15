package io.github.mango_clark.emirecipeforest.screen;

/** Keeps external configuration restoration ahead of EMI's widget rebuild. */
public final class ConfigRevertSync {
    private ConfigRevertSync() {
    }

    public static void restoreBeforeWidgetRefresh(Runnable restore) {
        restore.run();
    }
}
