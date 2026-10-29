package org.mydreamduels.dreamworldedit.clipboard;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.mydreamduels.dreamworldedit.pattern.Blocks;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Direction;

/**
 * A copied box of blocks. offset = (box minimum) - (where the player stood
 * when copying), so pasting puts the build back in the same spot relative
 * to the player - same as WorldEdit, and stored in .schem files as WEOffset.
 * null entries are positions outside a non-cuboid selection (never pasted).
 */
public final class Clipboard {

    private final int width, height, length;
    private final BlockData[] blocks;
    private final BlockVec offset;
    /** World position the copy was made from (for //paste -o), or null for loaded schematics. */
    private final BlockVec origin;

    public Clipboard(int width, int height, int length, BlockData[] blocks, BlockVec offset, BlockVec origin) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.offset = offset;
        this.origin = origin;
    }

    public static Clipboard empty(int w, int h, int l, BlockVec offset, BlockVec origin) {
        return new Clipboard(w, h, l, new BlockData[w * h * l], offset, origin);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }
    public BlockVec offset() { return offset; }
    public BlockVec origin() { return origin; }

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    public BlockData get(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    /** Only called by the copy job for its own positions - each slot is written by exactly one job. */
    public void set(int x, int y, int z, BlockData data) {
        blocks[index(x, y, z)] = data;
    }

    public long volume() {
        return (long) width * height * length;
    }

    /** Tiles the clipboard through the world - the #clipboard pattern. */
    public Pattern asPattern() {
        return (x, y, z) -> {
            BlockData d = get(Math.floorMod(x, width), Math.floorMod(y, height), Math.floorMod(z, length));
            return d == null ? Blocks.AIR : d;
        };
    }

    /** Rotate clockwise (seen from above) around the copy origin by 90, 180 or 270 degrees. */
    public Clipboard rotate(int degrees) {
        int turns = Math.floorMod(Math.round(degrees / 90f), 4);
        if (turns == 0) return this;
        StructureRotation rot = switch (turns) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            default -> StructureRotation.COUNTERCLOCKWISE_90;
        };
        // corners relative to origin, rotated, to find the new box
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : new int[][]{{0, 0}, {width - 1, 0}, {0, length - 1}, {width - 1, length - 1}}) {
            int[] r = rot(offset.x() + c[0], offset.z() + c[1], turns);
            minX = Math.min(minX, r[0]);
            maxX = Math.max(maxX, r[0]);
            minZ = Math.min(minZ, r[1]);
            maxZ = Math.max(maxZ, r[1]);
        }
        int nw = maxX - minX + 1, nl = maxZ - minZ + 1;
        Clipboard out = empty(nw, height, nl, new BlockVec(minX, offset.y(), minZ), origin);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockData d = get(x, y, z);
                    if (d == null) continue;
                    int[] r = rot(offset.x() + x, offset.z() + z, turns);
                    BlockData copy = d.clone();
                    try {
                        copy.rotate(rot);
                    } catch (RuntimeException ignored) {
                        // platform can't rotate block states - position still rotates
                    }
                    out.set(r[0] - minX, y, r[1] - minZ, copy);
                }
            }
        }
        return out;
    }

    /** Clockwise quarter turns: (x, z) -> (-z, x). */
    private static int[] rot(int x, int z, int turns) {
        for (int i = 0; i < turns; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[]{x, z};
    }

    /** Mirror across the plane facing dir (east/west flips x, north/south flips z, up/down flips y). */
    public Clipboard flip(Direction dir) {
        boolean fx = dir == Direction.EAST || dir == Direction.WEST;
        boolean fz = dir == Direction.NORTH || dir == Direction.SOUTH;
        boolean fy = !fx && !fz;
        BlockVec no = new BlockVec(fx ? -(offset.x() + width - 1) : offset.x(),
                fy ? -(offset.y() + height - 1) : offset.y(),
                fz ? -(offset.z() + length - 1) : offset.z());
        Mirror mirror = fx ? Mirror.FRONT_BACK : fz ? Mirror.LEFT_RIGHT : Mirror.NONE;
        Clipboard out = empty(width, height, length, no, origin);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockData d = get(x, y, z);
                    if (d == null) continue;
                    BlockData copy = d.clone();
                    if (mirror != Mirror.NONE) {
                        try {
                            copy.mirror(mirror);
                        } catch (RuntimeException ignored) {
                            // platform can't mirror block states - position still flips
                        }
                    }
                    out.set(fx ? width - 1 - x : x, fy ? height - 1 - y : y, fz ? length - 1 - z : z, copy);
                }
            }
        }
        return out;
    }
}
