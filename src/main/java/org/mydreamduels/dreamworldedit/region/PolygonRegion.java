package org.mydreamduels.dreamworldedit.region;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.util.BlockVec;

import java.util.List;

/** 2D polygon (x/z points) extruded from minY to maxY - WorldEdit's "poly" selection. */
public record PolygonRegion(World world, List<int[]> points, int minY, int maxY) implements Region {

    @Override
    public BlockVec min() {
        int x = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (int[] p : points) {
            x = Math.min(x, p[0]);
            z = Math.min(z, p[1]);
        }
        return new BlockVec(x, minY, z);
    }

    @Override
    public BlockVec max() {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        for (int[] p : points) {
            x = Math.max(x, p[0]);
            z = Math.max(z, p[1]);
        }
        return new BlockVec(x, maxY, z);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        if (y < minY || y > maxY || points.size() < 3) return false;
        boolean inside = false;
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int[] a = points.get(i), b = points.get(j);
            if (a[0] == x && a[1] == z) return true;
            // on an edge counts as inside
            if (onSegment(a, b, x, z)) return true;
            if ((a[1] > z) != (b[1] > z)) {
                double ix = (double) (b[0] - a[0]) * (z - a[1]) / (double) (b[1] - a[1]) + a[0];
                if (x < ix) inside = !inside;
            }
        }
        return inside;
    }

    private static boolean onSegment(int[] a, int[] b, int x, int z) {
        long cross = (long) (b[0] - a[0]) * (z - a[1]) - (long) (b[1] - a[1]) * (x - a[0]);
        if (cross != 0) return false;
        return x >= Math.min(a[0], b[0]) && x <= Math.max(a[0], b[0]) && z >= Math.min(a[1], b[1]) && z <= Math.max(a[1], b[1]);
    }

    @Override
    public long volume() {
        return countVolume();
    }

    @Override
    public String describe() {
        return "polygon with " + points.size() + " points, y " + minY + ".." + maxY;
    }
}
