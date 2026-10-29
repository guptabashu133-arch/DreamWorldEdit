package org.mydreamduels.dreamworldedit.edit;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * What a ChunkOp sees: one chunk (optionally a vertical slab of it), on
 * the thread that owns it. Writes are recorded for undo and done without
 * physics, like WorldEdit.
 */
public final class ChunkContext {

    public final World world;
    public final int cx, cz;
    /** Vertical bounds of this job (inclusive). */
    public final int minY, maxY;
    private final boolean record;
    private final ChangeList changes = new ChangeList();
    private final long budget;
    private long changed;

    ChunkContext(World world, int cx, int cz, int minY, int maxY, boolean record, long budget) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.minY = minY;
        this.maxY = maxY;
        this.record = record;
        this.budget = budget;
    }

    public int baseX() {
        return cx << 4;
    }

    public int baseZ() {
        return cz << 4;
    }

    public boolean inChunk(int x, int z) {
        return (x >> 4) == cx && (z >> 4) == cz;
    }

    public BlockData get(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getBlockData();
    }

    /** Sets a block. Returns true if it actually changed. */
    public boolean set(int x, int y, int z, BlockData data) {
        if (data == null || y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        if (changed >= budget) return false;
        Block b = world.getBlockAt(x, y, z);
        BlockData old = b.getBlockData();
        if (old.equals(data)) return false;
        if (record) changes.add(x, y, z, old, data);
        b.setBlockData(data, false);
        changed++;
        return true;
    }

    public long changed() {
        return changed;
    }

    ChangeList changes() {
        return changes;
    }
}
