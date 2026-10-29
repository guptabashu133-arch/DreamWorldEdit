package org.mydreamduels.dreamworldedit.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.pattern.Blocks;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.pattern.Patterns;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.util.Direction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Registry + shared helpers for every command group. */
public final class Commands {

    private final DreamWorldEdit plugin;
    private final List<WeCommand> all = new ArrayList<>();

    public Commands(DreamWorldEdit plugin) {
        this.plugin = plugin;
    }

    public DreamWorldEdit plugin() {
        return plugin;
    }

    public List<WeCommand> all() {
        return all;
    }

    /** name / aliases may start with "/" (typed as //name). */
    public void add(String group, String name, String usage, String desc, int minArgs,
                    WeCommand.Handler handler, WeCommand.Completer completer, String... aliases) {
        all.add(new WeCommand(group, name, Arrays.asList(aliases), usage, desc, minArgs, handler, completer));
    }

    public void registerAll() {
        CommandMap map = commandMap();
        if (map == null) {
            plugin.getLogger().severe("Could not access the server's command map - no commands registered!");
            return;
        }
        for (WeCommand c : all) map.register("dreamworldedit", c);
    }

    private CommandMap commandMap() {
        try {
            return Bukkit.getCommandMap(); // Paper / Folia
        } catch (NoSuchMethodError e) {
            try {
                Method m = Bukkit.getServer().getClass().getMethod("getCommandMap"); // Spigot
                return (CommandMap) m.invoke(Bukkit.getServer());
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ helpers used by the groups

    public Region selection(Player p) {
        Region r = plugin.sessions().get(p).selection().region();
        if (r == null) throw new WeCommand.Usage("Make a selection first (//wand, //pos1, //pos2).");
        if (!r.world().equals(p.getWorld())) throw new WeCommand.Usage("Your selection is in another world.");
        return r;
    }

    public Pattern pattern(Player p, String text) {
        return Patterns.parse(text, p, plugin.sessions().get(p).clipboard());
    }

    public Mask mask(String text) {
        return Patterns.parseMask(text);
    }

    public static int integer(String s, String what) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new WeCommand.Usage(what + " must be a whole number.");
        }
    }

    public static double number(String s, String what) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new WeCommand.Usage(what + " must be a number.");
        }
    }

    public int radius(String s) {
        int r = integer(s, "Radius");
        int max = plugin.getConfig().getInt("max-radius", 100);
        if (r < 0 || r > max) throw new WeCommand.Usage("Radius must be between 0 and " + max + ".");
        return r;
    }

    public static Direction direction(Player p, String text) {
        Direction d = Direction.parse(text, p.getLocation());
        if (d == null) throw new WeCommand.Usage("Unknown direction '" + text + "'.");
        return d;
    }

    public static boolean flag(String[] args, String flag) {
        for (String a : args) if (a.equalsIgnoreCase(flag)) return true;
        return false;
    }

    /** args without flags (anything starting with '-' that isn't a negative number). */
    public static String[] positional(String[] args) {
        List<String> out = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("-") && a.length() > 1 && !Character.isDigit(a.charAt(1))) continue;
            out.add(a);
        }
        return out.toArray(new String[0]);
    }

    /** Value after a flag like "-m mask", or null. */
    public static String flagValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equalsIgnoreCase(flag)) return args[i + 1];
        return null;
    }

    public static String[] without(String[] args, String flag) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                i++;
                continue;
            }
            out.add(args[i]);
        }
        return out.toArray(new String[0]);
    }

    public static List<String> blocks() {
        return Blocks.names();
    }

    public static List<String> masks() {
        List<String> out = new ArrayList<>(List.of("#existing", "#solid", "#liquid", "air", "!air"));
        out.addAll(Blocks.names());
        return out;
    }
}
