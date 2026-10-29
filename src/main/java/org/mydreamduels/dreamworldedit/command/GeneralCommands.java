package org.mydreamduels.dreamworldedit.command;

import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.platform.PlatformSupport;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /dwe help|reload|version and //help */
public final class GeneralCommands {

    private GeneralCommands() {
    }

    public static void register(Commands c) {
        DreamWorldEdit plugin = c.plugin();
        WeCommand.Handler help = (p, a) -> {
            Map<String, List<WeCommand>> groups = new LinkedHashMap<>();
            for (WeCommand cmd : c.all()) groups.computeIfAbsent(cmd.group, k -> new ArrayList<>()).add(cmd);
            String want = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : null;
            if (want == null) {
                Msg.raw(p, "&8&m     &r &dDreamWorldEdit &8&m     ");
                for (Map.Entry<String, List<WeCommand>> e : groups.entrySet()) {
                    List<String> names = new ArrayList<>();
                    for (WeCommand cmd : e.getValue()) names.add("/" + cmd.getName());
                    Msg.raw(p, "&d" + e.getKey() + ": &f" + String.join("&7, &f", names));
                }
                Msg.raw(p, "&7Details: &e//help <group>&7 (selection, region, generation, utility, clipboard, schematic, history, brush, navigation)");
                return;
            }
            List<WeCommand> list = groups.get(want);
            if (list == null) throw new WeCommand.Usage("Unknown group '" + a[0] + "'.");
            Msg.raw(p, "&8&m     &r &d" + want + " &8&m     ");
            for (WeCommand cmd : list) Msg.raw(p, "&e" + cmd.getUsage() + " &8- &7" + cmd.getDescription());
        };
        c.add("general", "/help", "[group]", "List commands", 0, help,
                (p, a) -> List.of("selection", "region", "generation", "utility", "clipboard", "schematic", "history", "brush", "navigation"));

        c.add("general", "dreamworldedit", "<reload|version|help>", "Plugin info and reload", 0, (p, a) -> {
            String sub = a.length == 0 ? "version" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "reload" -> {
                    if (!p.hasPermission("dreamworldedit.admin")) throw new WeCommand.Usage("You don't have permission.");
                    plugin.reloadConfig();
                    Msg.setPrefix(plugin.getConfig().getString("prefix", "&8[&dDWE&8] &r"));
                    Msg.send(p, "&dConfig reloaded.");
                }
                case "help" -> help.run(p, new String[0]);
                default -> {
                    @SuppressWarnings("deprecation") String v = plugin.getDescription().getVersion();
                    Msg.send(p, "&dDreamWorldEdit &f" + v + " &7on &f" + PlatformSupport.name()
                            + " &7- queued chunk jobs: &f" + plugin.engine().queued());
                }
            }
        }, (p, a) -> List.of("reload", "version", "help"), "dwe", "/dwe");
    }
}
