package org.mydreamduels.dreamworldedit.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.platform.CancelHandle;
import org.mydreamduels.dreamworldedit.platform.PlatformSupport;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server spawn (/setspawn, /spawn). New players start there, and everyone who dies comes
 * back there (a bed / respawn anchor still wins if spawn.bed-respawn is true).
 * <p>
 * Other plugins keep their own respawn spots: a plugin that sets the metadata
 * "dream.keep-respawn" (a time in ms) on the player during the respawn - DreamDuels does for its
 * arenas - is left alone, and so are the worlds in spawn.skip-worlds.
 */
public final class SpawnManager implements Listener {

    private final DreamWorldEdit plugin;
    private final File file;
    private volatile Location spawn;
    /** world name of a spawn whose world isn't loaded (yet) - kept so it isn't lost */
    private volatile String pendingWorld;
    private volatile double[] pending;
    private final Map<UUID, CancelHandle> warmups = new ConcurrentHashMap<>();
    /** last PlayerRespawnEvent per player (ms) - so the Folia fallback doesn't move them twice */
    private final Map<UUID, Long> respawned = new ConcurrentHashMap<>();

    public SpawnManager(DreamWorldEdit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
        load();
    }

    // ------------------------------------------------------------------ storage

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String w = y.getString("world");
        if (w == null) return;
        double[] d = {y.getDouble("x"), y.getDouble("y"), y.getDouble("z"), y.getDouble("yaw"), y.getDouble("pitch")};
        World world = Bukkit.getWorld(w);
        if (world == null) {
            pendingWorld = w;
            pending = d;
            plugin.getLogger().info("Spawn world '" + w + "' isn't loaded yet - the spawn will be used as soon as it is.");
            return;
        }
        spawn = new Location(world, d[0], d[1], d[2], (float) d[3], (float) d[4]);
    }

    /** The spawn, or null when none is set (or its world isn't loaded). */
    public Location spawn() {
        Location s = spawn;
        if (s == null && pendingWorld != null) {
            World w = Bukkit.getWorld(pendingWorld);
            if (w != null) {
                double[] d = pending;
                s = spawn = new Location(w, d[0], d[1], d[2], (float) d[3], (float) d[4]);
                pendingWorld = null;
            }
        }
        return s == null ? null : s.clone();
    }

    public boolean isSet() {
        return spawn != null || pendingWorld != null;
    }

    public void set(Location loc) {
        Location l = loc.clone();
        spawn = l;
        pendingWorld = null;
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", l.getWorld().getName());
        y.set("x", l.getX());
        y.set("y", l.getY());
        y.set("z", l.getZ());
        y.set("yaw", (double) l.getYaw());
        y.set("pitch", (double) l.getPitch());
        save(y);
        // vanilla's own world spawn too (compasses, the spawn chunks) - global thread on Folia
        plugin.scheduler().runGlobal(() -> {
            try {
                l.getWorld().setSpawnLocation(l);
            } catch (Throwable ignored) {
            }
        });
    }

    public void remove() {
        spawn = null;
        pendingWorld = null;
        if (file.isFile() && !file.delete()) save(new YamlConfiguration());
    }

    private void save(YamlConfiguration y) {
        plugin.scheduler().runAsync(() -> {
            try {
                y.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save spawn.yml: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ teleporting

    private void teleport(Player p, Location to) {
        if (PlatformSupport.hasUnifiedSchedulers()) p.teleportAsync(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        else p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    // TaraashMC-Icons pack, shared page EB: clock = EB0C, pearl = EB03, cross = EB16, check = EB15
    private static final int GLYPH_CLOCK = 0xEB0C, GLYPH_PEARL = 0xEB03, GLYPH_CROSS = 0xEB16;

    private String text(String key, String def) {
        return plugin.getConfig().getString("spawn.text." + key, def);
    }

    private void show(Player p, String titleKey, String titleDef, String subKey, String subDef, String icon, int glyph, int seconds, int stay) {
        SpawnText.title(p, text(titleKey, titleDef), text(subKey, subDef).replace("{seconds}", String.valueOf(seconds)), icon, glyph, 0, stay, 6);
    }

    private void sound(Player p, String name, float pitch) {
        try {
            p.playSound(p.getLocation(), name, 0.8f, pitch);
        } catch (Throwable ignored) {
        }
    }

    /** /spawn: a 5 second countdown on screen (moving or getting hurt cancels it), instant with the permission. */
    public void sendToSpawn(Player p) {
        Location to = spawn();
        if (to == null) {
            Msg.err(p, msg("not-set", "There's no spawn yet. An admin can set it with /setspawn."));
            return;
        }
        int seconds = Math.max(0, plugin.getConfig().getInt("spawn.countdown-seconds", 5));
        if (seconds == 0 || p.hasPermission("dreamworldedit.spawn.instant")) {
            teleport(p, to);
            arrived(p);
            return;
        }
        cancelWarmup(p.getUniqueId());
        Location start = p.getLocation();
        int[] left = {seconds};
        tick(p, left[0]);
        CancelHandle[] h = new CancelHandle[1];
        h[0] = plugin.scheduler().runForEntityRepeating(p, () -> {
            if (!p.isOnline()) {
                cancelWarmup(p.getUniqueId());
                return;
            }
            Location now = p.getLocation();
            if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.25) {
                cancelWarmup(p.getUniqueId());
                cancelled(p, "cancelled-move", "&7You moved - stand still next time.");
                return;
            }
            if (--left[0] <= 0) {
                cancelWarmup(p.getUniqueId());
                Location target = spawn();
                if (target == null) return;
                teleport(p, target);
                arrived(p);
                return;
            }
            tick(p, left[0]);
        }, 20L, 20L);
        if (h[0] != null) warmups.put(p.getUniqueId(), h[0]);
    }

    private void tick(Player p, int seconds) {
        show(p, "title", "&6&lTaraash&e&lMC", "subtitle", "{icon} &eTeleporting to spawn in &f&l{seconds}&e...", "item/clock_00", GLYPH_CLOCK, seconds, 25);
        sound(p, "block.note_block.pling", 0.8f + (5 - Math.min(5, seconds)) * 0.15f);
    }

    private void arrived(Player p) {
        show(p, "title", "&6&lTaraash&e&lMC", "arrived", "{icon} &aWelcome to spawn!", "item/ender_pearl", GLYPH_PEARL, 0, 40);
        sound(p, "entity.enderman.teleport", 1.0f);
    }

    private void cancelled(Player p, String key, String def) {
        show(p, "cancelled-title", "&c&lTeleport cancelled", key, def, "item/barrier", GLYPH_CROSS, 0, 40);
        sound(p, "block.note_block.bass", 0.6f);
    }

    private void cancelWarmup(UUID id) {
        CancelHandle h = warmups.remove(id);
        if (h != null) h.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && warmups.containsKey(p.getUniqueId())) {
            cancelWarmup(p.getUniqueId());
            cancelled(p, "cancelled-damage", "&7You got hurt - try again somewhere safe.");
        }
    }

    @SuppressWarnings("deprecation")
    private static void actionBar(Player p, String text) {
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Msg.color(text)));
    }

    String msg(String key, String def) {
        return plugin.getConfig().getString("spawn.messages." + key, def);
    }

    // ------------------------------------------------------------------ join / respawn

    private boolean skipWorld(World w) {
        if (w == null) return false;
        List<String> skip = plugin.getConfig().getStringList("spawn.skip-worlds");
        for (String s : skip) if (s.equalsIgnoreCase(w.getName())) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean first = !p.hasPlayedBefore();
        boolean go = first ? plugin.getConfig().getBoolean("spawn.on-first-join", true)
                : plugin.getConfig().getBoolean("spawn.on-every-join", false);
        if (!go || !isSet()) return;
        // a few ticks later: teleporting inside the join event is unreliable, and this also lands
        // after a world plugin's own first-join teleport (Multiverse does it 1 tick after joining)
        plugin.scheduler().runForEntityDelayed(p, () -> {
            Location to = spawn();
            if (to != null && p.isOnline()) teleport(p, to);
        }, 3L);
    }

    private boolean keptByOtherPlugin(Player p) {
        long now = System.currentTimeMillis();
        for (MetadataValue v : p.getMetadata("dream.keep-respawn")) {
            try {
                if (now - v.asLong() < 3000) return true;
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private boolean wantRespawn(Player p, boolean bedOrAnchor, World deathWorld) {
        if (!plugin.getConfig().getBoolean("spawn.respawn-at-spawn", true) || !isSet()) return false;
        if (bedOrAnchor && plugin.getConfig().getBoolean("spawn.bed-respawn", true)) return false;
        return !skipWorld(deathWorld) && !keptByOtherPlugin(p);
    }

    /** HIGHEST: after world plugins (Multiverse), but a plugin's own spot (duels) is kept, see keptByOtherPlugin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        respawned.put(p.getUniqueId(), System.currentTimeMillis());
        if (!wantRespawn(p, e.isBedSpawn() || e.isAnchorSpawn(), p.getWorld())) return;
        Location to = spawn();
        if (to != null) e.setRespawnLocation(to);
    }

    /** Folia may not fire PlayerRespawnEvent: move the player once they're alive again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        cancelWarmup(e.getEntity().getUniqueId());
        if (!PlatformSupport.isFolia()) return;
        Player p = e.getEntity();
        long died = System.currentTimeMillis();
        World deathWorld = p.getWorld();
        Location bed = p.getRespawnLocation();
        CancelHandle[] h = new CancelHandle[1];
        int[] waited = {0};
        h[0] = plugin.scheduler().runForEntityRepeating(p, () -> {
            if (!p.isOnline() || ++waited[0] > 6000) {
                if (h[0] != null) h[0].cancel();
                return;
            }
            if (p.isDead()) return;
            if (h[0] != null) h[0].cancel();
            Long ev = respawned.get(p.getUniqueId());
            if (ev != null && ev >= died) return; // the respawn event did it already
            boolean usedBed = bed != null && bed.getWorld() == p.getWorld() && bed.distanceSquared(p.getLocation()) < 16 * 16;
            if (!wantRespawn(p, usedBed, deathWorld)) return;
            // a little later than the world plugin's own respawn move, so this one is the last
            plugin.scheduler().runForEntityDelayed(p, () -> {
                Location to = spawn();
                if (to != null && p.isOnline() && !p.isDead()) teleport(p, to);
            }, 3L);
        }, 1L, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancelWarmup(e.getPlayer().getUniqueId());
        respawned.remove(e.getPlayer().getUniqueId());
    }
}
