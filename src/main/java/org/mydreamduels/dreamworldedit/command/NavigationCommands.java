package org.mydreamduels.dreamworldedit.command;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mydreamduels.dreamworldedit.platform.PlatformSupport;
import org.mydreamduels.dreamworldedit.util.Direction;
import org.mydreamduels.dreamworldedit.util.Msg;

/**
 * /jumpto /thru /up /ascend /descend /unstuck /ceil - all run on the
 * player's own thread and only look at blocks right around them.
 */
public final class NavigationCommands {

    private NavigationCommands() {
    }

    private static void go(Player p, Location to) {
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        if (PlatformSupport.hasUnifiedSchedulers()) p.teleportAsync(to, PlayerTeleportEvent.TeleportCause.COMMAND);
        else p.teleport(to, PlayerTeleportEvent.TeleportCause.COMMAND);
    }

    private static boolean free(World w, int x, int y, int z) {
        return y > w.getMinHeight() && y < w.getMaxHeight() - 1
                && w.getBlockAt(x, y, z).isPassable() && w.getBlockAt(x, y + 1, z).isPassable();
    }

    private static boolean floor(World w, int x, int y, int z) {
        Material m = w.getBlockAt(x, y - 1, z).getType();
        return m.isSolid();
    }

    public static void register(Commands c) {
        c.add("navigation", "jumpto", "", "Teleport to the block you look at", 0, (p, a) -> {
            Block b = p.getTargetBlockExact(300);
            if (b == null) throw new WeCommand.Usage("No block in sight.");
            World w = p.getWorld();
            int y = b.getY() + 1;
            while (y < w.getMaxHeight() - 1 && !free(w, b.getX(), y, b.getZ())) y++;
            go(p, new Location(w, b.getX() + 0.5, y, b.getZ() + 0.5));
            Msg.send(p, "&dPoof!");
        }, null, "j", "/jumpto", "/j");

        c.add("navigation", "thru", "", "Pass through the wall in front of you", 0, (p, a) -> {
            World w = p.getWorld();
            Location l = p.getLocation();
            Direction d = Direction.facing(l);
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            boolean hitWall = false;
            for (int i = 1; i <= 16; i++) {
                int nx = x + d.dx * i, ny = y + d.dy * i, nz = z + d.dz * i;
                if (!free(w, nx, ny, nz)) {
                    hitWall = true;
                    continue;
                }
                if (hitWall) {
                    go(p, new Location(w, nx + 0.5, ny, nz + 0.5));
                    Msg.send(p, "&dWhoosh!");
                    return;
                }
            }
            Msg.err(p, "No free spot on the other side.");
        }, null, "/thru");

        c.add("navigation", "up", "<distance>", "Go up, standing on a glass block", 1, (p, a) -> {
            int n = Commands.integer(a[0], "Distance");
            World w = p.getWorld();
            Location l = p.getLocation();
            int y = l.getBlockY() + n;
            if (y < w.getMinHeight() + 1 || y > w.getMaxHeight() - 2) throw new WeCommand.Usage("That's outside the world.");
            Block under = w.getBlockAt(l.getBlockX(), y - 1, l.getBlockZ());
            if (under.getType().isAir()) under.setType(Material.GLASS, false);
            go(p, new Location(w, l.getBlockX() + 0.5, y, l.getBlockZ() + 0.5));
            Msg.send(p, "&dWhoosh!");
        }, null, "/up");

        c.add("navigation", "ascend", "[levels]", "Go up to the next floor", 0, (p, a) -> climb(p, a.length > 0 ? Commands.integer(a[0], "Levels") : 1, true),
                null, "asc", "/ascend", "/asc");
        c.add("navigation", "descend", "[levels]", "Go down to the next floor", 0, (p, a) -> climb(p, a.length > 0 ? Commands.integer(a[0], "Levels") : 1, false),
                null, "desc", "/descend", "/desc");

        c.add("navigation", "unstuck", "", "Get out of blocks", 0, (p, a) -> {
            World w = p.getWorld();
            Location l = p.getLocation();
            for (int y = l.getBlockY(); y < w.getMaxHeight() - 1; y++) {
                if (free(w, l.getBlockX(), y, l.getBlockZ())) {
                    go(p, new Location(w, l.getBlockX() + 0.5, y, l.getBlockZ() + 0.5));
                    Msg.send(p, "&dThere you go!");
                    return;
                }
            }
            Msg.err(p, "No free space above you.");
        }, null, "!", "/unstuck");

        c.add("navigation", "ceil", "[clearance]", "Go to the ceiling above you, on a glass block", 0, (p, a) -> {
            int clearance = a.length > 0 ? Commands.integer(a[0], "Clearance") : 0;
            World w = p.getWorld();
            Location l = p.getLocation();
            int x = l.getBlockX(), z = l.getBlockZ();
            for (int y = l.getBlockY() + 2; y < w.getMaxHeight(); y++) {
                if (!w.getBlockAt(x, y, z).isPassable()) {
                    int stand = y - 2 - clearance;
                    if (stand <= l.getBlockY()) break;
                    Block under = w.getBlockAt(x, stand - 1, z);
                    if (under.getType().isAir()) under.setType(Material.GLASS, false);
                    go(p, new Location(w, x + 0.5, stand, z + 0.5));
                    Msg.send(p, "&dWhoosh!");
                    return;
                }
            }
            Msg.err(p, "No ceiling found above you.");
        }, null, "/ceil");
    }

    private static void climb(Player p, int levels, boolean up) {
        World w = p.getWorld();
        Location l = p.getLocation();
        int x = l.getBlockX(), z = l.getBlockZ(), y = l.getBlockY();
        int found = 0;
        int step = up ? 1 : -1;
        boolean leftStart = false;
        for (int ty = y + step; ty > w.getMinHeight() && ty < w.getMaxHeight() - 1; ty += step) {
            boolean ok = free(w, x, ty, z) && floor(w, x, ty, z);
            if (!ok) {
                leftStart = true;
                continue;
            }
            if (!leftStart) continue;
            if (++found == levels) {
                go(p, new Location(w, x + 0.5, ty, z + 0.5));
                Msg.send(p, (up ? "&dAscended " : "&dDescended ") + levels + " level" + (levels == 1 ? "" : "s") + ".");
                return;
            }
            leftStart = false;
        }
        Msg.err(p, "No free spot " + (up ? "above" : "below") + " you.");
    }
}
