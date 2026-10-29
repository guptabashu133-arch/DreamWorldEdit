package org.mydreamduels.dreamworldedit.util;

import org.bukkit.Location;

/** Immutable integer block position. */
public record BlockVec(int x, int y, int z) {

    public static BlockVec of(Location loc) {
        return new BlockVec(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public BlockVec add(int dx, int dy, int dz) {
        return new BlockVec(x + dx, y + dy, z + dz);
    }

    public BlockVec add(BlockVec o) {
        return new BlockVec(x + o.x, y + o.y, z + o.z);
    }

    public BlockVec subtract(BlockVec o) {
        return new BlockVec(x - o.x, y - o.y, z - o.z);
    }

    public BlockVec multiply(int f) {
        return new BlockVec(x * f, y * f, z * f);
    }

    public static BlockVec min(BlockVec a, BlockVec b) {
        return new BlockVec(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    public static BlockVec max(BlockVec a, BlockVec b) {
        return new BlockVec(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }
}
