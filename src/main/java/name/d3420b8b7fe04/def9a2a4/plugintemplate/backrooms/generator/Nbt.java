package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal reader for gzipped, big-endian (Java edition) NBT, enough for schematic files.
 *
 * <p>Compounds become {@code Map<String, Object>}, lists {@code List<Object>}, byte/int/long
 * arrays {@code byte[]}/{@code int[]}/{@code long[]}, and scalars their boxed Java type
 * (Byte, Short, Integer, Long, Float, Double, String).
 */
final class Nbt {

    private Nbt() {}

    /** Reads a gzipped NBT file and returns its root compound. */
    static Map<String, Object> readGzipped(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in), 1 << 16));
        if (d.readUnsignedByte() != 10) throw new IOException("NBT root is not a compound");
        d.readUTF();  // root name
        return compound(d);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> compound(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    static int integer(Map<String, Object> parent, String key, int fallback) {
        return parent.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    static String string(Map<String, Object> parent, String key, String fallback) {
        return parent.get(key) instanceof String s ? s : fallback;
    }

    private static Map<String, Object> compound(DataInputStream d) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int type; (type = d.readUnsignedByte()) != 0; ) {
            String name = d.readUTF();
            out.put(name, payload(d, type));
        }
        return out;
    }

    private static Object payload(DataInputStream d, int type) throws IOException {
        switch (type) {
            case 1: return d.readByte();
            case 2: return d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: {
                byte[] a = new byte[d.readInt()];
                d.readFully(a);
                return a;
            }
            case 8: return d.readUTF();
            case 9: {
                int inner = d.readUnsignedByte(), n = d.readInt();
                List<Object> l = new ArrayList<>(Math.max(n, 0));
                for (int i = 0; i < n; i++) l.add(payload(d, inner));
                return l;
            }
            case 10: return compound(d);
            case 11: {
                int[] a = new int[d.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = d.readInt();
                return a;
            }
            case 12: {
                long[] a = new long[d.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = d.readLong();
                return a;
            }
            default: throw new IOException("unknown NBT tag " + type);
        }
    }
}
