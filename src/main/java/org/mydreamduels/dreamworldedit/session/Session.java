package org.mydreamduels.dreamworldedit.session;

import org.bukkit.Material;
import org.mydreamduels.dreamworldedit.brush.Brush;
import org.mydreamduels.dreamworldedit.clipboard.Clipboard;
import org.mydreamduels.dreamworldedit.edit.EditRecord;
import org.mydreamduels.dreamworldedit.region.Selection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-player WorldEdit state. Survives reconnects until the server restarts (like WorldEdit). */
public final class Session {

    private final Selection selection = new Selection();
    private volatile Clipboard clipboard;
    private final Deque<EditRecord> undo = new ArrayDeque<>();
    private final Deque<EditRecord> redo = new ArrayDeque<>();
    private final Map<Material, Brush> brushes = new ConcurrentHashMap<>();
    private final AtomicBoolean busy = new AtomicBoolean();

    public Selection selection() {
        return selection;
    }

    public Clipboard clipboard() {
        return clipboard;
    }

    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    /** Only one edit per player at a time - returns false if one is already running. */
    public boolean tryBusy() {
        return busy.compareAndSet(false, true);
    }

    public void done() {
        busy.set(false);
    }

    public boolean isBusy() {
        return busy.get();
    }

    public synchronized void remember(EditRecord rec, int max) {
        undo.push(rec);
        redo.clear();
        while (undo.size() > Math.max(1, max)) undo.removeLast();
    }

    public synchronized EditRecord popUndo() {
        return undo.poll();
    }

    public synchronized void pushRedo(EditRecord rec) {
        redo.push(rec);
    }

    public synchronized EditRecord popRedo() {
        return redo.poll();
    }

    public synchronized void pushUndoKeepRedo(EditRecord rec) {
        undo.push(rec);
    }

    public synchronized void clearHistory() {
        undo.clear();
        redo.clear();
    }

    public synchronized int undoSize() {
        return undo.size();
    }

    public Map<Material, Brush> brushes() {
        return brushes;
    }
}
