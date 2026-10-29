package org.mydreamduels.dreamworldedit.pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Blocks {

    public static final BlockData AIR = Material.AIR.createBlockData();

    private Blocks() {
    }

    /** "stone", "minecraft:oak_stairs[facing=north]" -> BlockData, or null. */
    public static BlockData parse(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        try {
            BlockData data = Bukkit.createBlockData(t.contains(":") || t.contains("[") ? t : "minecraft:" + t);
            return data.getMaterial().isBlock() ? data : null;
        } catch (IllegalArgumentException e) {
            Material m = Material.matchMaterial(t.replaceAll("\\[.*", ""));
            return m != null && m.isBlock() ? m.createBlockData() : null;
        }
    }

    /** Splits on commas that aren't inside [...] block states. */
    public static List<String> splitTop(String text, char sep) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '[') depth++;
            if (c == ']') depth--;
            if (c == sep && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static List<String> names;

    /** Block ids for tab completion. */
    public static synchronized List<String> names() {
        if (names == null) {
            List<String> n = new ArrayList<>();
            for (Material m : Material.values()) {
                if (m.isBlock() && !m.isLegacy()) n.add(m.name().toLowerCase(Locale.ROOT));
            }
            names = n;
        }
        return names;
    }
}
