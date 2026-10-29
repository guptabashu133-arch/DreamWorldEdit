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
