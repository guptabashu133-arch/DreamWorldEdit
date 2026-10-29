package org.mydreamduels.dreamworldedit.brush;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.clipboard.Clipboard;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.region.CylinderRegion;
import org.mydreamduels.dreamworldedit.region.EllipsoidRegion;
import org.mydreamduels.dreamworldedit.region.Region;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** The brush types: sphere, cylinder, smooth, gravity, butcher, paste. */
public final class Brushes {

    private Brushes() {
    }

    public static final class Sphere extends Brush {
        private final boolean hollow;

        public Sphere(Pattern p, String text, double size, boolean hollow) {
            super(p, text, size);
            this.hollow = hollow;
        }

        public String name() {
            return hollow ? "hollow sphere" : "sphere";
        }

        public void use(DreamWorldEdit plugin, Player player, BlockVec t) {
            Region r = new EllipsoidRegion(player.getWorld(), t, size, size, size, hollow);
            plugin.edits().submit(player, "sphere brush", r.boxVolume(), budget -> plugin.ops().fill(r, pattern, mask, "sphere brush", budget));
        }
    }

    public static final class Cylinder extends Brush {
        private final int height;
        private final boolean hollow;

        public Cylinder(Pattern p, String text, double size, int height, boolean hollow) {
            super(p, text, size);
            this.height = Math.max(1, height);
            this.hollow = hollow;
        }

        public String name() {
            return (hollow ? "hollow cylinder" : "cylinder") + " h" + height;
        }

        public void use(DreamWorldEdit plugin, Player player, BlockVec t) {
            Region r = new CylinderRegion(player.getWorld(), t.x(), t.z(), size, size, t.y(), t.y() + height - 1, hollow);
            plugin.edits().submit(player, "cylinder brush", r.boxVolume(), budget -> plugin.ops().fill(r, pattern, mask, "cylinder brush", budget));
        }
    }

    public static final class Smooth extends Brush {
        private final int iterations;

        public Smooth(double size, int iterations) {
            super(null, "", size);
            this.iterations = Math.max(1, iterations);
        }

        public String name() {
            return "smooth x" + iterations;
        }

        public void use(DreamWorldEdit plugin, Player player, BlockVec t) {
            int s = (int) Math.ceil(size);
            Region r = new CylinderRegion(player.getWorld(), t.x(), t.z(), size, size, t.y() - s, t.y() + s, false);
            plugin.edits().submit(player, "smooth brush", r.boxVolume(), budget -> plugin.ops().smooth(r, iterations, budget));
        }
    }

    public static final class Gravity extends Brush {
        public Gravity(double size) {
            super(null, "", size);
        }

        public String name() {
            return "gravity";
        }

        public void use(DreamWorldEdit plugin, Player player, BlockVec t) {
            Region r = new EllipsoidRegion(player.getWorld(), t, size, size, size, false);
            plugin.edits().submit(player, "gravity brush", r.boxVolume(), budget -> plugin.ops().gravity(r, budget));
        }
    }

    public static final class Butcher extends Brush {
        public Butcher(double size) {
            super(null, "", size);
        }

        public String name() {
            return "butcher";
        }

        public void use(DreamWorldEdit plugin, Player player, BlockVec t) {
            World w = player.getWorld();
            Location at = new Location(w, t.x() + 0.5, t.y() + 0.5, t.z() + 0.5);
            plugin.scheduler().runAtLocation(at, () -> {
                AtomicInteger n = new AtomicInteger();
                for (Entity e : w.getNearbyEntities(at, size, size, size)) {
                    if (e instanceof Enemy && !named(e)) {
                        e.remove();
                        n.incrementAndGet();
                    }
                }
                Msg.send(player, "&7Killed &e" + n.get() + "&7 mobs.");
            });
        }
    }

    @SuppressWarnings("deprecation")
    static boolean named(Entity e) {
        return e.getCustomName() != null;
    }

    public static final class Paste extends Brush {
        private final Clipboard clipboard;
        private final boolean skipAir;

        public Paste(Clipboard clipboard, boolean skipAir) {
            super(null, "", 0);
            this.clipboard = clipboard;
            this.skipAir = skipAir;
        }

        public String name() {
            return "paste" + (skipAir ? " -a" : "");
        }

        public void use(DreamWorldEdit plugin, Player player, BlockVec t) {
            plugin.edits().submit(player, "paste brush", clipboard.volume(), budget ->
                    plugin.ops().paste(player.getWorld(), clipboard, List.of(t.add(0, 1, 0)), skipAir, null, null, "paste brush", budget));
        }
    }
}
