package org.mydreamduels.dreamworldedit.platform;

/**
 * Detects, once at startup, what kind of server this is:
 *
 *  - Folia: regionised multithreading. A block may only be read or
 *    written by the thread that owns its region, so every edit is split
 *    into per-chunk jobs (see edit/Engine).
 *  - Paper (and forks): unified region-aware schedulers + teleportAsync.
 *  - Plain Spigot/CraftBukkit: only the classic Bukkit scheduler.
 */
public final class PlatformSupport {

    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    /** -Ddreamworldedit.legacyScheduler=true forces the classic Bukkit scheduler (troubleshooting / test harnesses). Ignored on Folia. */
    private static final boolean UNIFIED_SCHEDULERS = classExists("io.papermc.paper.threadedregions.scheduler.AsyncScheduler")
            && (FOLIA || !Boolean.getBoolean("dreamworldedit.legacyScheduler"));

    private PlatformSupport() {
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /** True on Paper and every fork of it (including Folia) - also implies teleportAsync/getChunkAtAsync exist. */
    public static boolean hasUnifiedSchedulers() {
        return UNIFIED_SCHEDULERS;
    }

    public static String name() {
        if (FOLIA) return "Folia";
        if (UNIFIED_SCHEDULERS) return "Paper";
        return "Spigot";
    }
}
