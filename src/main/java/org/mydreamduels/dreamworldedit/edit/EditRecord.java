package org.mydreamduels.dreamworldedit.edit;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Everything one edit changed, grouped by chunk - what //undo and //redo replay. */
public final class EditRecord {

    private final World world;
    private final String description;
    private final Map<Long, List<ChangeList>> byChunk = new ConcurrentHashMap<>();
    private final AtomicLong count = new AtomicLong();

    public EditRecord(World world, String description) {
        this.world = world;
        this.description = description;
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }

    void add(int cx, int cz, ChangeList list) {
        if (list.size() == 0) return;
        byChunk.computeIfAbsent(key(cx, cz), k -> Collections.synchronizedList(new ArrayList<>())).add(list);
        count.addAndGet(list.size());
    }

    public World world() {
        return world;
    }

    public String description() {
        return description;
    }

    public long size() {
        return count.get();
    }

    public Map<Long, List<ChangeList>> chunks() {
        return byChunk;
    }
}
