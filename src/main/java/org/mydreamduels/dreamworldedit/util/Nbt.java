package org.mydreamduels.dreamworldedit.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal named binary tag reader/writer - just enough for Sponge
 * schematics (.schem). Compounds are LinkedHashMaps, lists are Lists,
 * arrays are byte[]/int[]/long[], everything else the boxed Java type.
 */
public final class Nbt {

    private static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;
    private static final int MAX_DEPTH = 512;

    private Nbt() {
    }

    /** Root tag of a gzipped file: [name, value]. */
    public static Map.Entry<String, Map<String, Object>> readGzip(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(new GZIPInputStream(in));
        int type = data.readUnsignedByte();
        if (type != COMPOUND) throw new IOException("Not an NBT compound (type " + type + ")");
        String name = data.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) read(data, COMPOUND, 0);
        return Map.entry(name, value);
    }

    private static Object read(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nested too deeply");
        switch (type) {
            case BYTE: return in.readByte();
            case SHORT: return in.readShort();
            case INT: return in.readInt();
            case LONG: return in.readLong();
            case FLOAT: return in.readFloat();
            case DOUBLE: return in.readDouble();
            case BYTE_ARRAY: {
                byte[] b = new byte[checkedLength(in.readInt())];
                in.readFully(b);
                return b;
            }
            case STRING: return in.readUTF();
            case LIST: {
                int elementType = in.readUnsignedByte();
                int len = checkedLength(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(len, 1024));
                for (int i = 0; i < len; i++) list.add(read(in, elementType, depth + 1));
                return list;
            }
            case COMPOUND: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == END) break;
                    String key = in.readUTF();
                    map.put(key, read(in, t, depth + 1));
                }
                return map;
            }
            case INT_ARRAY: {
                int[] a = new int[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case LONG_ARRAY: {
                long[] a = new long[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int checkedLength(int len) throws IOException {
        if (len < 0 || len > 256 * 1024 * 1024) throw new IOException("Bad NBT array length " + len);
        return len;
    }

    public static void writeGzip(OutputStream out, String rootName, Map<String, Object> root) throws IOException {
        GZIPOutputStream gz = new GZIPOutputStream(out);
        DataOutputStream data = new DataOutputStream(gz);
        data.writeByte(COMPOUND);
        data.writeUTF(rootName);
        write(data, root);
        data.flush();
        gz.finish();
    }

    private static int typeOf(Object v) {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof byte[]) return BYTE_ARRAY;
        if (v instanceof String) return STRING;
        if (v instanceof List) return LIST;
        if (v instanceof Map) return COMPOUND;
        if (v instanceof int[]) return INT_ARRAY;
        if (v instanceof long[]) return LONG_ARRAY;
        throw new IllegalArgumentException("Can't write " + v.getClass() + " as NBT");
    }

    @SuppressWarnings("unchecked")
    private static void write(DataOutputStream out, Object v) throws IOException {
        switch (typeOf(v)) {
            case BYTE -> out.writeByte((Byte) v);
            case SHORT -> out.writeShort((Short) v);
            case INT -> out.writeInt((Integer) v);
            case LONG -> out.writeLong((Long) v);
            case FLOAT -> out.writeFloat((Float) v);
            case DOUBLE -> out.writeDouble((Double) v);
            case BYTE_ARRAY -> {
                byte[] b = (byte[]) v;
                out.writeInt(b.length);
                out.write(b);
            }
            case STRING -> out.writeUTF((String) v);
            case LIST -> {
                List<Object> list = (List<Object>) v;
                int t = list.isEmpty() ? END : typeOf(list.get(0));
                out.writeByte(list.isEmpty() ? COMPOUND : t);
                out.writeInt(list.size());
                for (Object o : list) write(out, o);
            }
            case COMPOUND -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                    if (e.getValue() == null) continue;
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    write(out, e.getValue());
                }
                out.writeByte(END);
            }
            case INT_ARRAY -> {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int i : a) out.writeInt(i);
            }
            case LONG_ARRAY -> {
                long[] a = (long[]) v;
                out.writeInt(a.length);
                for (long l : a) out.writeLong(l);
            }
            default -> throw new IllegalStateException();
        }
    }
}
