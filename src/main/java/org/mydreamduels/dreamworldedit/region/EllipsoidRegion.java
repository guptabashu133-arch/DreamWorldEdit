package org.mydreamduels.dreamworldedit.region;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.util.BlockVec;

/** Sphere when all radii are equal. hollow = only the outer shell (1 block thick). */
public record EllipsoidRegion(World world, BlockVec center, double rx, double ry, double rz, boolean hollow) implements Region {

    @Override
    public BlockVec min() {
        return new BlockVec(center.x() - (int) Math.ceil(rx), clampY(center.y() - (int) Math.ceil(ry)), center.z() - (int) Math.ceil(rz));
    }

    @Override
    public BlockVec max() {
        return new BlockVec(center.x() + (int) Math.ceil(rx), clampTop(center.y() + (int) Math.ceil(ry)), center.z() + (int) Math.ceil(rz));
    }

    private int clampY(int y) {
        return Math.max(world.getMinHeight(), y);
    }

    private int clampTop(int y) {
        return Math.min(world.getMaxHeight() - 1, y);
    }

    private static double f(int dx, int dy, int dz, double rx, double ry, double rz) {
        double a = dx / (rx + 0.5), b = dy / (ry + 0.5), c = dz / (rz + 0.5);
        return a * a + b * b + c * c;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        int dx = x - center.x(), dy = y - center.y(), dz = z - center.z();
        if (f(dx, dy, dz, rx, ry, rz) > 1) return false;
        if (!hollow) return true;
        // on the shell if any neighbour is outside
        return f(Math.abs(dx) + 1, dy, dz, rx, ry, rz) > 1 || f(dx, Math.abs(dy) + 1, dz, rx, ry, rz) > 1
                || f(dx, dy, Math.abs(dz) + 1, rx, ry, rz) > 1;
    }

    @Override
    public long volume() {
        return countVolume();
    }

    @Override
    public String describe() {
        return (rx == ry && ry == rz ? "sphere r=" + (int) rx : "ellipsoid " + (int) rx + "/" + (int) ry + "/" + (int) rz) + " at " + center;
    }
}
