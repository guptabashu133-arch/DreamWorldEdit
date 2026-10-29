package org.mydreamduels.dreamworldedit.pattern;

import org.bukkit.block.data.BlockData;
import org.mydreamduels.dreamworldedit.edit.ChunkContext;

/** Which existing blocks an edit may touch. ctx is there for masks that look above/below. */
@FunctionalInterface
public interface Mask {
    boolean test(ChunkContext ctx, int x, int y, int z, BlockData current);

    Mask ALL = (ctx, x, y, z, d) -> true;
}
