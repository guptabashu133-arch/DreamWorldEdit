package org.mydreamduels.dreamworldedit.edit;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.clipboard.Clipboard;
import org.mydreamduels.dreamworldedit.pattern.Blocks;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.region.CuboidRegion;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.util.BlockVec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every editing operation, built on Engine. All return futures that
 * complete when the last chunk job is done; block access only ever happens
 * inside ChunkOps (i.e. on the chunk's own thread).
 */
public final class Operations {

    private final DreamWorldEdit plugin;

    public Operations(DreamWorldEdit plugin) {
        this.plugin = plugin;
    }

    private Engine engine() {
        return plugin.engine();
    }

    // ------------------------------------------------------------------ iteration helpers

    /** Visits every position of r that lies in this job's chunk + slab. */
    public static void forEach(ChunkContext ctx, Region r, PosVisitor v) {
        BlockVec a = r.min(), b = r.max();
        int x0 = Math.max(a.x(), ctx.baseX()), x1 = Math.min(b.x(), ctx.baseX() + 15);
        int z0 = Math.max(a.z(), ctx.baseZ()), z1 = Math.min(b.z(), ctx.baseZ() + 15);
        int y0 = Math.max(a.y(), ctx.minY), y1 = Math.min(b.y(), ctx.maxY);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    if (r.contains(x, y, z)) v.visit(ctx, x, y, z);
    }

    private CompletableFuture<EditResult> over(Region r, ChunkOp op, boolean columnar, String desc, boolean record, long budget) {
        return engine().run(r.world(), r.min(), r.max(), key -> r.touchesChunk(EditRecord.chunkX(key), EditRecord.chunkZ(key)),
                op, columnar, desc, record, budget);
    }

    // ------------------------------------------------------------------ basic

    /** //set, //replace, shapes, removenear... */
    public CompletableFuture<EditResult> fill(Region r, Pattern pattern, Mask mask, String desc, long budget) {
        return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
            if (mask == Mask.ALL || mask.test(c, x, y, z, c.get(x, y, z))) c.set(x, y, z, pattern.apply(x, y, z));
        }), false, desc, true, budget);
    }

    /** Blocks of r that touch the outside horizontally (walls) or in any direction (faces/outline). */
    public static Region shell(Region r, boolean allSides) {
        return new Region() {
            public World world() { return r.world(); }
            public BlockVec min() { return r.min(); }
            public BlockVec max() { return r.max(); }
            public long volume() { return countVolume(); }
            public String describe() { return (allSides ? "faces of " : "walls of ") + r.describe(); }
            public boolean touchesChunk(int cx, int cz) { return r.touchesChunk(cx, cz); }
            public boolean contains(int x, int y, int z) {
                if (!r.contains(x, y, z)) return false;
                if (!r.contains(x + 1, y, z) || !r.contains(x - 1, y, z) || !r.contains(x, y, z + 1) || !r.contains(x, y, z - 1)) return true;
                return allSides && (!r.contains(x, y + 1, z) || !r.contains(x, y - 1, z));
            }
        };
    }

    /** //count and //distr: read-only. */
    public CompletableFuture<Map<Material, AtomicLong>> distribution(Region r, Mask mask) {
        Map<Material, AtomicLong> counts = new ConcurrentHashMap<>();
        return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
            BlockData d = c.get(x, y, z);
            if (mask == Mask.ALL || mask.test(c, x, y, z, d)) counts.computeIfAbsent(d.getMaterial(), k -> new AtomicLong()).incrementAndGet();
        }), false, "count", false, 0).thenApply(res -> counts);
    }

    // ------------------------------------------------------------------ column based

    private interface ColumnVisitor {
        void visit(ChunkContext ctx, int x, int z, int minY, int maxY);
    }

    /** Whole columns of r inside the job's chunk (columnar jobs only). */
    private static void columns(ChunkContext ctx, Region r, ColumnVisitor v) {
        BlockVec a = r.min(), b = r.max();
        int x0 = Math.max(a.x(), ctx.baseX()), x1 = Math.min(b.x(), ctx.baseX() + 15);
        int z0 = Math.max(a.z(), ctx.baseZ()), z1 = Math.min(b.z(), ctx.baseZ() + 15);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int y = a.y(); y <= b.y(); y++) {
                    if (r.contains(x, y, z)) {
                        lo = Math.min(lo, y);
                        hi = Math.max(hi, y);
                    }
                }
                if (lo <= hi) v.visit(ctx, x, z, Math.max(lo, ctx.world.getMinHeight()), Math.min(hi, ctx.world.getMaxHeight() - 1));
            }
        }
    }

    /** One block of pattern on top of the highest non-air block of every column. */
    public CompletableFuture<EditResult> overlay(Region r, Pattern pattern, long budget) {
        return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
            for (int y = hi; y >= lo; y--) {
                if (!c.get(x, y, z).getMaterial().isAir()) {
                    if (y + 1 < c.world.getMaxHeight() && c.get(x, y + 1, z).getMaterial().isAir()) c.set(x, y + 1, z, pattern.apply(x, y + 1, z));
                    return;
                }
            }
        }), true, "overlay", true, budget);
    }

    private static final Set<Material> NATURAL = EnumSet.of(Material.STONE, Material.DIRT, Material.GRASS_BLOCK,
            Material.COARSE_DIRT, Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.DEEPSLATE, Material.TUFF);

    /** Grass on top, 3 dirt below, stone underneath - only for natural terrain blocks. */
    public CompletableFuture<EditResult> naturalize(Region r, long budget) {
        BlockData grass = Material.GRASS_BLOCK.createBlockData();
        BlockData dirt = Material.DIRT.createBlockData();
        BlockData stone = Material.STONE.createBlockData();
        return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
            int depth = 0;
            for (int y = hi; y >= lo; y--) {
                BlockData d = c.get(x, y, z);
                if (d.getMaterial().isAir()) {
                    depth = 0;
                    continue;
                }
                if (!NATURAL.contains(d.getMaterial())) continue;
                c.set(x, y, z, depth == 0 ? grass : depth <= 3 ? dirt : stone);
                depth++;
            }
        }), true, "naturalize", true, budget);
    }

    /** Non-air blocks of each column fall to the bottom of the region. */
    public CompletableFuture<EditResult> gravity(Region r, long budget) {
        return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
            List<BlockData> stack = new ArrayList<>();
            for (int y = lo; y <= hi; y++) {
                BlockData d = c.get(x, y, z);
                if (!d.getMaterial().isAir() && r.contains(x, y, z)) stack.add(d);
            }
            int i = 0;
            for (int y = lo; y <= hi; y++) {
                if (!r.contains(x, y, z)) continue;
                c.set(x, y, z, i < stack.size() ? stack.get(i) : Blocks.AIR);
                i++;
            }
        }), true, "gravity", true, budget);
    }

    /**
     * Heightmap smoothing (//smooth and the smooth brush). Phase 1 reads the
     * top of every column (+1 border), phase 2 blurs it off-thread, phase 3
     * raises/lowers columns using each column's own top block.
     */
    public CompletableFuture<EditResult> smooth(Region r, int iterations, long budget) {
        BlockVec a = r.min(), b = r.max();
        int w = b.x() - a.x() + 3, l = b.z() - a.z() + 3;
        int[] heights = new int[w * l];
        java.util.Arrays.fill(heights, Integer.MIN_VALUE);
        Region border = new CuboidRegion(r.world(), a.add(-1, 0, -1), b.add(1, 0, 1));
        return over(border, ctx -> columns(ctx, border, (c, x, z, lo, hi) -> {
            int h = lo - 1;
            for (int y = hi; y >= lo; y--) {
                if (c.get(x, y, z).getMaterial().isSolid()) {
                    h = y;
                    break;
                }
            }
            heights[(x - a.x() + 1) + (z - a.z() + 1) * w] = h;
        }), true, "smooth-read", false, 0).thenCompose(read -> {
            double[] cur = new double[heights.length];
            for (int i = 0; i < cur.length; i++) cur[i] = heights[i] == Integer.MIN_VALUE ? a.y() - 1 : heights[i];
            for (int it = 0; it < Math.max(1, iterations); it++) {
                double[] next = cur.clone();
                for (int z = 1; z < l - 1; z++) {
                    for (int x = 1; x < w - 1; x++) {
                        double sum = 0, wsum = 0;
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                double k = (dx == 0 && dz == 0) ? 4 : (dx == 0 || dz == 0) ? 2 : 1;
                                sum += cur[(x + dx) + (z + dz) * w] * k;
                                wsum += k;
                            }
                        }
                        next[x + z * w] = sum / wsum;
                    }
                }
                cur = next;
            }
            double[] target = cur;
            return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
                int idx = (x - a.x() + 1) + (z - a.z() + 1) * w;
                int old = heights[idx];
                int now = (int) Math.round(target[idx]);
                if (old == Integer.MIN_VALUE || old < lo || now == old) return;
                now = Math.max(lo - 1, Math.min(hi, now));
                BlockData top = c.get(x, old, z);
                if (now > old) {
                    BlockData under = old - 1 >= lo ? c.get(x, old - 1, z) : top;
                    if (under.getMaterial().isAir()) under = top;
                    for (int y = old; y < now; y++) c.set(x, y, z, under);
                    c.set(x, now, z, top);
                } else {
                    for (int y = old; y > now; y--) c.set(x, y, z, Blocks.AIR);
                    if (now >= lo) c.set(x, now, z, top);
                }
            }), true, "smooth", true, budget);
        });
    }

    // ------------------------------------------------------------------ clipboard

    /** Copies r into a new clipboard relative to origin (the player's position). */
    public CompletableFuture<Clipboard> copy(Region r, BlockVec origin, Mask mask) {
        BlockVec a = r.min();
        Clipboard clip = Clipboard.empty(r.width(), r.height(), r.length(), a.subtract(origin), origin);
        return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
            BlockData d = c.get(x, y, z);
            if (mask == Mask.ALL || mask.test(c, x, y, z, d)) clip.set(x - a.x(), y - a.y(), z - a.z(), d);
        }), false, "copy", false, 0).thenApply(res -> clip);
    }

    /** Pastes clip so that its origin lands on each of 'at' (one entry per copy for //stack). */
    public CompletableFuture<EditResult> paste(World world, Clipboard clip, List<BlockVec> at, boolean skipAir, Region clearFirst,
                                               BlockData clearWith, String desc, long budget) {
        BlockVec min = null, max = null;
        for (BlockVec o : at) {
            BlockVec lo = o.add(clip.offset());
            BlockVec hi = lo.add(clip.width() - 1, clip.height() - 1, clip.length() - 1);
            min = min == null ? lo : BlockVec.min(min, lo);
            max = max == null ? hi : BlockVec.max(max, hi);
        }
        if (clearFirst != null) {
            min = BlockVec.min(min, clearFirst.min());
            max = BlockVec.max(max, clearFirst.max());
        }
        Set<Long> chunks = ConcurrentHashMap.newKeySet();
        for (BlockVec o : at) addChunks(chunks, o.add(clip.offset()), o.add(clip.offset()).add(clip.width() - 1, 0, clip.length() - 1));
        if (clearFirst != null) addChunks(chunks, clearFirst.min(), clearFirst.max());
        ChunkOp op = ctx -> {
            if (clearFirst != null) forEach(ctx, clearFirst, (c, x, y, z) -> c.set(x, y, z, clearWith));
            for (BlockVec o : at) {
                BlockVec base = o.add(clip.offset());
                int x0 = Math.max(base.x(), ctx.baseX()), x1 = Math.min(base.x() + clip.width() - 1, ctx.baseX() + 15);
                int z0 = Math.max(base.z(), ctx.baseZ()), z1 = Math.min(base.z() + clip.length() - 1, ctx.baseZ() + 15);
                int y0 = Math.max(base.y(), ctx.minY), y1 = Math.min(base.y() + clip.height() - 1, ctx.maxY);
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++)
                        for (int y = y0; y <= y1; y++) {
                            BlockData d = clip.get(x - base.x(), y - base.y(), z - base.z());
                            if (d == null || (skipAir && d.getMaterial().isAir())) continue;
                            ctx.set(x, y, z, d);
                        }
            }
        };
        return engine().run(world, min, max, chunks::contains, op, false, desc, true, budget);
    }

    private static void addChunks(Set<Long> into, BlockVec a, BlockVec b) {
        for (int cx = Math.min(a.x(), b.x()) >> 4; cx <= Math.max(a.x(), b.x()) >> 4; cx++)
            for (int cz = Math.min(a.z(), b.z()) >> 4; cz <= Math.max(a.z(), b.z()) >> 4; cz++)
                into.add(EditRecord.key(cx, cz));
    }

    // ------------------------------------------------------------------ point sets (line, trees)

    /** Sets explicit positions (packed with pack()). */
    public CompletableFuture<EditResult> setAll(World world, Map<Long, BlockData> changes, String desc, long budget) {
        Map<Long, List<Long>> byChunk = new ConcurrentHashMap<>();
        for (long p : changes.keySet()) {
            byChunk.computeIfAbsent(EditRecord.key(unpackX(p) >> 4, unpackZ(p) >> 4), k -> new ArrayList<>()).add(p);
        }
        return engine().runChunks(world, byChunk.keySet(), ctx -> {
            List<Long> list = byChunk.get(EditRecord.key(ctx.cx, ctx.cz));
            if (list == null) return;
            for (long p : list) ctx.set(unpackX(p), unpackY(p), unpackZ(p), changes.get(p));
        }, desc, true, budget);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int unpackX(long p) {
        return (int) (p >> 38);
    }

    public static int unpackY(long p) {
        int y = (int) (p & 0xFFF);
        return y >= 2048 ? y - 4096 : y;
    }

    public static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }

    /** Straight line between two points, thickness = radius of the brush along it. */
    public CompletableFuture<EditResult> line(World world, BlockVec from, BlockVec to, Pattern pattern, int thickness, long budget) {
        Map<Long, BlockData> points = new ConcurrentHashMap<>();
        int dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int px = (int) Math.round(from.x() + dx * t), py = (int) Math.round(from.y() + dy * t), pz = (int) Math.round(from.z() + dz * t);
            for (int ox = -thickness; ox <= thickness; ox++)
                for (int oy = -thickness; oy <= thickness; oy++)
                    for (int oz = -thickness; oz <= thickness; oz++)
                        if (ox * ox + oy * oy + oz * oz <= thickness * thickness + thickness) {
                            int x = px + ox, y = py + oy, z = pz + oz;
                            points.put(pack(x, y, z), pattern.apply(x, y, z));
                        }
        }
        return setAll(world, points, "line", budget);
    }

    /**
     * //forest: trees are first generated into a recording delegate (nothing is
     * placed), then all their blocks go through setAll - so it's undoable and
     * every block is still written by its own chunk's thread.
     */
    public CompletableFuture<EditResult> forest(Region r, TreeType type, double density, long budget) {
        Map<Long, BlockData> planned = new ConcurrentHashMap<>();
        return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
            if (ThreadLocalRandom.current().nextDouble(100) >= density) return;
            for (int y = hi; y >= lo; y--) {
                Material m = c.get(x, y, z).getMaterial();
                if (m.isAir()) continue;
                if (m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.PODZOL || m == Material.COARSE_DIRT) {
                    Map<Long, BlockData> tree = new java.util.HashMap<>();
                    try {
                        c.world.generateTree(new Location(c.world, x, y + 1, z), type, new org.bukkit.BlockChangeDelegate() {
                            public boolean setBlockData(int bx, int by, int bz, BlockData data) {
                                tree.put(pack(bx, by, bz), data);
                                return true;
                            }
                            public BlockData getBlockData(int bx, int by, int bz) {
                                BlockData p = tree.get(pack(bx, by, bz));
                                if (p == null) p = planned.get(pack(bx, by, bz));
                                return p != null ? p : c.world.getBlockAt(bx, by, bz).getBlockData();
                            }
                            public int getHeight() {
                                return c.world.getMaxHeight();
                            }
                            public boolean isEmpty(int bx, int by, int bz) {
                                return getBlockData(bx, by, bz).getMaterial().isAir();
                            }
                        });
                        planned.putAll(tree); // only whole trees
                    } catch (RuntimeException ignored) {
                        // tree reached into a region this thread doesn't own (Folia) - skip it
                    }
                }
                return;
            }
        }), true, "forest-plan", false, 0).thenCompose(res -> setAll(r.world(), planned, "forest", budget));
    }

    // ------------------------------------------------------------------ biome / utilities

    /** Not undoable (biomes aren't blocks). Players see it after the chunk reloads. */
    public CompletableFuture<EditResult> setBiome(Region r, Biome biome) {
        AtomicLong n = new AtomicLong();
        return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
            c.world.setBiome(x, y, z, biome);
            n.incrementAndGet();
        }), false, "setbiome", false, 0).thenApply(res -> new EditResult(n.get(), null, res.millis(), res.error()));
    }

    /** //drain: water, lava and waterlogging removed. */
    public CompletableFuture<EditResult> drain(Region r, long budget) {
        return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
            BlockData d = c.get(x, y, z);
            Material m = d.getMaterial();
            if (m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP
                    || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS) {
                c.set(x, y, z, Blocks.AIR);
            } else if (d instanceof Waterlogged wl && wl.isWaterlogged()) {
                Waterlogged dry = (Waterlogged) d.clone();
                dry.setWaterlogged(false);
                c.set(x, y, z, dry);
            }
        }), false, "drain", true, budget);
    }

    /** //fixwater, //fixlava: flowing liquid becomes source blocks. */
    public CompletableFuture<EditResult> fixLiquid(Region r, Material liquid, long budget) {
        BlockData source = liquid.createBlockData();
        return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
            BlockData d = c.get(x, y, z);
            if (d.getMaterial() == liquid && d instanceof Levelled lv && lv.getLevel() != 0) c.set(x, y, z, source);
        }), false, "fix" + liquid.name().toLowerCase(), true, budget);
    }

    /** //snow, //thaw, //green per column; //ex(tinguish) per block. */
    public CompletableFuture<EditResult> surface(Region r, String kind, long budget) {
        BlockData snow = Material.SNOW.createBlockData();
        BlockData grass = Material.GRASS_BLOCK.createBlockData();
        BlockData water = Material.WATER.createBlockData();
        if (kind.equals("ex")) {
            return over(r, ctx -> forEach(ctx, r, (c, x, y, z) -> {
                Material m = c.get(x, y, z).getMaterial();
                if (m == Material.FIRE || m == Material.SOUL_FIRE) c.set(x, y, z, Blocks.AIR);
            }), false, "extinguish", true, budget);
        }
        return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
            for (int y = hi; y >= lo; y--) {
                BlockData d = c.get(x, y, z);
                Material m = d.getMaterial();
                if (kind.equals("thaw")) {
                    if (m == Material.SNOW) c.set(x, y, z, Blocks.AIR);
                    else if (m == Material.ICE) c.set(x, y, z, water);
                    if (!m.isAir() && m != Material.SNOW) return;
                    continue;
                }
                if (m.isAir()) continue;
                if (kind.equals("snow")) {
                    if (m.isSolid() && m.isOccluding() && y + 1 <= hi && c.get(x, y + 1, z).getMaterial().isAir()) c.set(x, y + 1, z, snow);
                } else if (kind.equals("green")) {
                    if (m == Material.DIRT) c.set(x, y, z, grass);
                }
                return;
            }
        }), true, kind, true, budget);
    }

    /** //fill: fills air going down from the start height in every column until something solid is hit. */
    public CompletableFuture<EditResult> fillHoles(Region r, Pattern pattern, long budget) {
        return over(r, ctx -> columns(ctx, r, (c, x, z, lo, hi) -> {
            for (int y = hi; y >= lo; y--) {
                Material m = c.get(x, y, z).getMaterial();
                if (!m.isAir() && m != Material.WATER && m != Material.LAVA) return;
                c.set(x, y, z, pattern.apply(x, y, z));
            }
        }), true, "fill", true, budget);
    }
}
