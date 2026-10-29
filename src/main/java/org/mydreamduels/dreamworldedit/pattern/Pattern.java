package org.mydreamduels.dreamworldedit.pattern;

import org.bukkit.block.data.BlockData;

/** What block to put at a position. Must be thread-safe (jobs run in parallel on Folia). */
@FunctionalInterface
public interface Pattern {
    BlockData apply(int x, int y, int z);
}
