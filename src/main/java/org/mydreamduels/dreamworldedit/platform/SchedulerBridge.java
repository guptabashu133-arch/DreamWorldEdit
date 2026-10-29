package org.mydreamduels.dreamworldedit.platform;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * Every bit of scheduling DreamWorldEdit does goes through here, so the
 * rest of the plugin never touches Bukkit's scheduler(s) directly.
 * PaperSchedulerBridge covers Paper + Folia, SpigotSchedulerBridge covers
 * plain Spigot (where "global", "entity" and "location" all simply mean
 * the single main thread).
 */
public interface SchedulerBridge {

    /** Off the main thread - for disk I/O. */
    void runAsync(Runnable task);

    /** Global region (Folia) / main thread - world-level state such as difficulty, gamerules, loading worlds. */
    void runGlobal(Runnable task);

    void runGlobalDelayed(Runnable task, long delayTicks);

    CancelHandle runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    /** On the thread that owns this entity. Returns false if the entity was already gone. */
    boolean runForEntity(Entity entity, Runnable task);

    boolean runForEntityDelayed(Entity entity, Runnable task, long delayTicks);

    /** Repeats on the entity's thread until cancelled or the entity is removed. Null if it could not be scheduled. */
    CancelHandle runForEntityRepeating(Entity entity, Runnable task, long initialDelayTicks, long periodTicks);

    /** On the thread that owns the region containing this location - required for block changes on Folia. */
    void runAtLocation(Location location, Runnable task);

    /** On the thread that owns this chunk - every block read/write of an edit happens here. */
    void runAtChunk(World world, int chunkX, int chunkZ, Runnable task);

    /** Loads (or generates) the chunk without blocking a server thread where the platform allows it. */
    CompletableFuture<?> loadChunk(World world, int chunkX, int chunkZ);

    void cancelAll();
}
