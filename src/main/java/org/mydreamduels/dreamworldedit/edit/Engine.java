package org.mydreamduels.dreamworldedit.edit;

import org.bukkit.World;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.platform.CancelHandle;
import org.mydreamduels.dreamworldedit.util.BlockVec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;

/**
 * Runs edits without freezing the server, on every platform.
 *
 * An edit is split into jobs - one per chunk, or per chunk "slab" of
 * slab-height blocks so a single job never touches too many blocks. A
 * global ticker starts at most chunks-per-tick jobs each tick (and keeps
 * at most max-in-flight running). Each job loads its chunk asynchronously
 * and then runs on the thread that owns that chunk: Folia's region thread,
 * or the main thread on Paper/Spigot. That is what makes it Folia-safe -
 * no block is ever touched from the wrong thread.
 */
public final class Engine {

    private final DreamWorldEdit plugin;
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private CancelHandle ticker;

    public Engine(DreamWorldEdit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ticker = plugin.scheduler().runGlobalRepeating(this::tick, 1L, 1L);
    }

    public void stop() {
        if (ticker != null) ticker.cancel();
        queue.clear();
    }

    public int queued() {
        return queue.size();
    }

    private void tick() {
        int perTick = Math.max(1, plugin.getConfig().getInt("performance.chunks-per-tick", 8));
        int maxInFlight = Math.max(perTick, plugin.getConfig().getInt("performance.max-in-flight", 64));
        for (int i = 0; i < perTick && inFlight.get() < maxInFlight; i++) {
            Runnable job = queue.poll();
            if (job == null) return;
            inFlight.incrementAndGet();
            job.run();
        }
    }

    /**
     * Runs op over every chunk the box [min,max] touches (filtered by chunkFilter).
     * columnar = the op needs whole columns (no vertical slabs).
     * record   = keep an undo record (null for reads/undo/redo).
     * budget   = max blocks this edit may change in total.
     */
    public CompletableFuture<EditResult> run(World world, BlockVec min, BlockVec max, LongPredicate chunkFilter,
                                             ChunkOp op, boolean columnar, String description, boolean record, long budget) {
        List<long[]> jobs = new ArrayList<>();
        int slab = columnar ? Integer.MAX_VALUE : Math.max(16, plugin.getConfig().getInt("performance.slab-height", 64));
        int y0 = Math.max(world.getMinHeight(), min.y());
        int y1 = Math.min(world.getMaxHeight() - 1, max.y());
        for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
            for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                if (chunkFilter != null && !chunkFilter.test(EditRecord.key(cx, cz))) continue;
                for (long y = y0; y <= y1; y += slab) {
                    jobs.add(new long[]{cx, cz, y, Math.min(y1, y + slab - 1)});
                }
            }
        }
        return runJobs(world, jobs, op, description, record, budget);
    }

    /** Same, but for an explicit list of chunks (undo/redo/precomputed change sets) - whole columns. */
    public CompletableFuture<EditResult> runChunks(World world, Collection<Long> chunkKeys, ChunkOp op,
                                                   String description, boolean record, long budget) {
        List<long[]> jobs = new ArrayList<>();
        for (long key : chunkKeys) {
            jobs.add(new long[]{EditRecord.chunkX(key), EditRecord.chunkZ(key), world.getMinHeight(), world.getMaxHeight() - 1});
        }
        return runJobs(world, jobs, op, description, record, budget);
    }

    private CompletableFuture<EditResult> runJobs(World world, List<long[]> jobs, ChunkOp op, String description,
                                                  boolean record, long budget) {
        CompletableFuture<EditResult> done = new CompletableFuture<>();
        long start = System.currentTimeMillis();
        EditRecord rec = record ? new EditRecord(world, description) : null;
        if (jobs.isEmpty()) {
            done.complete(new EditResult(0, rec, 0, null));
            return done;
        }
        AtomicInteger remaining = new AtomicInteger(jobs.size());
        AtomicLong changed = new AtomicLong();
        AtomicLong left = new AtomicLong(budget);
        AtomicReference<String> error = new AtomicReference<>();

        for (long[] j : jobs) {
            int cx = (int) j[0], cz = (int) j[1], jy0 = (int) j[2], jy1 = (int) j[3];
            queue.add(() -> plugin.scheduler().loadChunk(world, cx, cz).whenComplete((c, loadError) ->
                    plugin.scheduler().runAtChunk(world, cx, cz, () -> {
                        try {
                            if (loadError != null) throw new IllegalStateException("chunk " + cx + "," + cz + " failed to load", loadError);
                            ChunkContext ctx = new ChunkContext(world, cx, cz, jy0, jy1, record, Math.max(0, left.get()));
                            op.apply(ctx);
                            left.addAndGet(-ctx.changed());
                            changed.addAndGet(ctx.changed());
                            if (rec != null) rec.add(cx, cz, ctx.changes());
                        } catch (Throwable t) {
                            error.compareAndSet(null, t.getMessage() == null ? t.toString() : t.getMessage());
                            plugin.getLogger().log(java.util.logging.Level.WARNING, "Edit job failed in chunk " + cx + "," + cz, t);
                        } finally {
                            inFlight.decrementAndGet();
                            if (remaining.decrementAndGet() == 0) {
                                done.complete(new EditResult(changed.get(), rec, System.currentTimeMillis() - start, error.get()));
                            }
                        }
                    })));
        }
        return done;
    }

    /** Puts everything in a record back (undo) or forward again (redo). */
    public CompletableFuture<EditResult> replay(EditRecord rec, boolean undo) {
        ChunkOp op = ctx -> {
            List<ChangeList> lists = rec.chunks().get(EditRecord.key(ctx.cx, ctx.cz));
            if (lists == null) return;
            List<ChangeList> copy;
            synchronized (lists) {
                copy = new ArrayList<>(lists);
            }
            if (undo) {
                for (int l = copy.size() - 1; l >= 0; l--) {
                    ChangeList c = copy.get(l);
                    for (int i = c.size() - 1; i >= 0; i--) ctx.set(c.x(i), c.y(i), c.z(i), c.before(i));
                }
            } else {
                for (ChangeList c : copy) {
                    for (int i = 0; i < c.size(); i++) ctx.set(c.x(i), c.y(i), c.z(i), c.after(i));
                }
            }
        };
        return runChunks(rec.world(), rec.chunks().keySet(), op, rec.description(), false, Long.MAX_VALUE);
    }
}
