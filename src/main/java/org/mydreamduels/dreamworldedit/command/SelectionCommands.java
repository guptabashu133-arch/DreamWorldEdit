package org.mydreamduels.dreamworldedit.command;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.region.Selection;
import org.mydreamduels.dreamworldedit.region.SelectionMode;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Direction;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** //wand //pos1 //pos2 //hpos1 //hpos2 //sel //desel //expand //contract //shift //outset //inset //size //count //distr //chunk */
public final class SelectionCommands {

    private SelectionCommands() {
    }

    public static Material wand(DreamWorldEdit plugin) {
        Material m = Material.matchMaterial(plugin.getConfig().getString("wand-item", "WOODEN_AXE"));
        return m == null ? Material.WOODEN_AXE : m;
    }

    /** Shared by the commands and the wand listener. */
    public static void setPos(DreamWorldEdit plugin, Player p, int which, BlockVec pos) {
        Selection sel = plugin.sessions().get(p).selection();
        World w = p.getWorld();
        if (which == 1) {
            sel.setPos1(w, pos);
        } else if (!sel.setPos2(w, pos)) {
            Msg.err(p, "Set the first point with left-click / //pos1 first.");
            return;
        }
        Region r = sel.region();
        String extra = "";
        if (sel.mode() == SelectionMode.POLY) extra = " &8(" + sel.polyPoints() + " points)";
        else if (r != null) extra = " &8(" + r.boxVolume() + " blocks)";
        Msg.send(p, "&d" + (which == 1 ? "First" : "Second") + " position set to &f" + pos + extra);
        if (r != null) plugin.visualizer().show(p, r);
    }

    private static Region cuboid(Commands c, Player p) {
        Region r = c.selection(p);
        if (c.plugin().sessions().get(p).selection().mode() != SelectionMode.CUBOID) {
            throw new WeCommand.Usage("That only works with a cuboid selection (//sel cuboid).");
        }
        return r;
    }

    public static void register(Commands c) {
        DreamWorldEdit plugin = c.plugin();

        c.add("selection", "/wand", "", "Get the selection wand", 0, (p, a) -> {
            p.getInventory().addItem(new ItemStack(wand(plugin)));
            Msg.send(p, "&dLeft-click: first position, right-click: second position.");
        }, null);

        c.add("selection", "/pos1", "", "Set the first position where you stand", 0,
                (p, a) -> setPos(plugin, p, 1, BlockVec.of(p.getLocation())), null);
        c.add("selection", "/pos2", "", "Set the second position where you stand", 0,
                (p, a) -> setPos(plugin, p, 2, BlockVec.of(p.getLocation())), null);
        c.add("selection", "/hpos1", "", "First position = block you look at", 0, (p, a) -> {
            Block b = p.getTargetBlockExact(300);
            if (b == null) throw new WeCommand.Usage("No block in sight.");
            setPos(plugin, p, 1, BlockVec.of(b.getLocation()));
        }, null);
        c.add("selection", "/hpos2", "", "Second position = block you look at", 0, (p, a) -> {
            Block b = p.getTargetBlockExact(300);
            if (b == null) throw new WeCommand.Usage("No block in sight.");
            setPos(plugin, p, 2, BlockVec.of(b.getLocation()));
        }, null);

        c.add("selection", "/sel", "<cuboid|poly|sphere|ellipsoid|cyl>", "Change the selection shape", 0, (p, a) -> {
            Selection sel = plugin.sessions().get(p).selection();
            if (a.length == 0) {
                Msg.send(p, "&7Selection mode: &e" + sel.mode().name().toLowerCase(Locale.ROOT));
                return;
            }
            SelectionMode mode = switch (a[0].toLowerCase(Locale.ROOT)) {
                case "cuboid", "cube" -> SelectionMode.CUBOID;
                case "poly", "polygon", "polygon2d" -> SelectionMode.POLY;
                case "sphere" -> SelectionMode.SPHERE;
                case "ellipsoid" -> SelectionMode.ELLIPSOID;
                case "cyl", "cylinder" -> SelectionMode.CYL;
                default -> throw new WeCommand.Usage("Unknown shape '" + a[0] + "'.");
            };
            sel.setMode(mode);
            Msg.send(p, "&dSelection mode: &f" + mode.name().toLowerCase(Locale.ROOT) + switch (mode) {
                case CUBOID -> " &7(left = corner 1, right = corner 2)";
                case POLY -> " &7(left = first point, right = more points)";
                case SPHERE, ELLIPSOID -> " &7(left = centre, right = radius)";
                case CYL -> " &7(left = centre, right = radius/height)";
            });
        }, (p, a) -> List.of("cuboid", "poly", "sphere", "ellipsoid", "cyl"), "/select");

        c.add("selection", "/desel", "", "Clear your selection", 0, (p, a) -> {
            plugin.sessions().get(p).selection().clear();
            Msg.send(p, "&dSelection cleared.");
        }, null, "/deselect");

        c.add("selection", "/expand", "<amount|vert> [direction]", "Grow the selection", 1, (p, a) -> {
            Selection sel = plugin.sessions().get(p).selection();
            if (a[0].equalsIgnoreCase("vert")) {
                Region r = c.selection(p);
                World w = p.getWorld();
                if (sel.mode() == SelectionMode.POLY) sel.setPolyHeight(w.getMinHeight(), w.getMaxHeight() - 1);
                else if (sel.mode() == SelectionMode.CUBOID)
                    sel.setCuboid(w, new BlockVec(r.min().x(), w.getMinHeight(), r.min().z()), new BlockVec(r.max().x(), w.getMaxHeight() - 1, r.max().z()));
                else throw new WeCommand.Usage("expand vert works with cuboid and poly selections.");
                Msg.send(p, "&dSelection expanded to the full world height.");
                return;
            }
            resize(c, p, Commands.integer(a[0], "Amount"), a.length > 1 ? Commands.direction(p, a[1]) : Direction.facing(p.getLocation()));
        }, (p, a) -> a.length == 1 ? List.of("vert", "1", "5", "10") : Direction.NAMES);

        c.add("selection", "/contract", "<amount> [direction]", "Shrink the selection", 1, (p, a) ->
                resize(c, p, -Commands.integer(a[0], "Amount"), a.length > 1 ? Commands.direction(p, a[1]) : Direction.facing(p.getLocation())),
                (p, a) -> a.length == 2 ? Direction.NAMES : List.of());

        c.add("selection", "/shift", "<amount> [direction]", "Move the selection (not the blocks)", 1, (p, a) -> {
            c.selection(p);
            int n = Commands.integer(a[0], "Amount");
            Direction d = a.length > 1 ? Commands.direction(p, a[1]) : Direction.facing(p.getLocation());
            plugin.sessions().get(p).selection().shift(d.dx * n, d.dy * n, d.dz * n);
            Msg.send(p, "&dSelection shifted " + n + " " + d.name().toLowerCase(Locale.ROOT) + ".");
        }, (p, a) -> a.length == 2 ? Direction.NAMES : List.of());

        c.add("selection", "/outset", "<amount> [-h|-v]", "Grow the selection on every side", 1, (p, a) -> outset(c, p, a, 1), null);
        c.add("selection", "/inset", "<amount> [-h|-v]", "Shrink the selection on every side", 1, (p, a) -> outset(c, p, a, -1), null);

        c.add("selection", "/size", "", "Size of your selection", 0, (p, a) -> {
            Region r = c.selection(p);
            Msg.send(p, "&7Shape: &f" + r.describe());
            Msg.send(p, "&7Size: &f" + r.width() + " x " + r.height() + " x " + r.length() + " &7Blocks: &f" + r.volume());
        }, null);

        c.add("selection", "/count", "<mask>", "Count matching blocks in the selection", 1, (p, a) -> {
            Region r = c.selection(p);
            Mask m = c.mask(String.join(" ", a));
            plugin.ops().distribution(r, m).thenAccept(map -> {
                long total = map.values().stream().mapToLong(AtomicLong::get).sum();
                Msg.send(p, "&dCounted: &f" + total);
            });
        }, (p, a) -> Commands.masks());

        c.add("selection", "/distr", "", "Which blocks the selection is made of", 0, (p, a) -> {
            Region r = c.selection(p);
            plugin.ops().distribution(r, Mask.ALL).thenAccept(map -> {
                long total = map.values().stream().mapToLong(AtomicLong::get).sum();
                List<Map.Entry<Material, AtomicLong>> list = new ArrayList<>(map.entrySet());
                list.sort(Comparator.comparingLong((Map.Entry<Material, AtomicLong> e) -> e.getValue().get()).reversed());
                Msg.send(p, "&d" + total + " blocks:");
                for (int i = 0; i < Math.min(15, list.size()); i++) {
                    var e = list.get(i);
                    Msg.raw(p, String.format(Locale.ROOT, "&f%-10d &7(%.2f%%) &e%s", e.getValue().get(),
                            total == 0 ? 0 : e.getValue().get() * 100.0 / total, e.getKey().name().toLowerCase(Locale.ROOT)));
                }
            });
        }, null);

        c.add("selection", "/chunk", "", "Select the chunk you're in", 0, (p, a) -> {
            int cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
            World w = p.getWorld();
            plugin.sessions().get(p).selection().setCuboid(w, new BlockVec(cx << 4, w.getMinHeight(), cz << 4),
                    new BlockVec((cx << 4) + 15, w.getMaxHeight() - 1, (cz << 4) + 15));
            Msg.send(p, "&dChunk " + cx + ", " + cz + " selected.");
        }, null);
    }

    private static void resize(Commands c, Player p, int amount, Direction d) {
        Region r = cuboid(c, p);
        BlockVec min = r.min(), max = r.max();
        switch (d) {
            case EAST -> max = max.add(amount, 0, 0);
            case WEST -> min = min.add(-amount, 0, 0);
            case SOUTH -> max = max.add(0, 0, amount);
            case NORTH -> min = min.add(0, 0, -amount);
            case UP -> max = max.add(0, amount, 0);
            case DOWN -> min = min.add(0, -amount, 0);
        }
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) throw new WeCommand.Usage("That would make the selection empty.");
        c.plugin().sessions().get(p).selection().setCuboid(p.getWorld(), min, max);
        Region now = c.selection(p);
        Msg.send(p, "&dSelection " + (amount >= 0 ? "expanded" : "contracted") + " " + Math.abs(amount) + " "
                + d.name().toLowerCase(Locale.ROOT) + " &8(" + now.boxVolume() + " blocks)");
    }

    private static void outset(Commands c, Player p, String[] a, int sign) {
        Region r = cuboid(c, p);
        int n = Commands.integer(a[0], "Amount") * sign;
        boolean h = !Commands.flag(a, "-v"), v = !Commands.flag(a, "-h");
        BlockVec min = r.min().add(h ? -n : 0, v ? -n : 0, h ? -n : 0);
        BlockVec max = r.max().add(h ? n : 0, v ? n : 0, h ? n : 0);
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) throw new WeCommand.Usage("That would make the selection empty.");
        c.plugin().sessions().get(p).selection().setCuboid(p.getWorld(), min, max);
        Msg.send(p, "&dSelection " + (sign > 0 ? "outset" : "inset") + " by " + Math.abs(n) + ".");
    }
}
