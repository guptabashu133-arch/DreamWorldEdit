package org.mydreamduels.dreamworldedit.clipboard;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.mydreamduels.dreamworldedit.pattern.Blocks;
import org.mydreamduels.dreamworldedit.util.BlockVec;
import org.mydreamduels.dreamworldedit.util.Nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sponge schematic (.schem) - the format WorldEdit 7 and FastAsyncWorldEdit
 * use. Reads versions 2 and 3, writes version 2 (which every WorldEdit/FAWE
 * still reads). Blocks only: block entity contents (chest items, sign
 * text) and entities are not copied.
 */
public final class SchematicIO {

    public record Loaded(Clipboard clipboard, int unknownBlocks) {
    }

    private SchematicIO() {
    }

    @SuppressWarnings("unchecked")
    public static Loaded load(File file) throws IOException {
        Map<String, Object> s;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            s = Nbt.readGzip(in).getValue();
        }
        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic") || s.get("Regions") instanceof Map<?, ?>) {
            return loadLitematic(s);
        }
        if (s.get("Schematic") instanceof Map<?, ?> inner) s = (Map<String, Object>) inner;
        int version = num(s.get("Version"), 1);
        int width = num(s.get("Width"), 0) & 0xFFFF;
        int height = num(s.get("Height"), 0) & 0xFFFF;
        int length = num(s.get("Length"), 0) & 0xFFFF;
        if (width == 0 || height == 0 || length == 0) throw new IOException("Not a Sponge schematic (no size). Old MCEdit .schematic files aren't supported.");

        Map<String, Object> palette;
        byte[] data;
        BlockVec offset = new BlockVec(0, 0, 0);
        if (version >= 3) {
            Map<String, Object> blocks = (Map<String, Object>) s.get("Blocks");
            if (blocks == null) throw new IOException("Schematic v3 without Blocks");
            palette = (Map<String, Object>) blocks.get("Palette");
            data = (byte[]) blocks.get("Data");
            if (s.get("Offset") instanceof int[] o && o.length == 3) offset = new BlockVec(o[0], o[1], o[2]);
        } else {
            palette = (Map<String, Object>) s.get("Palette");
            data = (byte[]) s.get("BlockData");
            if (s.get("Metadata") instanceof Map<?, ?> meta && meta.containsKey("WEOffsetX")) {
                offset = new BlockVec(num(meta.get("WEOffsetX"), 0), num(meta.get("WEOffsetY"), 0), num(meta.get("WEOffsetZ"), 0));
            }
        }
        if (palette == null || data == null) throw new IOException("Schematic is missing its palette or block data");

        Map<Integer, BlockData> byId = new HashMap<>();
        int unknown = 0;
        Map<String, BlockData> cache = new HashMap<>();
        for (Map.Entry<String, Object> e : palette.entrySet()) {
            BlockData b = cache.computeIfAbsent(e.getKey(), SchematicIO::parseState);
            if (b == null) {
                unknown++;
                b = Blocks.AIR;
            }
            byId.put(num(e.getValue(), 0), b);
        }

        Clipboard clip = Clipboard.empty(width, height, length, offset, null);
        int total = width * height * length;
        int i = 0, index = 0;
        while (i < data.length && index < total) {
            int value = 0, shift = 0, b;
            do {
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 35) throw new IOException("Corrupt varint in block data");
            } while ((b & 0x80) != 0 && i < data.length);
            BlockData d = byId.getOrDefault(value, Blocks.AIR);
            int y = index / (width * length);
            int rem = index % (width * length);
            int z = rem / width;
            int x = rem % width;
            clip.set(x, y, z, d);
            index++;
        }
        return new Loaded(clip, unknown);
    }

    /**
     * Litematica .litematic: one or more regions, each with its own palette and a tightly packed
     * long[] of palette indexes (an entry may span two longs). All regions go into one clipboard;
     * the offset keeps Litematica's placement origin, so the build lands where Litematica's
     * placement shows it when the player stands on the origin.
     */
    @SuppressWarnings("unchecked")
    private static Loaded loadLitematic(Map<String, Object> root) throws IOException {
        if (!(root.get("Regions") instanceof Map<?, ?> regionsRaw) || regionsRaw.isEmpty()) throw new IOException("Litematic without regions");
        Map<String, Object> regions = (Map<String, Object>) regionsRaw;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        record Reg(int x, int y, int z, int sx, int sy, int sz, Map<String, Object> data) {
        }
        java.util.List<Reg> list = new java.util.ArrayList<>();
        for (Object o : regions.values()) {
            if (!(o instanceof Map<?, ?> rr)) continue;
            Map<String, Object> r = (Map<String, Object>) rr;
            int[] pos = vec(r.get("Position")), size = vec(r.get("Size"));
            if (pos == null || size == null) continue;
            int sx = Math.abs(size[0]), sy = Math.abs(size[1]), sz = Math.abs(size[2]);
            if (sx == 0 || sy == 0 || sz == 0) continue;
            // a negative size means the region grows the other way from its position
            int x = pos[0] + (size[0] < 0 ? size[0] + 1 : 0), y = pos[1] + (size[1] < 0 ? size[1] + 1 : 0), z = pos[2] + (size[2] < 0 ? size[2] + 1 : 0);
            list.add(new Reg(x, y, z, sx, sy, sz, r));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + sx - 1);
            maxY = Math.max(maxY, y + sy - 1);
            maxZ = Math.max(maxZ, z + sz - 1);
        }
        if (list.isEmpty()) throw new IOException("Litematic has no usable regions");
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 200_000_000L) throw new IOException("Litematic is too big (" + volume + " blocks)");
        Clipboard clip = Clipboard.empty(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, new BlockVec(minX, minY, minZ), null);
        int unknown = 0;
        Map<String, BlockData> cache = new HashMap<>();
        for (Reg reg : list) {
            java.util.List<Object> pal = reg.data().get("BlockStatePalette") instanceof java.util.List<?> l ? (java.util.List<Object>) l : java.util.List.of();
            BlockData[] palette = new BlockData[Math.max(1, pal.size())];
            for (int i = 0; i < pal.size(); i++) {
                String state = stateString(pal.get(i));
                BlockData b = cache.computeIfAbsent(state, SchematicIO::parseState);
                if (b == null) {
                    unknown++;
                    b = Blocks.AIR;
                }
                palette[i] = b;
            }
            if (!(reg.data().get("BlockStates") instanceof long[] packed)) continue;
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length - 1)));
            long mask = (1L << bits) - 1;
            int layer = reg.sx() * reg.sz();
            long total = (long) layer * reg.sy();
            for (long index = 0; index < total; index++) {
                long start = index * bits;
                int startArr = (int) (start >> 6), endArr = (int) (((index + 1) * bits - 1) >> 6);
                if (endArr >= packed.length) break;
                int startBit = (int) (start & 63);
                int value = (int) (startArr == endArr ? (packed[startArr] >>> startBit) & mask
                        : ((packed[startArr] >>> startBit) | (packed[endArr] << (64 - startBit))) & mask);
                BlockData d = value < palette.length ? palette[value] : Blocks.AIR;
                if (d == null) continue;
                int y = (int) (index / layer), rem = (int) (index % layer), z = rem / reg.sx(), x = rem % reg.sx();
                clip.set(reg.x() - minX + x, reg.y() - minY + y, reg.z() - minZ + z, d);
            }
        }
        // anything no region covered stays empty (null = skipped by paste)
        return new Loaded(clip, unknown);
    }

    private static int[] vec(Object o) {
        if (!(o instanceof Map<?, ?> m)) return null;
        return new int[]{num(m.get("x"), 0), num(m.get("y"), 0), num(m.get("z"), 0)};
    }

    /** {Name:"minecraft:oak_stairs", Properties:{facing:"north"...}} -> "minecraft:oak_stairs[facing=north,...]" */
    private static String stateString(Object o) {
        if (!(o instanceof Map<?, ?> m)) return "minecraft:air";
        String name = String.valueOf(m.get("Name") == null ? "minecraft:air" : m.get("Name"));
        if (m.get("Properties") instanceof Map<?, ?> props && !props.isEmpty()) {
            StringBuilder b = new StringBuilder(name).append('[');
            boolean first = true;
            for (Map.Entry<?, ?> e : props.entrySet()) {
                if (!first) b.append(',');
                b.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            return b.append(']').toString();
        }
        return name;
    }

    private static BlockData parseState(String state) {
        try {
            return Bukkit.createBlockData(state);
        } catch (IllegalArgumentException e) {
            return Blocks.parse(state.replaceAll("\\[.*", ""));
        }
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    @SuppressWarnings("deprecation")
    public static void save(Clipboard clip, File file) throws IOException {
        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int y = 0; y < clip.height(); y++) {
            for (int z = 0; z < clip.length(); z++) {
                for (int x = 0; x < clip.width(); x++) {
                    BlockData d = clip.get(x, y, z);
                    String key = (d == null ? Blocks.AIR : d).getAsString();
                    Integer id = palette.get(key);
                    if (id == null) {
                        id = palette.size();
                        palette.put(key, id);
                    }
                    int v = id;
                    while ((v & ~0x7F) != 0) {
                        bytes.write((v & 0x7F) | 0x80);
                        v >>>= 7;
                    }
                    bytes.write(v);
                }
            }
        }
        Map<String, Object> pal = new LinkedHashMap<>(palette);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("WEOffsetX", clip.offset().x());
        meta.put("WEOffsetY", clip.offset().y());
        meta.put("WEOffsetZ", clip.offset().z());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 2);
        root.put("DataVersion", Bukkit.getUnsafe().getDataVersion());
        root.put("Width", (short) clip.width());
        root.put("Height", (short) clip.height());
        root.put("Length", (short) clip.length());
        root.put("Offset", new int[]{0, 0, 0});
        root.put("Metadata", meta);
        root.put("PaletteMax", palette.size());
        root.put("Palette", pal);
        root.put("BlockData", bytes.toByteArray());
        root.put("BlockEntities", new ArrayList<>());

        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            Nbt.writeGzip(out, "Schematic", root);
        }
    }
}
