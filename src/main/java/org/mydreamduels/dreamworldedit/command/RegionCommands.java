package org.mydreamduels.dreamworldedit.command;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.edit.Operations;
import org.mydreamduels.dreamworldedit.pattern.Blocks;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.region.CuboidRegion;
import org.mydreamduels.dreamworldedit.region.CylinderRegion;
import org.mydreamduels.dreamworldedit.region.EllipsoidRegion;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.region.SelectionMode;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Direction;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Region edits, generation (shapes) and utilities. */
public final class RegionCommands {

    private RegionCommands() {
    }

    private static BlockVec here(Player p) {
        return BlockVec.of(p.getLocation());
    }

    public static void register(Commands c) {
        DreamWorldEdit plugin = c.plugin();
        var ops = plugin.ops();
        var edits = plugin.edits();
        WeCommand.Completer blocks = (p, a) -> Commands.blocks();

        // ------------------------------------------------------------ region
        c.add("region", "/set", "<pattern>", "Fill the selection", 1, (p, a) -> {
            Region r = c.selection(p);
            Pattern pat = c.pattern(p, a[0]);
            edits.submit(p, "set", r.boxVolume(), b -> ops.fill(r, pat, Mask.ALL, "set", b));
        }, blocks);

        c.add("region", "/replace", "[from-mask] <to-pattern>", "Replace blocks in the selection", 1, (p, a) -> {
            Region r = c.selection(p);
            Mask m = a.length >= 2 ? c.mask(a[0]) : c.mask("#existing");
            Pattern pat = c.pattern(p, a.length >= 2 ? a[1] : a[0]);
            edits.submit(p, "replace", r.boxVolume(), b -> ops.fill(r, pat, m, "replace", b));
        }, (p, a) -> a.length == 1 ? Commands.masks() : Commands.blocks(), "/re", "/rep");

        c.add("region", "/walls", "<pattern>", "Build walls around the selection", 1, (p, a) -> {
            Region r = Operations.shell(c.selection(p), false);
            Pattern pat = c.pattern(p, a[0]);
            edits.submit(p, "walls", r.boxVolume(), b -> ops.fill(r, pat, Mask.ALL, "walls", b));
        }, blocks);

        c.add("region", "/faces", "<pattern>", "Build the outside shell (walls, floor, ceiling)", 1, (p, a) -> {
            Region r = Operations.shell(c.selection(p), true);
            Pattern pat = c.pattern(p, a[0]);
            edits.submit(p, "faces", r.boxVolume(), b -> ops.fill(r, pat, Mask.ALL, "faces", b));
        }, blocks, "/outline");

        c.add("region", "/overlay", "<pattern>", "Put a layer on top of every column", 1, (p, a) -> {
            Region r = c.selection(p);
            Pattern pat = c.pattern(p, a[0]);
            edits.submit(p, "overlay", r.boxVolume(), b -> ops.overlay(r, pat, b));
        }, blocks);

        c.add("region", "/center", "<pattern>", "Set the centre block(s)", 1, (p, a) -> {
            Region r = c.selection(p);
            BlockVec lo = r.min(), hi = r.max();
            int cx0 = (lo.x() + hi.x()) / 2, cy0 = (lo.y() + hi.y()) / 2, cz0 = (lo.z() + hi.z()) / 2;
            Region center = CuboidRegion.of(p.getWorld(), new BlockVec(cx0, cy0, cz0),
                    new BlockVec(cx0 + ((r.width() + 1) % 2), cy0 + ((r.height() + 1) % 2), cz0 + ((r.length() + 1) % 2)));
            Pattern pat = c.pattern(p, a[0]);
            edits.submit(p, "center", center.boxVolume(), b -> ops.fill(center, pat, Mask.ALL, "center", b));
        }, blocks, "/middle");

        c.add("region", "/naturalize", "", "Grass on top, dirt below, then stone", 0, (p, a) -> {
            Region r = c.selection(p);
            edits.submit(p, "naturalize", r.boxVolume(), b -> ops.naturalize(r, b));
        }, null);

        c.add("region", "/smooth", "[iterations]", "Smooth the terrain", 0, (p, a) -> {
            Region r = c.selection(p);
            int it = a.length > 0 ? Commands.integer(a[0], "Iterations") : 1;
            edits.submit(p, "smooth", r.boxVolume(), b -> ops.smooth(r, Math.min(it, 50), b));
        }, null);

        c.add("region", "/stack", "[count] [direction] [-a]", "Repeat the selection", 0, (p, a) -> {
            Region r = c.selection(p);
            String[] pos = Commands.positional(a);
            int count = pos.length > 0 ? Commands.integer(pos[0], "Count") : 1;
            Direction d = pos.length > 1 ? Commands.direction(p, pos[1]) : Direction.facing(p.getLocation());
            boolean skipAir = Commands.flag(a, "-a");
            if (count < 1) throw new WeCommand.Usage("Count must be at least 1.");
            int step = d.dx != 0 ? r.width() : d.dy != 0 ? r.height() : r.length();
            List<BlockVec> at = new ArrayList<>();
            for (int i = 1; i <= count; i++) at.add(r.min().add(d.vec().multiply(step * i)));
            edits.submit(p, "stack", r.boxVolume() * (count + 1), budget -> ops.copy(r, r.min(), Mask.ALL)
                    .thenCompose(clip -> ops.paste(p.getWorld(), clip, at, skipAir, null, null, "stack", budget)));
        }, (p, a) -> a.length == 2 ? Direction.NAMES : List.of("1", "2", "5", "-a"));

        c.add("region", "/move", "[count] [direction] [leftover-pattern] [-s] [-a]", "Move the selected blocks", 0, (p, a) -> {
            Region r = c.selection(p);
            String[] pos = Commands.positional(a);
            int count = pos.length > 0 ? Commands.integer(pos[0], "Count") : 1;
            Direction d = pos.length > 1 ? Commands.direction(p, pos[1]) : Direction.facing(p.getLocation());
            Pattern left = pos.length > 2 ? c.pattern(p, pos[2]) : (x, y, z) -> Blocks.AIR;
            boolean moveSel = Commands.flag(a, "-s"), skipAir = Commands.flag(a, "-a");
            BlockVec target = r.min().add(d.vec().multiply(count));
            edits.submit(p, "move", r.boxVolume() * 2, budget -> ops.copy(r, r.min(), Mask.ALL)
                    .thenCompose(clip -> ops.paste(p.getWorld(), clip, List.of(target), skipAir, r, left.apply(0, 0, 0), "move", budget))
                    .thenApply(res -> {
                        if (moveSel) plugin.sessions().get(p).selection().shift(d.dx * count, d.dy * count, d.dz * count);
                        return res;
                    }));
        }, (p, a) -> a.length == 2 ? Direction.NAMES : a.length == 3 ? Commands.blocks() : List.of("1", "5"));

        c.add("region", "/line", "<pattern> [thickness]", "Line from pos1 to pos2", 1, (p, a) -> {
            var sel = plugin.sessions().get(p).selection();
            c.selection(p);
            if (sel.mode() != SelectionMode.CUBOID) throw new WeCommand.Usage("//line needs a cuboid selection.");
            Pattern pat = c.pattern(p, a[0]);
            int t = a.length > 1 ? Commands.integer(a[1], "Thickness") : 0;
            if (t < 0 || t > 10) throw new WeCommand.Usage("Thickness must be 0-10.");
            BlockVec from = sel.pos1(), to = sel.pos2();
            long est = (long) (Math.max(Math.abs(to.x() - from.x()), Math.max(Math.abs(to.y() - from.y()), Math.abs(to.z() - from.z()))) + 1)
                    * (2L * t + 1) * (2L * t + 1) * (2L * t + 1);
            edits.submit(p, "line", est, b -> ops.line(p.getWorld(), from, to, pat, t, b));
        }, blocks);

        c.add("region", "/forest", "[type] [density%]", "Plant trees on grass in the selection", 0, (p, a) -> {
            Region r = c.selection(p);
            TreeType type = TreeType.TREE;
            if (a.length > 0) {
                try {
                    type = TreeType.valueOf(a[0].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new WeCommand.Usage("Unknown tree type. Try: tree, birch, redwood, jungle, acacia, dark_oak, cherry...");
                }
            }
            double density = a.length > 1 ? Commands.number(a[1], "Density") : 5;
            TreeType t = type;
            edits.submit(p, "forest", r.boxVolume(), b -> ops.forest(r, t, Math.max(0, Math.min(100, density)), b));
        }, (p, a) -> a.length == 1 ? Arrays.stream(TreeType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList() : List.of("5", "10"));

        c.add("region", "/setbiome", "<biome>", "Change the biome of the selection", 1, (p, a) -> {
            Region r = c.selection(p);
            Biome biome = biome(a[0]);
            ops.setBiome(r, biome).thenAccept(res -> Msg.send(p, "&dBiome set for " + res.changed() + " blocks &7(rejoin to see it).&8 Not undoable."));
        }, (p, a) -> biomeNames());

        c.add("region", "biomelist", "", "List biomes", 0, (p, a) -> Msg.send(p, "&7Biomes: &f" + String.join(", ", biomeNames())), null, "biomels");

        // ------------------------------------------------------------ generation
        c.add("generation", "/sphere", "<pattern> <radius>[,ry,rz] [-r]", "Sphere around you (-r: raised)", 2, (p, a) -> sphere(c, p, a, false), shapeTab());
        c.add("generation", "/hsphere", "<pattern> <radius>[,ry,rz] [-r]", "Hollow sphere around you", 2, (p, a) -> sphere(c, p, a, true), shapeTab());
        c.add("generation", "/cyl", "<pattern> <radius>[,rz] [height]", "Cylinder at your feet", 2, (p, a) -> cyl(c, p, a, false), shapeTab());
        c.add("generation", "/hcyl", "<pattern> <radius>[,rz] [height]", "Hollow cylinder at your feet", 2, (p, a) -> cyl(c, p, a, true), shapeTab());
        c.add("generation", "/pyramid", "<pattern> <size>", "Pyramid at your feet", 2, (p, a) -> pyramid(c, p, a, false), shapeTab());
        c.add("generation", "/hpyramid", "<pattern> <size>", "Hollow pyramid at your feet", 2, (p, a) -> pyramid(c, p, a, true), shapeTab());

        // ------------------------------------------------------------ utilities
        c.add("utility", "/fill", "<pattern> <radius> [depth]", "Fill holes below you", 2, (p, a) -> {
            Pattern pat = c.pattern(p, a[0]);
            int r = c.radius(a[1]);
            int depth = a.length > 2 ? Commands.integer(a[2], "Depth") : r;
            BlockVec h = here(p);
            Region region = new CylinderRegion(p.getWorld(), h.x(), h.z(), r, r, h.y() - Math.max(1, depth) + 1, h.y(), false);
            edits.submit(p, "fill", region.boxVolume(), b -> ops.fillHoles(region, pat, b));
        }, blocks);

        c.add("utility", "/drain", "<radius>", "Remove water and lava around you", 1, (p, a) -> {
            Region region = new EllipsoidRegion(p.getWorld(), here(p), c.radius(a[0]), c.radius(a[0]), c.radius(a[0]), false);
            edits.submit(p, "drain", region.boxVolume(), b -> ops.drain(region, b));
        }, null);
        c.add("utility", "/fixwater", "<radius>", "Turn flowing water into still water", 1, (p, a) -> {
            int r = c.radius(a[0]);
            Region region = new EllipsoidRegion(p.getWorld(), here(p), r, r, r, false);
            edits.submit(p, "fixwater", region.boxVolume(), b -> ops.fixLiquid(region, Material.WATER, b));
        }, null);
        c.add("utility", "/fixlava", "<radius>", "Turn flowing lava into still lava", 1, (p, a) -> {
            int r = c.radius(a[0]);
            Region region = new EllipsoidRegion(p.getWorld(), here(p), r, r, r, false);
            edits.submit(p, "fixlava", region.boxVolume(), b -> ops.fixLiquid(region, Material.LAVA, b));
        }, null);

        for (String[] s : new String[][]{{"/snow", "snow", "Cover the ground with snow"}, {"/thaw", "thaw", "Melt snow and ice"},
                {"/green", "green", "Turn dirt into grass"}}) {
            c.add("utility", s[0], "<radius>", s[2], 1, (p, a) -> {
                int r = c.radius(a[0]);
                BlockVec h = here(p);
                Region region = new CylinderRegion(p.getWorld(), h.x(), h.z(), r, r, h.y() - r, h.y() + r, false);
                edits.submit(p, s[1], region.boxVolume(), b -> ops.surface(region, s[1], b));
            }, null);
        }
        c.add("utility", "/ex", "<radius>", "Put out fires", 1, (p, a) -> {
            int r = c.radius(a[0]);
            Region region = new EllipsoidRegion(p.getWorld(), here(p), r, r, r, false);
            edits.submit(p, "extinguish", region.boxVolume(), b -> ops.surface(region, "ex", b));
        }, null, "/extinguish", "/ext");

        c.add("utility", "/replacenear", "<radius> <from-mask> <to-pattern>", "Replace blocks near you", 3, (p, a) -> {
            int r = c.radius(a[0]);
            Mask m = c.mask(a[1]);
            Pattern pat = c.pattern(p, a[2]);
            BlockVec h = here(p);
            Region region = CuboidRegion.of(p.getWorld(), h.add(-r, -r, -r), h.add(r, r, r));
            edits.submit(p, "replacenear", region.boxVolume(), b -> ops.fill(region, pat, m, "replacenear", b));
        }, (p, a) -> a.length == 2 ? Commands.masks() : a.length == 3 ? Commands.blocks() : List.of("5", "10"));

        c.add("utility", "removenear", "<mask> [radius]", "Remove matching blocks near you", 1, (p, a) -> {
            Mask m = c.mask(a[0]);
            int r = a.length > 1 ? c.radius(a[1]) : 50;
            BlockVec h = here(p);
            Region region = CuboidRegion.of(p.getWorld(), h.add(-r, -r, -r), h.add(r, r, r));
            edits.submit(p, "removenear", region.boxVolume(), b -> ops.fill(region, (x, y, z) -> Blocks.AIR, m, "removenear", b));
        }, (p, a) -> a.length == 1 ? Commands.masks() : List.of("10", "50"), "/removenear");

        c.add("utility", "removeabove", "[size] [height]", "Clear blocks above your head", 0, (p, a) -> {
            int size = a.length > 0 ? c.radius(a[0]) : 1;
            BlockVec h = here(p);
            int top = a.length > 1 ? h.y() + 1 + Commands.integer(a[1], "Height") : p.getWorld().getMaxHeight() - 1;
            Region region = CuboidRegion.of(p.getWorld(), new BlockVec(h.x() - size + 1, h.y() + 2, h.z() - size + 1), new BlockVec(h.x() + size - 1, top, h.z() + size - 1));
            edits.submit(p, "removeabove", region.boxVolume(), b -> ops.fill(region, (x, y, z) -> Blocks.AIR, Mask.ALL, "removeabove", b));
        }, null, "/removeabove");

        c.add("utility", "removebelow", "[size] [depth]", "Clear blocks below you", 0, (p, a) -> {
            int size = a.length > 0 ? c.radius(a[0]) : 1;
            BlockVec h = here(p);
            int bottom = a.length > 1 ? h.y() - Commands.integer(a[1], "Depth") : p.getWorld().getMinHeight();
            Region region = CuboidRegion.of(p.getWorld(), new BlockVec(h.x() - size + 1, bottom, h.z() - size + 1), new BlockVec(h.x() + size - 1, h.y() - 1, h.z() + size - 1));
            edits.submit(p, "removebelow", region.boxVolume(), b -> ops.fill(region, (x, y, z) -> Blocks.AIR, Mask.ALL, "removebelow", b));
        }, null, "/removebelow");

        c.add("utility", "butcher", "[radius] [-a] [-n]", "Kill hostile mobs near you (-a animals too, -n named too)", 0, (p, a) -> {
            String[] pos = Commands.positional(a);
            int r = pos.length > 0 ? c.radius(pos[0]) : plugin.getConfig().getInt("butcher-default-radius", 50);
            boolean animals = Commands.flag(a, "-a"), named = Commands.flag(a, "-n");
            int n = 0;
            List<Entity> nearby;
            try {
                nearby = p.getNearbyEntities(r, r, r);
            } catch (RuntimeException e) {
                nearby = p.getNearbyEntities(16, 16, 16); // Folia: box crossed into another region - stay local
                Msg.send(p, "&7Only checked 16 blocks around you here.");
            }
            for (Entity e : nearby) {
                if (e instanceof Player) continue;
                boolean hostile = e instanceof Enemy;
                boolean animal = e instanceof Animals || e instanceof WaterMob || e instanceof Ambient;
                if (!hostile && !(animals && animal)) continue;
                if (!named && isNamed(e)) continue;
                if (e instanceof Tameable t && t.isTamed() && !named) continue;
                e.remove();
                n++;
            }
            Msg.send(p, "&dKilled &f" + n + "&d mobs.");
        }, null, "/butcher");

        c.add("utility", "/limit", "", "Your block limit per edit", 0, (p, a) -> {
            long l = org.mydreamduels.dreamworldedit.session.Limits.of(plugin, p);
            Msg.send(p, "&7Your limit: &f" + (l == Long.MAX_VALUE ? "unlimited" : l + " blocks"));
        }, null);
    }

    @SuppressWarnings("deprecation")
    private static boolean isNamed(Entity e) {
        return e.getCustomName() != null;
    }

    private static WeCommand.Completer shapeTab() {
        return (p, a) -> a.length == 1 ? Commands.blocks() : a.length == 2 ? List.of("3", "5", "10") : List.of("-r");
    }

    private static void sphere(Commands c, Player p, String[] a, boolean hollow) {
        String[] pos = Commands.positional(a);
        Pattern pat = c.pattern(p, pos[0]);
        String[] radii = pos[1].split(",");
        double rx = c.radius(radii[0]);
        double ry = radii.length > 1 ? c.radius(radii[1]) : rx;
        double rz = radii.length > 2 ? c.radius(radii[2]) : rx;
        BlockVec center = BlockVec.of(p.getLocation());
        if (Commands.flag(a, "-r")) center = center.add(0, (int) ry, 0);
        Region region = new EllipsoidRegion(p.getWorld(), center, rx, ry, rz, hollow);
        c.plugin().edits().submit(p, hollow ? "hsphere" : "sphere", region.boxVolume(),
                b -> c.plugin().ops().fill(region, pat, Mask.ALL, hollow ? "hsphere" : "sphere", b));
    }

    private static void cyl(Commands c, Player p, String[] a, boolean hollow) {
        Pattern pat = c.pattern(p, a[0]);
        String[] radii = a[1].split(",");
        double rx = c.radius(radii[0]);
        double rz = radii.length > 1 ? c.radius(radii[1]) : rx;
        int h = a.length > 2 ? Commands.integer(a[2], "Height") : 1;
        if (h < 1) throw new WeCommand.Usage("Height must be at least 1.");
        BlockVec base = BlockVec.of(p.getLocation());
        Region region = new CylinderRegion(p.getWorld(), base.x(), base.z(), rx, rz, base.y(), base.y() + h - 1, hollow);
        c.plugin().edits().submit(p, hollow ? "hcyl" : "cyl", region.boxVolume(),
                b -> c.plugin().ops().fill(region, pat, Mask.ALL, hollow ? "hcyl" : "cyl", b));
    }

    private static void pyramid(Commands c, Player p, String[] a, boolean hollow) {
        Pattern pat = c.pattern(p, a[0]);
        int size = c.radius(a[1]);
        BlockVec base = BlockVec.of(p.getLocation());
        World w = p.getWorld();
        Region region = new Region() {
            public World world() { return w; }
            public BlockVec min() { return base.add(-size + 1, 0, -size + 1); }
            public BlockVec max() { return base.add(size - 1, size - 1, size - 1); }
            public long volume() { return countVolume(); }
            public String describe() { return "pyramid"; }
            public boolean contains(int x, int y, int z) {
                int layer = y - base.y();
                if (layer < 0 || layer >= size) return false;
                int half = size - 1 - layer;
                int dx = Math.abs(x - base.x()), dz = Math.abs(z - base.z());
                if (dx > half || dz > half) return false;
                return !hollow || dx == half || dz == half || layer == 0;
            }
        };
        c.plugin().edits().submit(p, hollow ? "hpyramid" : "pyramid", region.boxVolume(),
                b -> c.plugin().ops().fill(region, pat, Mask.ALL, "pyramid", b));
    }

    @SuppressWarnings("deprecation")
    private static Biome biome(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        Biome b = Registry.BIOME.get(NamespacedKey.minecraft(key));
        if (b == null) throw new WeCommand.Usage("Unknown biome '" + name + "'. See /biomelist");
        return b;
    }

    @SuppressWarnings("deprecation")
    private static List<String> biomeNames() {
        List<String> out = new ArrayList<>();
        for (Biome b : Registry.BIOME) out.add(b.getKey().getKey());
        out.sort(String::compareTo);
        return out;
    }

}
