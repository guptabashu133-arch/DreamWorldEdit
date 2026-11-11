package org.mydreamduels.dreamworldedit.zone;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** A protected box: a name, a world, two corners and its rules (flags). */
public final class Zone {

    /** What a zone can switch on or off. allowedByDefault = the value a new zone starts with. */
    public enum Flag {
        PVP(false, "Players hitting each other"),
        BUILD(false, "Breaking and placing blocks, buckets, item frames, armor stands"),
        CONTAINERS(false, "Opening chests, barrels, furnaces, shulkers..."),
        USE(true, "Doors, trapdoors, gates, buttons, levers, pressure plates"),
        DAMAGE(false, "Players taking any damage (falling, mobs, fire...)"),
        HUNGER(false, "Hunger going down"),
        MOBS(false, "Monsters spawning by themselves"),
        EXPLOSIONS(false, "TNT, creepers and crystals breaking blocks"),
        FIRE(false, "Fire spreading and burning blocks"),
        PEARLS(false, "Ender pearls (throwing, landing) and chorus fruit teleports"),
        DROPS(false, "Dropping items (ops / bypass can)"),
        TRAMPLE(false, "Trampling farmland and crops");

        public final boolean allowedByDefault;
        public final String description;

        Flag(boolean allowedByDefault, String description) {
            this.allowedByDefault = allowedByDefault;
            this.description = description;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Flag parse(String s) {
            if (s == null) return null;
            String k = s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            return switch (k) {
                case "pvp", "fight", "fighting" -> PVP;
                case "build", "break", "place", "blockbreak", "blockplace" -> BUILD;
                case "containers", "container", "chests", "chest" -> CONTAINERS;
                case "use", "doors", "buttons", "interact" -> USE;
                case "damage", "invincible", "god" -> DAMAGE;
                case "hunger", "food" -> HUNGER;
                case "mobs", "mobspawn", "mobspawning", "monsters" -> MOBS;
                case "explosions", "explosion", "tnt", "creeper" -> EXPLOSIONS;
                case "fire", "firespread" -> FIRE;
                case "pearls", "pearl", "enderpearl", "enderpearls", "chorus" -> PEARLS;
                case "drops", "drop", "itemdrop", "dropitems" -> DROPS;
                case "trample", "trampling", "farmland", "crops" -> TRAMPLE;
                default -> null;
            };
        }
    }

    private final String name;
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);

    public Zone(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        for (Flag f : Flag.values()) flags.put(f, f.allowedByDefault);
    }

    public String name() { return name; }
    public String world() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    public boolean allows(Flag f) {
        return flags.getOrDefault(f, f.allowedByDefault);
    }

    /** Same rules as another zone (used when a zone is moved). */
    public void copyFlags(Zone o) {
        for (Flag f : Flag.values()) flags.put(f, o.allows(f));
    }

    void set(Flag f, boolean allowed) {
        flags.put(f, allowed);
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ && world.equals(worldName);
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && contains(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean contains(Block b) {
        return contains(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
