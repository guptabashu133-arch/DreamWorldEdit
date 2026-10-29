package org.mydreamduels.dreamworldedit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One WorldEdit-style command. Names starting with "/" are typed with two
 * slashes (//set), exactly like WorldEdit registers them.
 */
public final class WeCommand extends Command {

    @FunctionalInterface
    public interface Handler {
        void run(Player player, String[] args);
    }

    @FunctionalInterface
    public interface Completer {
        List<String> complete(Player player, String[] args);
    }

    /** Thrown by handlers: message (optional) then the usage line. */
    public static final class Usage extends RuntimeException {
        public Usage(String message) {
            super(message, null, false, false);
        }
    }

    final String group;
    private final int minArgs;
    private final Handler handler;
    private final Completer completer;

    WeCommand(String group, String name, List<String> aliases, String usage, String description, int minArgs,
              Handler handler, Completer completer) {
        super(name, description, "/" + name + (usage.isEmpty() ? "" : " " + usage), aliases);
        this.group = group;
        this.minArgs = minArgs;
        this.handler = handler;
        this.completer = completer;
        setPermission("dreamworldedit.command." + name.replace("/", ""));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can use that.");
            return true;
        }
        if (!testPermissionSilent(player) && !player.hasPermission("dreamworldedit.*")) {
            Msg.err(player, "You don't have permission (" + getPermission() + ").");
            return true;
        }
        if (args.length < minArgs) {
            Msg.raw(player, "&7Usage: &e" + getUsage());
            return true;
        }
        try {
            handler.run(player, args);
        } catch (Usage e) {
            if (e.getMessage() != null) Msg.err(player, e.getMessage());
            Msg.raw(player, "&7Usage: &e" + getUsage());
        } catch (IllegalArgumentException e) {
            Msg.err(player, e.getMessage());
        } catch (RuntimeException e) {
            // e.g. Folia refusing a look-up that reached into another region
            Msg.err(player, "That didn't work here: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.FINE, "DreamWorldEdit command " + getName() + " failed", e);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player player) || completer == null || args.length == 0) return List.of();
        List<String> all = completer.complete(player, args);
        if (all == null) return List.of();
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (s.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(s);
            if (out.size() >= 60) break;
        }
        return out;
    }
}
