package org.mydreamduels.dreamworldedit.region;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.util.BlockVec;

public record CuboidRegion(World world, BlockVec min, BlockVec max) implements Region {

    public static CuboidRegion of(World world, BlockVec a, BlockVec b) {
        return new CuboidRegion(world, BlockVec.min(a, b), BlockVec.max(a, b));
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return x >= min.x() && x <= max.x() && y >= min.y() && y <= max.y() && z >= min.z() && z <= max.z();
    }

    @Override
    public long volume() {
        return boxVolume();
    }

    @Override
    public String describe() {
        return "cuboid " + min + " -> " + max;
    }
}
