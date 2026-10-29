package org.mydreamduels.dreamworldedit.pattern;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamworldedit.clipboard.Clipboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pattern syntax (WorldEdit style):
 *   stone                     one block
 *   oak_stairs[facing=north]  with block states
 *   stone,dirt                equal mix
 *   70%stone,30%cobblestone   weighted mix
 *   hand                      the block you're holding
 *   #clipboard                repeat your clipboard
 */
public final class Patterns {

    private Patterns() {
    }

    public static Pattern parse(String text, Player player, Clipboard clipboard) {
        if (text.equalsIgnoreCase("#clipboard") || text.equalsIgnoreCase("#copy")) {
            if (clipboard == null) throw new IllegalArgumentException("Your clipboard is empty.");
            return clipboard.asPattern();
        }
        List<BlockData> blocks = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String part : Blocks.splitTop(text, ',')) {
            String p = part.trim();
            double weight = 1;
            int pct = p.indexOf('%');
            if (pct > 0) {
                try {
                    weight = Double.parseDouble(p.substring(0, pct));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Bad percentage in '" + p + "'.");
                }
                p = p.substring(pct + 1);
            }
            BlockData data;
            if (p.equalsIgnoreCase("hand") || p.equalsIgnoreCase("#hand")) {
                Material m = player.getInventory().getItemInMainHand().getType();
                if (!m.isBlock()) throw new IllegalArgumentException("You're not holding a block.");
                data = m.createBlockData();
            } else {
                data = Blocks.parse(p);
            }
            if (data == null) throw new IllegalArgumentException("Unknown block '" + p + "'.");
            blocks.add(data);
            weights.add(weight);
        }
        if (blocks.size() == 1) {
            BlockData only = blocks.get(0);
            return (x, y, z) -> only;
        }
        double total = 0;
        double[] cumulative = new double[weights.size()];
        for (int i = 0; i < weights.size(); i++) {
            total += weights.get(i);
            cumulative[i] = total;
        }
        double sum = total;
        BlockData[] arr = blocks.toArray(new BlockData[0]);
        return (x, y, z) -> {
            double r = ThreadLocalRandom.current().nextDouble(sum);
            for (int i = 0; i < cumulative.length; i++) if (r < cumulative[i]) return arr[i];
            return arr[arr.length - 1];
        };
    }

    /**
     * Mask syntax:
     *   stone,dirt      any of these
     *   !stone          anything except
     *   #existing       not air        #solid  solid blocks     #liquid  water/lava
     *   >grass_block    directly ABOVE grass   <stone  directly BELOW stone
     *   a b             (space) all must match
     */
    public static Mask parseMask(String text) {
        List<Mask> all = new ArrayList<>();
        for (String term : text.trim().split("\\s+")) {
            if (!term.isEmpty()) all.add(parseTerm(term));
        }
        if (all.isEmpty()) return Mask.ALL;
        if (all.size() == 1) return all.get(0);
        return (ctx, x, y, z, d) -> {
            for (Mask m : all) if (!m.test(ctx, x, y, z, d)) return false;
            return true;
        };
    }

    private static Mask parseTerm(String term) {
        if (term.startsWith("!")) {
            Mask inner = parseTerm(term.substring(1));
            return (ctx, x, y, z, d) -> !inner.test(ctx, x, y, z, d);
        }
        if (term.startsWith(">")) {
            Mask below = parseTerm(term.substring(1));
            return (ctx, x, y, z, d) -> y > ctx.world.getMinHeight() && below.test(ctx, x, y - 1, z, ctx.get(x, y - 1, z));
        }
        if (term.startsWith("<")) {
            Mask above = parseTerm(term.substring(1));
            return (ctx, x, y, z, d) -> y < ctx.world.getMaxHeight() - 1 && above.test(ctx, x, y + 1, z, ctx.get(x, y + 1, z));
        }
        switch (term.toLowerCase(Locale.ROOT)) {
            case "#existing":
                return (ctx, x, y, z, d) -> !d.getMaterial().isAir();
            case "#solid":
                return (ctx, x, y, z, d) -> d.getMaterial().isSolid();
            case "#liquid":
                return (ctx, x, y, z, d) -> d.getMaterial() == Material.WATER || d.getMaterial() == Material.LAVA;
            case "#air":
            case "air":
                return (ctx, x, y, z, d) -> d.getMaterial().isAir();
            default:
                break;
        }
        List<BlockData> exact = new ArrayList<>();
        List<Material> types = new ArrayList<>();
        for (String part : Blocks.splitTop(term, ',')) {
            BlockData data = Blocks.parse(part);
            if (data == null) throw new IllegalArgumentException("Unknown block '" + part + "' in mask.");
            if (part.contains("[")) exact.add(data);
            else types.add(data.getMaterial());
        }
        return (ctx, x, y, z, d) -> {
            if (types.contains(d.getMaterial())) return true;
            for (BlockData e : exact) if (d.matches(e)) return true;
            return false;
        };
    }
}
