package org.mydreamduels.dreamworldedit.command;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Msg;
import org.mydreamduels.dreamworldedit.zone.Zone;
import org.mydreamduels.dreamworldedit.zone.ZoneManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /setspawn /spawn /delspawn and /zone (protected areas). */
public final class SpawnZoneCommands {

    private SpawnZoneCommands() {
    }

    /** The diamond-axe wand of /mosspit and /keepinv (left click = pos1, right click = pos2). */
    public static final String AREA_WAND_KEY = "area_wand";

    public static boolean isAreaWand(org.bukkit.plugin.Plugin plugin, org.bukkit.inventory.ItemStack it) {
        if (it == null || it.getType() != org.bukkit.Material.DIAMOND_AXE || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, AREA_WAND_KEY),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** /mosspit and /keepinv: wand, create <name> (zone with these rules), list, delete <name>. */
    private static void areaCommand(Commands c, String name, String desc, java.util.Map<Zone.Flag, Boolean> rules) {
        var plugin = c.plugin();
        c.add("spawn", name, "<wand|create <name>|list|delete <name>>", desc, 0, (p, a) -> {
            ZoneManager zm = plugin.zones();
            String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "wand" -> {
                    org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_AXE);
                    var meta = wand.getItemMeta();
                    meta.setDisplayName(Msg.color("&b&l" + (name.equals("mosspit") ? "Moss Pit" : "Keep Inventory") + " Wand"));
                    meta.setLore(List.of(Msg.color("&7Left click a block: &fcorner 1"), Msg.color("&7Right click a block: &fcorner 2"),
                            Msg.color("&7Then: &e/" + name + " create <name>")));
                    meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, AREA_WAND_KEY),
                            org.bukkit.persistence.PersistentDataType.STRING, name);
                    wand.setItemMeta(meta);
                    for (var left : p.getInventory().addItem(wand).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                    Msg.send(p, "&aWand given. &7Left click one corner, right click the opposite corner (include the full height), then &e/" + name + " create <name>&7.");
                }
                case "create", "define", "add" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Give it a name, e.g. /" + name + " create pit1");
                    String zoneName = a[1];
                    if (!NAME.matcher(zoneName).matches()) throw new WeCommand.Usage("Names: letters, numbers, _ and - (max 32).");
                    if (zm.get(zoneName) != null) throw new WeCommand.Usage("A zone called '" + zoneName + "' already exists.");
                    Region r = c.selection(p);
                    BlockVec lo = r.min(), hi = r.max();
                    Zone z = new Zone(zoneName, r.world().getName(), lo.x(), lo.y(), lo.z(), hi.x(), hi.y(), hi.z());
                    zm.add(z);
                    for (var e : rules.entrySet()) zm.setFlag(z, e.getKey(), e.getValue());
                    Msg.send(p, "&a" + (name.equals("mosspit") ? "Moss pit" : "Keep-inventory area") + " &f" + zoneName + " &acreated &7("
                            + (z.maxX() - z.minX() + 1) + " x " + (z.maxY() - z.minY() + 1) + " x " + (z.maxZ() - z.minZ() + 1) + ").");
                    showFlags(p, z);
                    Msg.raw(p, "&7 Change a rule: &e/zone flag " + zoneName + " <rule> on|off");
                }
                case "list", "ls" -> {
                    Msg.send(p, "&7Zones:");
                    for (Zone z : zm.zones()) Msg.raw(p, " &8- &f" + ZoneManager.describe(z));
                }
                case "delete", "remove", "del" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Which one?");
                    Zone z = zone(zm, a[1]);
                    zm.remove(z.name());
                    Msg.send(p, "&e" + z.name() + " &edeleted.");
                }
                default -> {
                    Msg.raw(p, "&8&m          &r &d/" + name + " &8&m          ");
                    Msg.raw(p, "&7" + desc + ".");
                    Msg.raw(p, "&e/" + name + " wand &7- a diamond axe: left click corner 1, right click corner 2");
                    Msg.raw(p, "&e/" + name + " create <name> &7- make the area from your selection");
                    Msg.raw(p, "&e/" + name + " list &8| &e/" + name + " delete <name>");
                }
            }
        }, (p, a) -> a.length == 1 ? List.of("wand", "create", "list", "delete")
                : a.length == 2 && a[0].equalsIgnoreCase("delete") ? plugin.zones().zones().stream().map(Zone::name).toList() : List.of());
    }

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,32}");

    public static void register(Commands c) {
        var plugin = c.plugin();

        // ------------------------------------------------------------------ spawn
        c.add("spawn", "setspawn", "", "Set the server spawn where you stand", 0, (p, a) -> {
            Location l = p.getLocation();
            plugin.spawns().set(l);
            Msg.send(p, "&aSpawn set &7at &f" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " &7in &f" + l.getWorld().getName() + "&7.");
            Msg.raw(p, "&7 New players start here and everyone respawns here after dying"
                    + (plugin.getConfig().getBoolean("spawn.bed-respawn", true) ? " &8(unless they slept in a bed)" : "") + "&7.");
            if (plugin.zones().at(l).isEmpty()) {
                Msg.raw(p, "&7 Tip: protect it - select the area with &e//wand&7, then &e/zone create spawn&7.");
            }
        }, (p, a) -> List.of(), "spawnset");

        c.add("spawn", "spawn", "", "Go to the server spawn", 0, (p, a) -> plugin.spawns().sendToSpawn(p), (p, a) -> List.of());

        c.add("spawn", "delspawn", "", "Remove the server spawn", 0, (p, a) -> {
            if (!plugin.spawns().isSet()) throw new WeCommand.Usage("There's no spawn set.");
            plugin.spawns().remove();
            Msg.send(p, "&eSpawn removed. &7Players now use the normal world spawn again.");
        }, (p, a) -> List.of());

        // ------------------------------------------------------------------ moss pit / keep-inventory areas
        areaCommand(c, "mosspit", "Moss pit: PvP on, no building, no wind charges, the only way out is dying",
                java.util.Map.ofEntries(java.util.Map.entry(Zone.Flag.PVP, true), java.util.Map.entry(Zone.Flag.BUILD, false),
                        java.util.Map.entry(Zone.Flag.CONTAINERS, false), java.util.Map.entry(Zone.Flag.DAMAGE, true),
                        java.util.Map.entry(Zone.Flag.HUNGER, true), java.util.Map.entry(Zone.Flag.PEARLS, false),
                        java.util.Map.entry(Zone.Flag.DROPS, true), java.util.Map.entry(Zone.Flag.PUSH, true),
                        java.util.Map.entry(Zone.Flag.WINDCHARGE, false), java.util.Map.entry(Zone.Flag.ESCAPE, false),
                        java.util.Map.entry(Zone.Flag.KEEPINV, false), java.util.Map.entry(Zone.Flag.MOBS, false),
                        java.util.Map.entry(Zone.Flag.EXPLOSIONS, false), java.util.Map.entry(Zone.Flag.FIRE, false)));
        areaCommand(c, "keepinv", "Keep-inventory area: PvP on, no building, dying here keeps your items",
                java.util.Map.ofEntries(java.util.Map.entry(Zone.Flag.PVP, true), java.util.Map.entry(Zone.Flag.BUILD, false),
                        java.util.Map.entry(Zone.Flag.CONTAINERS, false), java.util.Map.entry(Zone.Flag.DAMAGE, true),
                        java.util.Map.entry(Zone.Flag.HUNGER, true), java.util.Map.entry(Zone.Flag.DROPS, true),
                        java.util.Map.entry(Zone.Flag.PUSH, true), java.util.Map.entry(Zone.Flag.KEEPINV, true),
                        java.util.Map.entry(Zone.Flag.MOBS, false), java.util.Map.entry(Zone.Flag.EXPLOSIONS, false),
                        java.util.Map.entry(Zone.Flag.FIRE, false)));

        // ------------------------------------------------------------------ zones
        c.add("spawn", "zone", "<create|flag|info|list|select|redefine|delete> ...", "Protected areas: no pvp, no building...", 0, (p, a) -> {
            ZoneManager zm = plugin.zones();
            String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "create", "define", "add" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Give the zone a name, e.g. /zone create spawn");
                    String name = a[1];
                    if (!NAME.matcher(name).matches()) throw new WeCommand.Usage("Zone names: letters, numbers, _ and - (max 32).");
                    if (zm.get(name) != null) throw new WeCommand.Usage("A zone called '" + name + "' already exists. Use /zone redefine " + name + " to move it.");
                    Region r = c.selection(p);
                    BlockVec lo = r.min(), hi = r.max();
                    boolean full = a.length > 2 && a[2].equalsIgnoreCase("-full");
                    Zone z = new Zone(name, r.world().getName(), lo.x(), full ? r.world().getMinHeight() : lo.y(), lo.z(),
                            hi.x(), full ? r.world().getMaxHeight() - 1 : hi.y(), hi.z());
                    zm.add(z);
                    Msg.send(p, "&aZone &f" + name + " &acreated &7(" + (z.maxX() - z.minX() + 1) + " x " + (z.maxY() - z.minY() + 1) + " x " + (z.maxZ() - z.minZ() + 1) + ").");
                    showFlags(p, z);
                    Msg.raw(p, "&7 Change a rule: &e/zone flag " + name + " <rule> on|off");
                    if (!full) Msg.raw(p, "&8 Tip: add -full to cover the whole height of the world (sky to bedrock).");
                }
                case "redefine", "move", "resize" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Which zone?");
                    Zone old = zone(zm, a[1]);
                    Region r = c.selection(p);
                    BlockVec lo = r.min(), hi = r.max();
                    Zone z = new Zone(old.name(), r.world().getName(), lo.x(), lo.y(), lo.z(), hi.x(), hi.y(), hi.z());
                    z.copyFlags(old);
                    zm.add(z);
                    Msg.send(p, "&aZone &f" + z.name() + " &anow covers your selection. &7Its rules didn't change.");
                }
                case "flag", "set", "rule" -> {
                    if (a.length < 4) {
                        Msg.raw(p, "&7Usage: &e/zone flag <zone> <rule> on|off");
                        Msg.raw(p, "&7Rules: &f" + String.join("&7, &f", ZoneManager.flagIds()));
                        return;
                    }
                    Zone z = zone(zm, a[1]);
                    Zone.Flag f = Zone.Flag.parse(a[2]);
                    if (f == null) throw new WeCommand.Usage("Unknown rule '" + a[2] + "'. Rules: " + String.join(", ", ZoneManager.flagIds()));
                    Boolean on = switch (a[3].toLowerCase(Locale.ROOT)) {
                        case "on", "allow", "true", "yes", "enable" -> true;
                        case "off", "deny", "false", "no", "disable" -> false;
                        default -> null;
                    };
                    if (on == null) throw new WeCommand.Usage("Use on or off.");
                    zm.setFlag(z, f, on);
                    Msg.send(p, "&f" + f.id() + " &7in &f" + z.name() + "&7: " + (on ? "&aallowed" : "&cblocked") + "&7.");
                }
                case "info", "i" -> {
                    Zone z;
                    if (a.length >= 2) z = zone(zm, a[1]);
                    else {
                        List<Zone> here = zm.at(p.getLocation());
                        if (here.isEmpty()) {
                            Msg.send(p, "&7You're not in any zone. &8(/zone list)");
                            return;
                        }
                        z = here.get(0);
                    }
                    Msg.raw(p, "&8&m          &r &dZone &f" + z.name() + " &8&m          ");
                    Msg.raw(p, "&7 World &f" + z.world() + " &7from &f" + z.minX() + ", " + z.minY() + ", " + z.minZ() + " &7to &f" + z.maxX() + ", " + z.maxY() + ", " + z.maxZ());
                    showFlags(p, z);
                }
                case "list", "ls" -> {
                    List<Zone> all = zm.zones();
                    if (all.isEmpty()) {
                        Msg.send(p, "&7No zones yet. Select an area with &e//wand&7 and use &e/zone create <name>&7.");
                        return;
                    }
                    Msg.send(p, "&7Zones (" + all.size() + "):");
                    for (Zone z : all) Msg.raw(p, " &8- &f" + ZoneManager.describe(z));
                }
                case "select", "sel" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Which zone?");
                    Zone z = zone(zm, a[1]);
                    org.bukkit.World w = org.bukkit.Bukkit.getWorld(z.world());
                    if (w == null) throw new WeCommand.Usage("That zone's world isn't loaded.");
                    plugin.sessions().get(p).selection().setCuboid(w, new BlockVec(z.minX(), z.minY(), z.minZ()), new BlockVec(z.maxX(), z.maxY(), z.maxZ()));
                    Msg.send(p, "&aSelected zone &f" + z.name() + "&a.");
                }
                case "delete", "remove", "del" -> {
                    if (a.length < 2) throw new WeCommand.Usage("Which zone?");
                    Zone z = zone(zm, a[1]);
                    zm.remove(z.name());
                    Msg.send(p, "&eZone &f" + z.name() + " &edeleted. &7That area isn't protected any more.");
                }
                default -> {
                    Msg.raw(p, "&8&m          &r &dZones &8&m          ");
                    Msg.raw(p, "&e/zone create <name> &7- protect your //wand selection");
                    Msg.raw(p, "&e/zone flag <name> <rule> on|off &7- change a rule");
                    Msg.raw(p, "&e/zone info [name] &7- rules of a zone (or the one you're in)");
                    Msg.raw(p, "&e/zone list &8| &e/zone select <name> &8| &e/zone redefine <name> &8| &e/zone delete <name>");
                    Msg.raw(p, "&7Rules: &f" + String.join("&7, &f", ZoneManager.flagIds()));
                    Msg.raw(p, "&8Admins (dreamworldedit.zone.bypass) can still build and open chests in zones.");
                }
            }
        }, (p, a) -> {
            if (a.length == 1) return List.of("create", "flag", "info", "list", "select", "redefine", "delete");
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2 && !sub.equals("create") && !sub.equals("list")) {
                List<String> names = new ArrayList<>();
                for (Zone z : plugin.zones().zones()) names.add(z.name());
                return names;
            }
            if (a.length == 3 && sub.equals("create")) return List.of("-full");
            if (a.length == 3 && sub.equals("flag")) return ZoneManager.flagIds();
            if (a.length == 4 && sub.equals("flag")) return List.of("on", "off");
            return List.of();
        }, "rg", "protect");
    }

    private static Zone zone(ZoneManager zm, String name) {
        Zone z = zm.get(name);
        if (z == null) throw new WeCommand.Usage("No zone called '" + name + "'. See /zone list");
        return z;
    }

    private static void showFlags(Player p, Zone z) {
        StringBuilder sb = new StringBuilder("&7 Rules: ");
        boolean first = true;
        for (Zone.Flag f : Zone.Flag.values()) {
            if (!first) sb.append("&8, ");
            first = false;
            sb.append(z.allows(f) ? "&a" : "&c").append(f.id()).append(z.allows(f) ? " ✔" : " ✘");
        }
        Msg.raw(p, sb.toString());
        Msg.raw(p, "&8 (green = allowed, red = blocked)");
    }
}
