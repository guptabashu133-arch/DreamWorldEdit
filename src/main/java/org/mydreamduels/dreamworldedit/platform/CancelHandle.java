package org.mydreamduels.dreamworldedit.platform;

/** A scheduled task that can be cancelled, independent of which scheduler created it. */
@FunctionalInterface
public interface CancelHandle {
    void cancel();
}
