package org.mydreamduels.dreamworldedit.edit;

import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.session.Limits;
import org.mydreamduels.dreamworldedit.session.Session;
import org.mydreamduels.dreamworldedit.util.Msg;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;

/** Starts player edits: limit check, one-at-a-time, history, and the "done" message. */
public final class EditService {

    private final DreamWorldEdit plugin;

    public EditService(DreamWorldEdit plugin) {
        this.plugin = plugin;
    }

    private int historySize() {
        return plugin.getConfig().getInt("history.size", 15);
    }

    /**
     * estimate = blocks the edit may touch (checked against the player's limit).
     * job gets the budget (max blocks it may change) and returns the running edit.
     */
    public void submit(Player player, String what, long estimate, LongFunction<CompletableFuture<EditResult>> job) {
        Session session = plugin.sessions().get(player);
        long limit = Limits.of(plugin, player);
        if (estimate > limit) {
            Msg.err(player, "That would touch " + estimate + " blocks - your limit is " + limit + ".");
            return;
        }
        if (!session.tryBusy()) {
            Msg.err(player, "Your previous edit is still running - please wait.");
            return;
        }
        if (estimate > 20000) Msg.send(player, "&7Working on &e" + what + "&7 (" + estimate + " blocks)...");
        CompletableFuture<EditResult> future;
        try {
            future = job.apply(limit);
        } catch (RuntimeException e) {
            session.done();
            Msg.err(player, e.getMessage() == null ? e.toString() : e.getMessage());
            return;
        }
        future.whenComplete((res, err) -> {
            session.done();
            if (err != null) {
                Msg.err(player, what + " failed: " + err.getMessage());
                plugin.getLogger().log(java.util.logging.Level.WARNING, what + " failed", err);
                return;
            }
            if (res.record() != null && res.record().size() > 0) session.remember(res.record(), historySize());
            Msg.send(player, "&a" + capital(what) + ": &f" + res.changed() + " &ablock" + (res.changed() == 1 ? "" : "s")
                    + " changed &8(" + res.millis() + " ms)");
            if (res.changed() >= limit) Msg.err(player, "Stopped at your limit of " + limit + " blocks.");
            if (res.error() != null) Msg.err(player, "Some chunks failed: " + res.error());
        });
    }

    public void undo(Player player, int times) {
        replay(player, Math.max(1, times), true);
    }

    public void redo(Player player, int times) {
        replay(player, Math.max(1, times), false);
    }

    private void replay(Player player, int times, boolean undo) {
        Session session = plugin.sessions().get(player);
        if (!session.tryBusy()) {
            Msg.err(player, "Your previous edit is still running - please wait.");
            return;
        }
        step(player, session, times, undo, 0, 0);
    }

    private void step(Player player, Session session, int left, boolean undo, int done, long blocks) {
        EditRecord rec = left > 0 ? (undo ? session.popUndo() : session.popRedo()) : null;
        if (rec == null) {
            session.done();
            if (done == 0) Msg.err(player, undo ? "Nothing left to undo." : "Nothing left to redo.");
            else Msg.ok(player, (undo ? "Undid " : "Redid ") + done + " edit" + (done == 1 ? "" : "s") + " (" + blocks + " blocks).");
            return;
        }
        plugin.engine().replay(rec, undo).whenComplete((res, err) -> {
            if (undo) session.pushRedo(rec);
            else session.pushUndoKeepRedo(rec);
            step(player, session, left - 1, undo, done + 1, blocks + (res == null ? 0 : res.changed()));
        });
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
