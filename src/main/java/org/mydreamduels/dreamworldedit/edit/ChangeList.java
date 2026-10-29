package org.mydreamduels.dreamworldedit.edit;

import org.bukkit.block.data.BlockData;

import java.util.Arrays;

/** Changes made inside one chunk job: positions + before/after block data. Single-threaded. */
public final class ChangeList {

    private int[] xyz = new int[48];
    private BlockData[] before = new BlockData[16];
    private BlockData[] after = new BlockData[16];
    private int size;

    public void add(int x, int y, int z, BlockData old, BlockData now) {
        if (size == before.length) {
            int n = size * 2;
            xyz = Arrays.copyOf(xyz, n * 3);
            before = Arrays.copyOf(before, n);
            after = Arrays.copyOf(after, n);
        }
        xyz[size * 3] = x;
        xyz[size * 3 + 1] = y;
        xyz[size * 3 + 2] = z;
        before[size] = old;
        after[size] = now;
        size++;
    }

    public int size() {
        return size;
    }

    public int x(int i) { return xyz[i * 3]; }
    public int y(int i) { return xyz[i * 3 + 1]; }
    public int z(int i) { return xyz[i * 3 + 2]; }
    public BlockData before(int i) { return before[i]; }
    public BlockData after(int i) { return after[i]; }
}
