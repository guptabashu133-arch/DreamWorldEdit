package org.mydreamduels.dreamworldedit.zone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.platform.PlatformSupport;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protected zones (/zone): spawn, safe areas, event areas. Where zones overlap, a rule is
 * blocked if ANY of them blocks it. Players with dreamworldedit.zone.bypass (op by default) can
 * still build and open chests - so admins can keep decorating - but are covered by pvp / damage
 * like everyone else.
 * <p>
 * Cheap on purpose: a handful of boxes checked with plain number compares, no move listener.
 */
public final class ZoneManager implements Listener {

    private final DreamWorldEdit plugin;
    private final File file;
    /** copy-on-write: read from every region thread, changed only by commands */
    private volatile List<Zone> zones = List.of();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS, CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
            CreatureSpawnEvent.SpawnReason.TRAP);

    public ZoneManager(DreamWorldEdit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        load();
    }

    // ------------------------------------------------------------------ storage

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("zones");
        if (s == null) return;
        List<Zone> out = new ArrayList<>();
        for (String name : s.getKeys(false)) {
            ConfigurationSection z = s.getConfigurationSection(name);
            if (z == null) continue;
            Zone zone = new Zone(name, z.getString("world", "world"),
                    z.getInt("min.x"), z.getInt("min.y"), z.getInt("min.z"),
                    z.getInt("max.x"), z.getInt("max.y"), z.getInt("max.z"));
            ConfigurationSection f = z.getConfigurationSection("flags");
            if (f != null) for (String k : f.getKeys(false)) {
                Zone.Flag flag = Zone.Flag.parse(k);
                if (flag != null) zone.set(flag, f.getBoolean(k));
            }
            out.add(zone);
        }
        zones = List.copyOf(out);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Protected zones - change them in game with /zone (see /zone help).",
                "true = allowed, false = blocked inside the zone."));
        for (Zone z : zones) {
            String p = "zones." + z.name() + ".";
            y.set(p + "world", z.world());
            y.set(p + "min.x", z.minX());
            y.set(p + "min.y", z.minY());
            y.set(p + "min.z", z.minZ());
            y.set(p + "max.x", z.maxX());
            y.set(p + "max.y", z.maxY());
            y.set(p + "max.z", z.maxZ());
            for (Zone.Flag f : Zone.Flag.values()) y.set(p + "flags." + f.id(), z.allows(f));
        }
        plugin.scheduler().runAsync(() -> {
            try {
                y.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save zones.yml: " + e.getMessage());
            }
        });
    }

    public List<Zone> zones() {
        return zones;
    }

    public Zone get(String name) {
        for (Zone z : zones) if (z.name().equalsIgnoreCase(name)) return z;
        return null;
    }

    public synchronized void add(Zone z) {
        List<Zone> next = new ArrayList<>(zones);
        next.removeIf(o -> o.name().equalsIgnoreCase(z.name()));
        next.add(z);
        zones = List.copyOf(next);
        save();
    }

    public synchronized boolean remove(String name) {
        List<Zone> next = new ArrayList<>(zones);
        boolean removed = next.removeIf(o -> o.name().equalsIgnoreCase(name));
        zones = List.copyOf(next);
        if (removed) save();
        return removed;
    }

    public synchronized void setFlag(Zone z, Zone.Flag f, boolean allowed) {
        z.set(f, allowed);
        zones = List.copyOf(zones); // publish the change to every thread
        save();
    }

    public List<Zone> at(Location l) {
        if (zones.isEmpty() || l.getWorld() == null) return Collections.emptyList();
        List<Zone> out = new ArrayList<>(2);
        for (Zone z : zones) if (z.contains(l)) out.add(z);
        return out;
    }

    // ------------------------------------------------------------------ rule checks

    private boolean blocked(String world, int x, int y, int z, Zone.Flag f) {
        List<Zone> list = zones;
        if (list.isEmpty()) return false;
        for (Zone zone : list) if (!zone.allows(f) && zone.contains(world, x, y, z)) return true;
        return false;
    }

    public boolean blocked(Location l, Zone.Flag f) {
        return l != null && l.getWorld() != null && blocked(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), f);
    }

    public boolean blocked(Block b, Zone.Flag f) {
        return blocked(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), f);
    }

    private boolean bypass(Player p) {
        return p.hasPermission("dreamworldedit.zone.bypass");
    }

    /** A short "you can't do that here" in the action bar, at most once a second. */
    @SuppressWarnings("deprecation")
    private void warn(Player p, Zone.Flag f) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastWarn.put(p.getUniqueId(), now);
        String def = switch (f) {
            case PVP -> "&cPvP is off here.";
            case BUILD -> "&cThis area is protected - you can't build here.";
            case CONTAINERS -> "&cThis area is protected - you can't open that here.";
            case USE -> "&cYou can't use that here.";
            default -> "&cYou can't do that here.";
        };
        String text = plugin.getConfig().getString("zones.messages." + f.id(), def);
        if (text == null || text.isBlank()) return;
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Msg.color(text)));
    }

    private boolean denyBuild(Player p, Block b) {
        if (bypass(p) || !blocked(b, Zone.Flag.BUILD)) return false;
        warn(p, Zone.Flag.BUILD);
        return true;
    }

    // ------------------------------------------------------------------ build

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlockPlaced())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlockClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (denyBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) { // armor stands, boats, minecarts, end crystals
        Player p = e.getPlayer();
        if (p != null && denyBuild(p, e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = player(e.getRemover());
        if (p != null ? denyBuild(p, e.getEntity().getLocation().getBlock()) : blocked(e.getEntity().getLocation(), Zone.Flag.EXPLOSIONS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if ((t instanceof Hanging || t instanceof ArmorStand) && denyBuild(e.getPlayer(), t.getLocation().getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (denyBuild(e.getPlayer(), e.getRightClicked().getLocation().getBlock())) e.setCancelled(true);
    }

    /** Endermen, ravagers, withers, silverfish, mobs trampling crops. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        if (en instanceof FallingBlock) return;
        if (en instanceof Player p) {
            if (denyBuild(p, e.getBlock())) e.setCancelled(true);
        } else if (blocked(e.getBlock(), Zone.Flag.BUILD)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ clicking blocks

    private static boolean isContainer(Material m) {
        if (Tag.SHULKER_BOXES.isTagged(m)) return true;
        return switch (m) {
            case CHEST, TRAPPED_CHEST, BARREL, FURNACE, BLAST_FURNACE, SMOKER, HOPPER, DISPENSER, DROPPER,
                 BREWING_STAND, CRAFTER, DECORATED_POT, CHISELED_BOOKSHELF, LECTERN, JUKEBOX, BEACON,
                 CAMPFIRE, SOUL_CAMPFIRE -> true;
            default -> false;
        };
    }

    private static boolean isUse(Material m) {
        if (Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m) || Tag.BUTTONS.isTagged(m)
                || Tag.PRESSURE_PLATES.isTagged(m) || Tag.ALL_SIGNS.isTagged(m) || Tag.ANVIL.isTagged(m)) return true;
        // work stations and the (personal) ender chest are fine to use at a spawn
        return switch (m) {
            case LEVER, BELL, ENDER_CHEST, CRAFTING_TABLE, ENCHANTING_TABLE, GRINDSTONE, STONECUTTER, LOOM,
                 SMITHING_TABLE, CARTOGRAPHY_TABLE -> true;
            default -> false;
        };
    }

    /** Items that change the block they're used on (stripping logs, paths, farmland, fire...). */
    private static boolean changesBlocks(ItemStack it) {
        if (it == null) return false;
        Material m = it.getType();
        String n = m.name();
        return n.endsWith("_HOE") || n.endsWith("_SHOVEL") || n.endsWith("_AXE") || n.endsWith("_SPAWN_EGG")
                || m == Material.BONE_MEAL || m == Material.FLINT_AND_STEEL || m == Material.FIRE_CHARGE
                || m == Material.HONEYCOMB || m == Material.BRUSH || m == Material.SHEARS || m == Material.INK_SAC
                || m == Material.GLOW_INK_SAC || m == Material.ARMOR_STAND || m == Material.END_CRYSTAL;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || zones.isEmpty()) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) {
            // trampling farmland / turtle eggs is "build"; stepping on plates is "use"
            Material m = b.getType();
            if (m == Material.FARMLAND || m == Material.TURTLE_EGG || m == Material.SNIFFER_EGG) {
                if (!bypass(p) && blocked(b, Zone.Flag.BUILD)) e.setCancelled(true);
            } else if (Tag.PRESSURE_PLATES.isTagged(m) || m == Material.TRIPWIRE) {
                if (!bypass(p) && blocked(b, Zone.Flag.USE)) e.setCancelled(true);
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || bypass(p)) return;
        Material m = b.getType();
        Zone.Flag need;
        if (isContainer(m)) need = Zone.Flag.CONTAINERS;
        else if (isUse(m)) need = Zone.Flag.USE;
        else if (m.isInteractable() && !p.isSneaking()) need = Zone.Flag.BUILD; // repeaters, cake, pots, note blocks, beds...
        else need = null;
        if (need != null && blocked(b, need)) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            warn(p, need);
        }
        if (changesBlocks(e.getItem()) && blocked(b.getRelative(e.getBlockFace()), Zone.Flag.BUILD) | blocked(b, Zone.Flag.BUILD)) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            warn(p, Zone.Flag.BUILD);
        }
    }

    // ------------------------------------------------------------------ pvp / damage / hunger

    private static Player player(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            if (s instanceof Player p) return p;
        }
        if (e instanceof org.bukkit.entity.TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (zones.isEmpty()) return;
        Entity victim = e.getEntity();
        Player attacker = player(e.getDamager());
        if (victim instanceof Player v && attacker != null && attacker != v) {
            if (blocked(v.getLocation(), Zone.Flag.PVP) || blocked(attacker.getLocation(), Zone.Flag.PVP)) {
                e.setCancelled(true);
                warn(attacker, Zone.Flag.PVP);
            }
            return;
        }
        // players' tamed pets count as players for pvp
        if (victim instanceof Tameable t && t.getOwner() instanceof Player && attacker != null && !attacker.getUniqueId().equals(t.getOwner().getUniqueId())
                && blocked(victim.getLocation(), Zone.Flag.PVP)) {
            e.setCancelled(true);
            return;
        }
        // item frames, paintings, armor stands
        if ((victim instanceof Hanging || victim instanceof ArmorStand)) {
            if (attacker != null ? denyBuild(attacker, victim.getLocation().getBlock()) : blocked(victim.getLocation(), Zone.Flag.BUILD)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || zones.isEmpty()) return;
        if (!blocked(p.getLocation(), Zone.Flag.DAMAGE)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            // falling out of a protected spawn: back to the spawn instead of dying (or falling forever)
            Location to = plugin.spawns() == null ? null : plugin.spawns().spawn();
            if (to != null) {
                e.setCancelled(true);
                p.setFallDistance(0);
                if (PlatformSupport.hasUnifiedSchedulers()) p.teleportAsync(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
                else p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.KILL) return; // /kill still works
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && e.getFoodLevel() < p.getFoodLevel() && blocked(p.getLocation(), Zone.Flag.HUNGER)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ world: mobs, explosions, fire

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (zones.isEmpty() || !NATURAL.contains(e.getSpawnReason())) return;
        Entity en = e.getEntity();
        if ((en instanceof Monster || en instanceof Phantom || en instanceof Slime) && blocked(e.getLocation(), Zone.Flag.MOBS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!zones.isEmpty()) e.blockList().removeIf(b -> blocked(b, Zone.Flag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        if (!zones.isEmpty()) e.blockList().removeIf(b -> blocked(b, Zone.Flag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (blocked(e.getBlock(), Zone.Flag.FIRE)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (zones.isEmpty()) return;
        if (e.getPlayer() != null) {
            if (denyBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        } else if (blocked(e.getBlock(), Zone.Flag.FIRE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (e.getNewState().getType() == Material.FIRE && blocked(e.getBlock(), Zone.Flag.FIRE)) e.setCancelled(true);
    }

    public static String describe(Zone z) {
        return z.name() + " (" + z.world() + " " + z.minX() + "," + z.minY() + "," + z.minZ() + " -> " + z.maxX() + "," + z.maxY() + "," + z.maxZ() + ")";
    }

    public static List<String> flagIds() {
        List<String> out = new ArrayList<>();
        for (Zone.Flag f : Zone.Flag.values()) out.add(f.id());
        return out;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
