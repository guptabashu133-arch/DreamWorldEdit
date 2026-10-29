package org.mydreamduels.dreamworldedit;

import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.platform.CancelHandle;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.util.BlockVec;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shows the bounding box of a new selection with particles for a few seconds (only to that player). */
public final class Visualizer {

    private final DreamWorldEdit plugin;
    private final Map<UUID, CancelHandle> running = new ConcurrentHashMap<>();

    public Visualizer(DreamWorldEdit plugin) {
        this.plugin = plugin;
    }

    public void show(Player p, Region r) {
        if (!plugin.getConfig().getBoolean("visualize-selection", true)) return;
        CancelHandle old = running.remove(p.getUniqueId());
        if (old != null) old.cancel();
        BlockVec a = r.min(), b = r.max().add(1, 1, 1);
        int[] runs = {0};
        CancelHandle[] h = new CancelHandle[1];
        h[0] = plugin.scheduler().runForEntityRepeating(p, () -> {
            if (++runs[0] > 6 || !p.isOnline() || !p.getWorld().equals(r.world())) {
                if (h[0] != null) h[0].cancel();
                running.remove(p.getUniqueId());
                return;
            }
            edges(p, a, b);
        }, 1L, 10L);
        if (h[0] != null) running.put(p.getUniqueId(), h[0]);
    }

    private static void edges(Player p, BlockVec a, BlockVec b) {
        double[][] corners = {{a.x(), a.y(), a.z()}, {b.x(), b.y(), b.z()}};
        // 12 edges of the box
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < 4; i++) {
                double[] from = new double[3];
                double[] to = new double[3];
                int o1 = (axis + 1) % 3, o2 = (axis + 2) % 3;
                from[axis] = corners[0][axis];
                to[axis] = corners[1][axis];
                from[o1] = to[o1] = corners[i & 1][o1];
                from[o2] = to[o2] = corners[(i >> 1) & 1][o2];
                line(p, from, to);
            }
        }
    }

    private static void line(Player p, double[] from, double[] to) {
        double len = Math.max(Math.abs(to[0] - from[0]), Math.max(Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])));
        int steps = (int) Math.min(64, Math.max(1, len));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            p.spawnParticle(Particle.END_ROD, from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t,
                    from[2] + (to[2] - from[2]) * t, 1, 0, 0, 0, 0);
        }
    }
}
