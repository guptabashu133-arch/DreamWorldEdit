package org.mydreamduels.dreamworldedit.region;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.util.BlockVec;

/** A set of block positions in one world. min()/max() are its bounding box (inclusive). */
public interface Region {

    World world();

    BlockVec min();

    BlockVec max();

    boolean contains(int x, int y, int z);

    /** Exact number of blocks (may iterate for odd shapes). */
    long volume();

    String describe();

    default int width() {
        return max().x() - min().x() + 1;
    }

    default int height() {
        return max().y() - min().y() + 1;
    }

    default int length() {
        return max().z() - min().z() + 1;
    }

    default long boxVolume() {
        return (long) width() * height() * length();
    }

    default BlockVec center() {
        return new BlockVec((min().x() + max().x()) >> 1, (min().y() + max().y()) >> 1, (min().z() + max().z()) >> 1);
    }

    /** Cheap reject for chunks that can't contain any block of this region. */
    default boolean touchesChunk(int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        return x0 + 15 >= min().x() && x0 <= max().x() && z0 + 15 >= min().z() && z0 <= max().z();
    }

    default long countVolume() {
        long n = 0;
        BlockVec a = min(), b = max();
        for (int x = a.x(); x <= b.x(); x++)
            for (int z = a.z(); z <= b.z(); z++)
                for (int y = a.y(); y <= b.y(); y++)
                    if (contains(x, y, z)) n++;
        return n;
    }
}
