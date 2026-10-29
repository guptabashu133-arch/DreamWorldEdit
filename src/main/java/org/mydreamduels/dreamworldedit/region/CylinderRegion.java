package org.mydreamduels.dreamworldedit.region;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.util.BlockVec;

/** Vertical cylinder from minY to maxY. hollow = only the side wall. */
public record CylinderRegion(World world, int cx, int cz, double rx, double rz, int minY, int maxY, boolean hollow) implements Region {

    @Override
    public BlockVec min() {
        return new BlockVec(cx - (int) Math.ceil(rx), minY, cz - (int) Math.ceil(rz));
    }

    @Override
    public BlockVec max() {
        return new BlockVec(cx + (int) Math.ceil(rx), maxY, cz + (int) Math.ceil(rz));
    }

    private static double f(int dx, int dz, double rx, double rz) {
        double a = dx / (rx + 0.5), b = dz / (rz + 0.5);
        return a * a + b * b;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        if (y < minY || y > maxY) return false;
        int dx = x - cx, dz = z - cz;
        if (f(dx, dz, rx, rz) > 1) return false;
        return !hollow || f(Math.abs(dx) + 1, dz, rx, rz) > 1 || f(dx, Math.abs(dz) + 1, rx, rz) > 1;
    }

    @Override
    public long volume() {
        return countVolume();
    }

    @Override
    public String describe() {
        return "cylinder r=" + (int) rx + (rx != rz ? "/" + (int) rz : "") + " y " + minY + ".." + maxY + " at " + cx + ", " + cz;
    }
}
