package org.mydreamduels.dreamworldedit.command;

import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.clipboard.Clipboard;
import org.mydreamduels.dreamworldedit.clipboard.SchematicIO;
import org.mydreamduels.dreamworldedit.pattern.Blocks;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.session.Session;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Direction;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** //copy //cut //paste //rotate //flip /clearclipboard //schem //undo //redo /clearhistory */
public final class ClipboardCommands {

    private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_\\-. ]{1,64}");

    private ClipboardCommands() {
    }

    private static Clipboard clipboard(DreamWorldEdit plugin, Player p) {
        Clipboard clip = plugin.sessions().get(p).clipboard();
        if (clip == null) throw new WeCommand.Usage("Your clipboard is empty - //copy or //schem load first.");
        return clip;
    }

    public static void register(Commands c) {
        DreamWorldEdit plugin = c.plugin();
        var ops = plugin.ops();

        c.add("clipboard", "/copy", "[-m mask]", "Copy the selection (relative to you)", 0, (p, a) -> {
            Region r = c.selection(p);
            String m = Commands.flagValue(a, "-m");
            Mask mask = m == null ? Mask.ALL : c.mask(m);
            long limit = org.mydreamduels.dreamworldedit.session.Limits.of(plugin, p);
            if (r.boxVolume() > limit) throw new WeCommand.Usage("That's " + r.boxVolume() + " blocks - your limit is " + limit + ".");
            ops.copy(r, BlockVec.of(p.getLocation()), mask).thenAccept(clip -> {
                plugin.sessions().get(p).setClipboard(clip);
                Msg.send(p, "&d" + clip.volume() + " blocks copied.");
            });
        }, (p, a) -> List.of("-m"));

        c.add("clipboard", "/cut", "[leftover-pattern] [-m mask]", "Copy the selection, then clear it", 0, (p, a) -> {
            Region r = c.selection(p);
            String m = Commands.flagValue(a, "-m");
            Mask mask = m == null ? Mask.ALL : c.mask(m);
            String[] rest = Commands.positional(Commands.without(a, "-m"));
            Pattern leftover = rest.length > 0 ? c.pattern(p, rest[0]) : (x, y, z) -> Blocks.AIR;
            BlockVec origin = BlockVec.of(p.getLocation());
            plugin.edits().submit(p, "cut", r.boxVolume(), budget -> ops.copy(r, origin, mask).thenCompose(clip -> {
                plugin.sessions().get(p).setClipboard(clip);
                return ops.fill(r, leftover, mask, "cut", budget);
            }));
        }, (p, a) -> Commands.blocks());

        c.add("clipboard", "/paste", "[-a] [-s] [-o]", "Paste (-a skip air, -s select it, -o at original spot)", 0, (p, a) -> {
            Clipboard clip = clipboard(plugin, p);
            boolean skipAir = Commands.flag(a, "-a"), select = Commands.flag(a, "-s"), original = Commands.flag(a, "-o");
            BlockVec at = BlockVec.of(p.getLocation());
            if (original) {
                if (clip.origin() == null) throw new WeCommand.Usage("This clipboard has no original position (loaded from a file).");
                at = clip.origin();
            }
            BlockVec target = at;
            plugin.edits().submit(p, "paste", clip.volume(), budget ->
                    ops.paste(p.getWorld(), clip, List.of(target), skipAir, null, null, "paste", budget).thenApply(res -> {
                        if (select) {
                            BlockVec lo = target.add(clip.offset());
                            plugin.sessions().get(p).selection().setCuboid(p.getWorld(), lo, lo.add(clip.width() - 1, clip.height() - 1, clip.length() - 1));
                        }
                        return res;
                    }));
        }, (p, a) -> List.of("-a", "-s", "-o"));

        c.add("clipboard", "/rotate", "<90|180|270|-90>", "Rotate the clipboard", 1, (p, a) -> {
            int deg = Commands.integer(a[0], "Degrees");
            if (deg % 90 != 0) throw new WeCommand.Usage("Only multiples of 90 degrees are supported.");
            Session s = plugin.sessions().get(p);
            s.setClipboard(clipboard(plugin, p).rotate(deg));
            Msg.send(p, "&dClipboard rotated " + deg + " degrees.");
        }, (p, a) -> List.of("90", "180", "270", "-90"));

        c.add("clipboard", "/flip", "[direction]", "Mirror the clipboard", 0, (p, a) -> {
            Direction d = a.length > 0 ? Commands.direction(p, a[0]) : Direction.facing(p.getLocation());
            Session s = plugin.sessions().get(p);
            s.setClipboard(clipboard(plugin, p).flip(d));
            Msg.send(p, "&dClipboard flipped " + d.name().toLowerCase(Locale.ROOT) + ".");
        }, (p, a) -> org.mydreamduels.dreamworldedit.util.Direction.NAMES);

        c.add("clipboard", "clearclipboard", "", "Empty your clipboard", 0, (p, a) -> {
            plugin.sessions().get(p).setClipboard(null);
            Msg.send(p, "&dClipboard cleared.");
        }, null, "/clearclipboard");

        // ------------------------------------------------------------ schematics
        c.add("schematic", "/schem", "<save|load|list|delete> [name]", "Schematic files (.schem)", 1, (p, a) -> {
            String sub = a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "list", "ls" -> {
                    List<String> names = new ArrayList<>();
                    for (File dir : folders(plugin)) {
                        File[] files = dir.listFiles((d, n) -> n.endsWith(".schem") || n.endsWith(".schematic"));
                        if (files == null) continue;
                        for (File f : files) {
                            String n = f.getName().replaceFirst("\\.schem(atic)?$", "");
                            if (!names.contains(n)) names.add(n);
                        }
                    }
                    names.sort(String.CASE_INSENSITIVE_ORDER);
                    Msg.send(p, names.isEmpty() ? "&7No schematics yet - put .schem files in plugins/DreamWorldEdit/schematics."
                            : "&7Schematics (" + names.size() + "): &f" + String.join("&7, &f", names));
                }
                case "save" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Give a name.");
                    String name = name(a[1]);
                    Clipboard clip = clipboard(plugin, p);
                    File file = new File(own(plugin), name + ".schem");
                    if (file.exists() && !Commands.flag(a, "-f")) throw new WeCommand.Usage("'" + name + "' already exists - add -f to overwrite.");
                    plugin.scheduler().runAsync(() -> {
                        try {
                            SchematicIO.save(clip, file);
                            Msg.send(p, "&dSaved schematic &f" + name + "&d (" + clip.volume() + " blocks).");
                        } catch (IOException e) {
                            Msg.err(p, "Could not save: " + e.getMessage());
                        }
                    });
                }
                case "load" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Give a name.");
                    File file = find(plugin, name(a[1]));
                    if (file == null) throw new WeCommand.Usage("No schematic called '" + a[1] + "'. See //schem list");
                    plugin.scheduler().runAsync(() -> {
                        try {
                            SchematicIO.Loaded loaded = SchematicIO.load(file);
                            plugin.sessions().get(p).setClipboard(loaded.clipboard());
                            Msg.send(p, "&dLoaded &f" + file.getName() + "&d (" + loaded.clipboard().width() + "x" + loaded.clipboard().height()
                                    + "x" + loaded.clipboard().length() + "). Use //paste.");
                            if (loaded.unknownBlocks() > 0) {
                                Msg.err(p, loaded.unknownBlocks() + " block type(s) from another Minecraft version were unknown and became air.");
                            }
                        } catch (IOException | RuntimeException e) {
                            Msg.err(p, "Could not load " + file.getName() + ": " + e.getMessage());
                        }
                    });
                }
                case "delete", "del" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Give a name.");
                    File file = new File(own(plugin), name(a[1]) + ".schem");
                    if (!file.isFile()) throw new WeCommand.Usage("No schematic called '" + a[1] + "' in DreamWorldEdit's folder.");
                    Msg.send(p, file.delete() ? "&dDeleted " + a[1] + "." : "&cCould not delete " + a[1] + ".");
                }
                default -> throw new WeCommand.Usage(null);
            }
        }, (p, a) -> {
            if (a.length == 1) return List.of("save", "load", "list", "delete");
            List<String> names = new ArrayList<>();
            for (File dir : folders(plugin)) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".schem"));
                if (files != null) for (File f : files) names.add(f.getName().replaceFirst("\\.schem$", ""));
            }
            return names;
        }, "/schematic", "schem", "schematic");

        // ------------------------------------------------------------ history
        c.add("history", "/undo", "[times]", "Undo your last edit(s)", 0,
                (p, a) -> plugin.edits().undo(p, a.length > 0 ? Commands.integer(a[0], "Times") : 1), null, "undo");
        c.add("history", "/redo", "[times]", "Redo what you undid", 0,
                (p, a) -> plugin.edits().redo(p, a.length > 0 ? Commands.integer(a[0], "Times") : 1), null, "redo");
        c.add("history", "clearhistory", "", "Forget your undo history", 0, (p, a) -> {
            plugin.sessions().get(p).clearHistory();
            Msg.send(p, "&dHistory cleared.");
        }, null, "/clearhistory");
    }

    private static String name(String raw) {
        String n = raw.replaceFirst("\\.schem(atic)?$", "");
        if (!SAFE_NAME.matcher(n).matches() || n.contains("..")) throw new WeCommand.Usage("Schematic names may use letters, numbers, spaces, _ - and .");
        return n;
    }

    private static File own(DreamWorldEdit plugin) {
        File dir = new File(plugin.getDataFolder(), "schematics");
        dir.mkdirs();
        return dir;
    }

    /** Ours first, then WorldEdit/FAWE folders so existing schematics just work. */
    private static List<File> folders(DreamWorldEdit plugin) {
        List<File> out = new ArrayList<>();
        out.add(own(plugin));
        File root = plugin.getDataFolder().getParentFile().getParentFile();
        for (String extra : plugin.getConfig().getStringList("schematics.extra-folders")) {
            File f = new File(root, extra);
            if (f.isDirectory()) out.add(f);
        }
        return out;
    }

    private static File find(DreamWorldEdit plugin, String name) {
        for (File dir : folders(plugin)) {
            for (String ext : new String[]{".schem", ".schematic", ""}) {
                File f = new File(dir, name + ext);
                if (f.isFile()) return f;
            }
        }
        return null;
    }
}
