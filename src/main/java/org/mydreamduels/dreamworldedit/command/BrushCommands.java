package org.mydreamduels.dreamworldedit.command;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.brush.Brush;
import org.mydreamduels.dreamworldedit.brush.Brushes;
import org.mydreamduels.dreamworldedit.clipboard.Clipboard;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.List;
import java.util.Locale;

/** /brush (/br) /mask /material /size /range /none */
public final class BrushCommands {

    private BrushCommands() {
    }

    private static Material held(Player p) {
        Material m = p.getInventory().getItemInMainHand().getType();
        if (m.isAir()) throw new WeCommand.Usage("Hold the item you want to use as the brush.");
        return m;
    }

    private static Brush current(DreamWorldEdit plugin, Player p) {
        Brush b = plugin.sessions().get(p).brushes().get(held(p));
        if (b == null) throw new WeCommand.Usage("There's no brush on this item - use /brush first.");
        return b;
    }

    private static double size(Commands c, String s) {
        double v = Commands.number(s, "Size");
        double max = c.plugin().getConfig().getDouble("max-brush-size", 25);
        if (v < 0 || v > max) throw new WeCommand.Usage("Brush size must be between 0 and " + (int) max + ".");
        return v;
    }

    public static void register(Commands c) {
        DreamWorldEdit plugin = c.plugin();

        c.add("brush", "brush", "<sphere|cylinder|smooth|gravity|butcher|paste> [args]", "Bind a brush to the item in your hand", 1, (p, a) -> {
            Material item = held(p);
            String type = a[0].toLowerCase(Locale.ROOT);
            String[] pos = Commands.positional(a);
            boolean hollow = Commands.flag(a, "-h");
            Brush brush = switch (type) {
                case "sphere", "s" -> {
                    if (pos.length < 2) throw new WeCommand.Usage("/brush sphere <pattern> [radius] [-h]");
                    Pattern pat = c.pattern(p, pos[1]);
                    yield new Brushes.Sphere(pat, pos[1], pos.length > 2 ? size(c, pos[2]) : 2, hollow);
                }
                case "cylinder", "cyl", "c" -> {
                    if (pos.length < 2) throw new WeCommand.Usage("/brush cylinder <pattern> [radius] [height] [-h]");
                    Pattern pat = c.pattern(p, pos[1]);
                    yield new Brushes.Cylinder(pat, pos[1], pos.length > 2 ? size(c, pos[2]) : 2,
                            pos.length > 3 ? Commands.integer(pos[3], "Height") : 1, hollow);
                }
                case "smooth" -> new Brushes.Smooth(pos.length > 1 ? size(c, pos[1]) : 2, pos.length > 2 ? Commands.integer(pos[2], "Iterations") : 4);
                case "gravity", "grav" -> new Brushes.Gravity(pos.length > 1 ? size(c, pos[1]) : 5);
                case "butcher", "kill" -> new Brushes.Butcher(pos.length > 1 ? size(c, pos[1]) : 5);
                case "paste", "clipboard" -> {
                    Clipboard clip = plugin.sessions().get(p).clipboard();
                    if (clip == null) throw new WeCommand.Usage("Copy something first.");
                    yield new Brushes.Paste(clip, Commands.flag(a, "-a"));
                }
                default -> throw new WeCommand.Usage("Unknown brush type '" + a[0] + "'.");
            };
            plugin.sessions().get(p).brushes().put(item, brush);
            Msg.send(p, "&d" + brush.describe() + " &dbrush bound to &f" + item.name().toLowerCase(Locale.ROOT) + "&d. Right-click to use.");
        }, (p, a) -> {
            if (a.length == 1) return List.of("sphere", "cylinder", "smooth", "gravity", "butcher", "paste");
            String t = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2 && (t.startsWith("s") && !t.equals("smooth") || t.startsWith("c"))) return Commands.blocks();
            return List.of("2", "3", "5", "-h", "-a");
        }, "br");

        c.add("brush", "mask", "[mask]", "Only let the brush change matching blocks (empty = clear)", 0, (p, a) -> {
            Brush b = current(plugin, p);
            if (a.length == 0) {
                b.mask = Mask.ALL;
                b.maskText = "";
                Msg.send(p, "&dBrush mask cleared.");
                return;
            }
            String text = String.join(" ", a);
            b.mask = c.mask(text);
            b.maskText = text;
            Msg.send(p, "&dBrush mask set to &f" + text);
        }, (p, a) -> Commands.masks(), "/mask");

        c.add("brush", "material", "<pattern>", "Change the brush's block", 1, (p, a) -> {
            Brush b = current(plugin, p);
            b.pattern = c.pattern(p, a[0]);
            b.patternText = a[0];
            Msg.send(p, "&dBrush material set to &f" + a[0]);
        }, (p, a) -> Commands.blocks(), "mat", "/material", "/mat");

        c.add("brush", "size", "<size>", "Change the brush size", 1, (p, a) -> {
            Brush b = current(plugin, p);
            b.size = size(c, a[0]);
            Msg.send(p, "&dBrush size set to &f" + a[0]);
        }, (p, a) -> List.of("1", "2", "3", "5", "10"));

        c.add("brush", "range", "<blocks>", "How far the brush reaches", 1, (p, a) -> {
            Brush b = current(plugin, p);
            b.range = Math.max(1, Math.min(300, Commands.integer(a[0], "Range")));
            Msg.send(p, "&dBrush range set to &f" + b.range);
        }, null);

        c.add("brush", "none", "", "Remove the brush from the item in your hand", 0, (p, a) -> {
            Material item = held(p);
            boolean removed = plugin.sessions().get(p).brushes().remove(item) != null;
            Msg.send(p, removed ? "&dBrush removed." : "&7There was no brush on this item.");
        }, null, "/none");
    }
}
