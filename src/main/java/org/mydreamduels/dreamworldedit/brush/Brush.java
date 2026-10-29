package org.mydreamduels.dreamworldedit.brush;

import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.DreamWorldEdit;
import org.mydreamduels.dreamworldedit.pattern.Mask;
import org.mydreamduels.dreamworldedit.pattern.Pattern;
import org.mydreamduels.dreamworldedit.util.BlockVec;

/** A tool bound to an item: right-click to use it where you're looking. */
public abstract class Brush {

    public volatile Pattern pattern;
    public volatile String patternText = "";
    public volatile Mask mask = Mask.ALL;
    public volatile String maskText = "";
    public volatile double size;
    public volatile int range = 100;

    protected Brush(Pattern pattern, String patternText, double size) {
        this.pattern = pattern;
        this.patternText = patternText;
        this.size = size;
    }

    public abstract String name();

    public abstract void use(DreamWorldEdit plugin, Player player, BlockVec target);

    public String describe() {
        return name() + " size " + (int) size + (patternText.isEmpty() ? "" : " " + patternText)
                + (maskText.isEmpty() ? "" : " mask " + maskText) + " range " + range;
    }
}
