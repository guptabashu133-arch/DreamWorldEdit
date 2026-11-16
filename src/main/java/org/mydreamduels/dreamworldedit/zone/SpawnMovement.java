package org.mydreamduels.dreamworldedit.zone;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawn flight and double jump, both limited to zones with the matching grant rule
 * ({@code fly} / {@code doublejump}, made with /spawnflywand and /doublejumpwand or /zone flag).
 * <ul>
 *   <li>/spawnfly (dreamworldedit.spawnfly) turns flight on inside a fly zone. Leaving the zone,
 *       teleporting out, changing world or getting hit turns it off, with a short fall protection.</li>
 *   <li>Double jump (dreamworldedit.doublejump): inside a doublejump zone, pressing jump in the air
 *       launches you forward and up. Outside the zone it is gone.</li>
 * </ul>
 * Only players this class switched on are ever switched off again, so /fly from other plugins,
 * creative and spectator are never touched. Every handler runs on the player's own thread (Folia safe).
 */
public final class SpawnMovement implements Listener {

    public static final String PERM_FLY = "dreamworldedit.spawnfly";
    public static final String PERM_DOUBLEJUMP = "dreamworldedit.doublejump";

    private final DreamWorldEdit plugin;
    /** players flying through /spawnfly */
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();
    /** players we gave allow-flight to so the second jump press can be caught */
    private final Set<UUID> armed = ConcurrentHashMap.newKeySet();
    /** players in the air after a double jump (re-armed when they land) */
    private final Set<UUID> airborne = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> fallSafeUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastJump = new ConcurrentHashMap<>();

    private org.mydreamduels.dreamworldedit.platform.CancelHandle watchdog;

    public SpawnMovement(DreamWorldEdit plugin) {
        this.plugin = plugin;
        // Safety net once a second, only for players we manage: catches teleports that fire no event
        // (Folia async teleports), permissions taken away and anything else that moved them out.
        watchdog = plugin.scheduler().runGlobalRepeating(this::watch, 20, 20);
    }

    private void watch() {
        if (flying.isEmpty() && armed.isEmpty()) return;
        java.util.Set<UUID> ids = new java.util.HashSet<>(flying);
        ids.addAll(armed);
        for (UUID id : ids) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p == null) {
                flying.remove(id);
                armed.remove(id);
                continue;
            }
            plugin.scheduler().runForEntity(p, () -> {
                if (flying.contains(id) && !p.hasPermission(PERM_FLY) && !p.hasPermission("dreamworldedit.command.spawnfly")) {
                    stopFlying(p, "spawn-fly.messages.disabled", true);
                    return;
                }
                refresh(p);
            });
        }
    }

    private ZoneManager zones() {
        return plugin.zones();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private static boolean survivalLike(Player p) {
        GameMode g = p.getGameMode();
        return g == GameMode.SURVIVAL || g == GameMode.ADVENTURE;
    }

    // ------------------------------------------------------------------ /spawnfly

    public boolean isSpawnFlying(Player p) {
        return flying.contains(p.getUniqueId());
    }

    /** /spawnfly - player's thread. */
    public void toggle(Player p) {
        if (!survivalLike(p)) {
            say(p, "spawn-fly.messages.gamemode", "item/feather");
            return;
        }
        if (flying.contains(p.getUniqueId())) {
            stopFlying(p, "spawn-fly.messages.disabled", false);
            return;
        }
        if (!cfg().getBoolean("spawn-fly.enabled", true)) {
            say(p, "spawn-fly.messages.feature-off", "item/barrier");
            return;
        }
        if (!zones().granted(p.getLocation(), Zone.Flag.FLY)) {
            say(p, "spawn-fly.messages.not-here", "item/barrier");
            return;
        }
        disarm(p); // double jump and flight share allow-flight: flight wins
        flying.add(p.getUniqueId());
        p.setAllowFlight(true);
        p.setFlying(true);
        say(p, "spawn-fly.messages.enabled", "item/feather");
    }

    private void stopFlying(Player p, String messageKey, boolean protectFall) {
        if (!flying.remove(p.getUniqueId())) return;
        if (survivalLike(p)) {
            boolean wasInAir = p.isFlying() || !onGround(p);
            p.setFlying(false);
            p.setAllowFlight(false);
            if (protectFall && wasInAir) protectFall(p);
        }
        if (messageKey != null) say(p, messageKey, "item/feather");
        // still inside a double jump zone? give double jump back right away
        evaluateDoubleJump(p, p.getLocation());
    }

    // ------------------------------------------------------------------ double jump

    private boolean mayDoubleJump(Player p, Location at) {
        return cfg().getBoolean("double-jump.enabled", true) && survivalLike(p) && !flying.contains(p.getUniqueId())
                && p.hasPermission(PERM_DOUBLEJUMP) && zones().granted(at, Zone.Flag.DOUBLEJUMP);
    }

    /** Arms or disarms double jump for where the player is now. */
    private void evaluateDoubleJump(Player p, Location at) {
        UUID id = p.getUniqueId();
        if (mayDoubleJump(p, at)) {
            if (onGround(p)) airborne.remove(id);
            if (!airborne.contains(id) && !p.getAllowFlight()) {
                p.setAllowFlight(true);
                armed.add(id);
            }
        } else {
            disarm(p);
        }
    }

    private void disarm(Player p) {
        UUID id = p.getUniqueId();
        airborne.remove(id);
        if (!armed.remove(id)) return;
        if (survivalLike(p) && !flying.contains(id)) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!e.isFlying() || flying.contains(id) || !armed.contains(id)) return;
        // the second jump press: no flying, a jump instead
        e.setCancelled(true);
        p.setFlying(false);
        p.setAllowFlight(false);
        armed.remove(id);
        if (!mayDoubleJump(p, p.getLocation())) return;
        long now = System.currentTimeMillis();
        Long last = lastJump.get(id);
        if (last != null && now - last < cfg().getLong("double-jump.cooldown-ms", 800)) return;
        lastJump.put(id, now);
        airborne.add(id);

        double power = cfg().getDouble("double-jump.power", 1.1);
        double height = cfg().getDouble("double-jump.height", 0.85);
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 1.0E-4) dir.normalize().multiply(power);
        p.setVelocity(dir.setY(height));
        protectFall(p);

        Location l = p.getLocation();
        String sound = cfg().getString("double-jump.sound", "entity.bat.takeoff");
        if (sound != null && !sound.isBlank()) {
            try {
                p.getWorld().playSound(l, sound.toLowerCase(Locale.ROOT), 0.8f, 1.2f);
            } catch (Throwable ignored) {
            }
        }
        String particle = cfg().getString("double-jump.particle", "CLOUD");
        if (particle != null && !particle.isBlank()) {
            try {
                p.getWorld().spawnParticle(Particle.valueOf(particle.toUpperCase(Locale.ROOT)), l, 12, 0.3, 0.05, 0.3, 0.02);
            } catch (Throwable ignored) { // unknown name or a particle that needs extra data
            }
        }
    }

    // ------------------------------------------------------------------ movement / leaving

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Location from = e.getFrom();
        boolean newBlock = from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
        // landing after a double jump re-arms it even without changing block
        if (!newBlock && !(airborne.contains(id) && onGround(p))) return;

        if (flying.contains(id) && !zones().granted(to, Zone.Flag.FLY)) {
            stopFlying(p, "spawn-fly.messages.left-area", true);
            return;
        }
        // fall protection is not cleared here: the fall damage of this landing is applied after this event
        if (armed.contains(id) || airborne.contains(id) || p.hasPermission(PERM_DOUBLEJUMP)) evaluateDoubleJump(p, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!flying.contains(p.getUniqueId()) && !armed.contains(p.getUniqueId()) && !p.hasPermission(PERM_DOUBLEJUMP)) return;
        plugin.scheduler().runForEntityDelayed(p, () -> refresh(p), 2); // after the teleport landed
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        plugin.scheduler().runForEntityDelayed(p, () -> refresh(p), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        flying.remove(p.getUniqueId());
        armed.remove(p.getUniqueId());
        airborne.remove(p.getUniqueId());
        plugin.scheduler().runForEntityDelayed(p, () -> refresh(p), 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (e.getNewGameMode() == GameMode.CREATIVE || e.getNewGameMode() == GameMode.SPECTATOR) {
            // those modes manage flight themselves
            flying.remove(id);
            armed.remove(id);
            airborne.remove(id);
            return;
        }
        plugin.scheduler().runForEntityDelayed(p, () -> refresh(p), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.scheduler().runForEntityDelayed(p, () -> refresh(p), 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        // allow-flight is saved with the player: never let ours stick to the account
        if ((flying.remove(id) | armed.remove(id)) && survivalLike(p)) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        airborne.remove(id);
        fallSafeUntil.remove(id);
        lastJump.remove(id);
    }

    /** Re-checks flight and double jump where the player is now (player's thread). */
    private void refresh(Player p) {
        if (!p.isOnline()) return;
        if (flying.contains(p.getUniqueId()) && !zones().granted(p.getLocation(), Zone.Flag.FLY)) {
            stopFlying(p, "spawn-fly.messages.left-area", true);
            return;
        }
        evaluateDoubleJump(p, p.getLocation());
    }

    // ------------------------------------------------------------------ damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Long until = fallSafeUntil.remove(p.getUniqueId());
        if (until != null && System.currentTimeMillis() <= until) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !flying.contains(p.getUniqueId())) return;
        if (!cfg().getBoolean("spawn-fly.disable-on-damage", true)) return;
        stopFlying(p, "spawn-fly.messages.damaged", true);
    }

    private void protectFall(Player p) {
        long seconds = Math.max(0, cfg().getLong("spawn-fly.fall-protection-seconds", 10));
        if (seconds > 0) fallSafeUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000);
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("deprecation") // Player#isOnGround is client-reported but fine for re-arming a jump
    private static boolean onGround(Player p) {
        return p.isOnGround();
    }

    private void say(Player p, String key, String icon) {
        String text = cfg().getString(key, "");
        if (text == null || text.isBlank()) return;
        ZoneChat.send(p, text.replace("{player}", p.getName()), icon, cfg().getBoolean("zones.sprite-icons", true));
    }

    /** Plugin disable / reload: hand every player's normal flight state back. */
    public void shutdown() {
        if (watchdog != null) watchdog.cancel();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if ((flying.contains(id) || armed.contains(id)) && survivalLike(p)) {
                try { // the server is stopping or reloading: no scheduler any more, do it right here
                    p.setFlying(false);
                    p.setAllowFlight(false);
                } catch (Throwable ignored) { // Folia: not this player's thread - allow-flight is cleared on quit anyway
                }
            }
        }
        flying.clear();
        armed.clear();
        airborne.clear();
    }
}
