package org.mydreamduels.dreamworldedit.session;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

/**
 * Max blocks per edit: dreamworldedit.limit.unlimited, or the highest
 * dreamworldedit.limit.<number> the player has, else limits.default.
 */
public final class Limits {

    private Limits() {
    }

    public static long of(Plugin plugin, Player player) {
        if (player.hasPermission("dreamworldedit.limit.unlimited")) return Long.MAX_VALUE;
        long best = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission();
            if (perm.startsWith("dreamworldedit.limit.")) {
                try {
                    best = Math.max(best, Long.parseLong(perm.substring("dreamworldedit.limit.".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best >= 0 ? best : plugin.getConfig().getLong("limits.default", 100000);
    }
}
