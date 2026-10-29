package org.mydreamduels.dreamworldedit.region;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.util.BlockVec;

import java.util.ArrayList;
import java.util.List;

/**
 * The points a player picked (wand / //pos1 / //pos2) and the shape they
 * make. pos1 always starts a new selection; pos2 adds to it.
 *   cuboid    pos1, pos2 = opposite corners
 *   poly      pos1 = first point, pos2 = every next point (height grows to include them)
 *   sphere    pos1 = centre, pos2 = sets the radius (distance)
 *   ellipsoid pos1 = centre, pos2 = sets the x/y/z radii
 *   cyl       pos1 = centre, pos2 = sets the radius and extends the height
 */
public final class Selection {

    private SelectionMode mode = SelectionMode.CUBOID;
    private World world;
    private BlockVec pos1, pos2;
    private final List<int[]> poly = new ArrayList<>();
    private int polyMinY, polyMaxY;

    public synchronized SelectionMode mode() {
        return mode;
    }

    public synchronized void setMode(SelectionMode mode) {
        this.mode = mode;
        clear();
    }

    public synchronized void clear() {
        pos1 = pos2 = null;
        poly.clear();
    }

    public synchronized BlockVec pos1() {
        return pos1;
    }

    public synchronized BlockVec pos2() {
        return pos2;
    }

    public synchronized World world() {
        return world;
    }

    public synchronized void setPos1(World w, BlockVec p) {
        if (world != null && !world.equals(w)) clear();
        world = w;
        pos1 = p;
        if (mode != SelectionMode.CUBOID) pos2 = null;
        if (mode == SelectionMode.POLY) {
            poly.clear();
            poly.add(new int[]{p.x(), p.z()});
            polyMinY = polyMaxY = p.y();
        }
        cylMinY = cylMaxY = p.y();
    }

    /** Returns false if pos2 was ignored (e.g. poly without a first point). */
    public synchronized boolean setPos2(World w, BlockVec p) {
        if (world != null && !world.equals(w)) {
            clear();
            world = w;
        }
        world = w;
        if (mode == SelectionMode.POLY) {
            if (poly.isEmpty()) return false;
            poly.add(new int[]{p.x(), p.z()});
            polyMinY = Math.min(polyMinY, p.y());
            polyMaxY = Math.max(polyMaxY, p.y());
            pos2 = p;
            return true;
        }
        if (mode == SelectionMode.CYL && pos2 != null && pos1 != null) {
            // cyl: further clicks keep the larger radius and extend height
            BlockVec old = pos2;
            int r = Math.max(Math.max(Math.abs(old.x() - pos1.x()), Math.abs(old.z() - pos1.z())),
                    Math.max(Math.abs(p.x() - pos1.x()), Math.abs(p.z() - pos1.z())));
            pos2 = new BlockVec(pos1.x() + r, p.y(), pos1.z());
            cylMinY = Math.min(cylMinY, p.y());
            cylMaxY = Math.max(cylMaxY, p.y());
            return true;
        }
        if (mode == SelectionMode.CYL && pos1 != null) {
            cylMinY = Math.min(pos1.y(), p.y());
            cylMaxY = Math.max(pos1.y(), p.y());
        }
        pos2 = p;
        return mode == SelectionMode.CUBOID || pos1 != null;
    }

    private int cylMinY, cylMaxY;

    public synchronized int polyPoints() {
        return poly.size();
    }

    /** The selected region, or null if incomplete. */
    public synchronized Region region() {
        if (world == null) return null;
        switch (mode) {
            case CUBOID:
                return pos1 == null || pos2 == null ? null : CuboidRegion.of(world, pos1, pos2);
            case POLY:
                return poly.size() < 3 ? null : new PolygonRegion(world, new ArrayList<>(poly), polyMinY, polyMaxY);
            case SPHERE: {
                if (pos1 == null || pos2 == null) return null;
                double r = Math.sqrt(sq(pos2.x() - pos1.x()) + sq(pos2.y() - pos1.y()) + sq(pos2.z() - pos1.z()));
                return new EllipsoidRegion(world, pos1, r, r, r, false);
            }
            case ELLIPSOID:
                if (pos1 == null || pos2 == null) return null;
                return new EllipsoidRegion(world, pos1, Math.abs(pos2.x() - pos1.x()), Math.abs(pos2.y() - pos1.y()),
                        Math.abs(pos2.z() - pos1.z()), false);
            case CYL: {
                if (pos1 == null || pos2 == null) return null;
                double r = Math.max(Math.abs(pos2.x() - pos1.x()), Math.abs(pos2.z() - pos1.z()));
                return new CylinderRegion(world, pos1.x(), pos1.z(), r, r, cylMinY, cylMaxY, false);
            }
            default:
                return null;
        }
    }

    private static double sq(double d) {
        return d * d;
    }

    /** Replace a cuboid selection's corners (expand/contract/shift/chunk/paste -s). */
    public synchronized void setCuboid(World w, BlockVec a, BlockVec b) {
        mode = SelectionMode.CUBOID;
        poly.clear();
        world = w;
        pos1 = a;
        pos2 = b;
    }

    /** Moves every point of the selection, whatever its shape. */
    public synchronized void shift(int dx, int dy, int dz) {
        if (pos1 != null) pos1 = pos1.add(dx, dy, dz);
        if (pos2 != null) pos2 = pos2.add(dx, dy, dz);
        for (int[] p : poly) {
            p[0] += dx;
            p[1] += dz;
        }
        polyMinY += dy;
        polyMaxY += dy;
        cylMinY += dy;
        cylMaxY += dy;
    }

    /** Poly: change the vertical extent (expand vert / up / down). */
    public synchronized void setPolyHeight(int minY, int maxY) {
        polyMinY = minY;
        polyMaxY = maxY;
    }
}
