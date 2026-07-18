package ua.nanit.limbo.world;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.io.*;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class DimensionRegistry {

    private final LimboServer server;
    private String dimensionType;

    // Compressed SNBT bytes (GZip) — ~500KB-1MB total, much smaller than parsed NBT trees
    private final Map<String, byte[]> compressedCodecs = new HashMap<>();

    // SoftReference cache: parsed trees survive under normal memory, reclaimed under pressure
    private final Map<String, SoftReference<CompoundBinaryTag>> codecCache = new HashMap<>();

    // Lazy-built dimension objects
    private volatile Dimension cacheDim_1_16;
    private volatile Dimension cacheDim_1_18_2;
    private volatile Dimension cacheDim_1_20_5;
    private volatile Dimension cacheDim_1_21;

    public DimensionRegistry(LimboServer server) {
        this.server = server;
    }

    public synchronized CompoundBinaryTag getCodec_1_16()    { return getOrParse("1_16"); }
    public synchronized CompoundBinaryTag getCodec_1_18_2()  { return getOrParse("1_18_2"); }
    public synchronized CompoundBinaryTag getCodec_1_19()     { return getOrParse("1_19"); }
    public synchronized CompoundBinaryTag getCodec_1_19_1()   { return getOrParse("1_19_1"); }
    public synchronized CompoundBinaryTag getCodec_1_19_4()   { return getOrParse("1_19_4"); }
    public synchronized CompoundBinaryTag getCodec_1_20()     { return getOrParse("1_20"); }
    public synchronized CompoundBinaryTag getCodec_1_21()     { return getOrParse("1_21"); }
    public synchronized CompoundBinaryTag getOldCodec()       { return getOrParse("old"); }

    public Dimension getDefaultDimension_1_16() {
        Dimension d = cacheDim_1_16;
        if (d == null) { d = extractDimFromLegacyCodec(getCodec_1_16()); cacheDim_1_16 = d; }
        return d;
    }

    public Dimension getDefaultDimension_1_18_2() {
        Dimension d = cacheDim_1_18_2;
        if (d == null) { d = extractDimFromLegacyCodec(getCodec_1_18_2()); cacheDim_1_18_2 = d; }
        return d;
    }

    public Dimension getDimension_1_20_5() {
        Dimension d = cacheDim_1_20_5;
        if (d == null) { d = dimFromModernCodec(getCodec_1_20()); cacheDim_1_20_5 = d; }
        return d;
    }

    public Dimension getDimension_1_21() {
        Dimension d = cacheDim_1_21;
        if (d == null) { d = dimFromModernCodec(getCodec_1_21()); cacheDim_1_21 = d; }
        return d;
    }

    public void load(String def) throws IOException {
        this.dimensionType = def;

        compressedCodecs.put("old",    compress(readResource("/dimension/codec_old.snbt")));
        compressedCodecs.put("1_16",   compress(readResource("/dimension/codec_1_16.snbt")));
        compressedCodecs.put("1_18_2", compress(readResource("/dimension/codec_1_18_2.snbt")));
        compressedCodecs.put("1_19",   compress(readResource("/dimension/codec_1_19.snbt")));
        compressedCodecs.put("1_19_1", compress(readResource("/dimension/codec_1_19_1.snbt")));
        compressedCodecs.put("1_19_4", compress(readResource("/dimension/codec_1_19_4.snbt")));
        compressedCodecs.put("1_20",   compress(readResource("/dimension/codec_1_20.snbt")));
        compressedCodecs.put("1_21",   compress(readResource("/dimension/codec_1_21.snbt")));

        Log.info("[server] World data loaded (lazy parsing)");
    }

    private byte[] compress(String snbt) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(snbt.length() / 3);
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(snbt.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private CompoundBinaryTag getOrParse(String key) {
        SoftReference<CompoundBinaryTag> ref = codecCache.get(key);
        CompoundBinaryTag tag = (ref != null) ? ref.get() : null;
        if (tag == null) {
            tag = parseCodec(key);
            codecCache.put(key, new SoftReference<>(tag));
        }
        return tag;
    }

    private CompoundBinaryTag parseCodec(String key) {
        byte[] compressed = compressedCodecs.get(key);
        if (compressed == null) throw new IllegalStateException("No codec: " + key);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            try (java.io.InputStream gzis = new GZIPInputStream(bais)) {
                int len;
                while ((len = gzis.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            }
            String snbt = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            return TagStringIO.get().asCompound(snbt);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse codec: " + key, e);
        }
    }

    private Dimension extractDimFromLegacyCodec(CompoundBinaryTag codec) {
        String def = dimensionType;
        ListBinaryTag dimensions = codec.getCompound("minecraft:dimension_type").getList("value");

        // Look up dimension by name rather than hardcoded list index (robust against reordering)
        CompoundBinaryTag overworld = null, nether = null, theEnd = null;
        for (int i = 0; i < dimensions.size(); i++) {
            CompoundBinaryTag dimTag = (CompoundBinaryTag) ((CompoundBinaryTag) dimensions.get(i)).get("element");
            String name = ((CompoundBinaryTag) dimensions.get(i)).getString("name");
            if ("minecraft:overworld".equals(name)) overworld = dimTag;
            else if ("minecraft:the_nether".equals(name)) nether = dimTag;
            else if ("minecraft:the_end".equals(name)) theEnd = dimTag;
        }

        switch (def.toLowerCase()) {
            case "overworld":
                if (overworld == null) break;
                return new Dimension(0, "minecraft:overworld", overworld);
            case "the_nether": {
                if (nether == null) break;
                return new Dimension(-1, "minecraft:nether", nether);
            }
            case "the_end":
                if (theEnd == null) break;
                return new Dimension(1, "minecraft:the_end", theEnd);
        }
        Log.warning("Undefined dimension type: '%s'. Using THE_END as default", def);
        return new Dimension(1, "minecraft:the_end", theEnd != null ? theEnd : overworld);
    }

    private Dimension dimFromModernCodec(CompoundBinaryTag codec) {
        String def = dimensionType;
        switch (def.toLowerCase()) {
            case "overworld":   return new Dimension(0, "minecraft:overworld", codec);
            case "the_nether":  return new Dimension(2, "minecraft:nether", codec);
            case "the_end":     return new Dimension(3, "minecraft:the_end", codec);
            default:
                Log.warning("Undefined dimension type: '%s'. Using THE_END as default", def);
                return new Dimension(3, "minecraft:the_end", codec);
        }
    }

    private String readResource(String resPath) throws IOException {
        InputStream in = server.getClass().getResourceAsStream(resPath);
        if (in == null) throw new FileNotFoundException("Cannot find dimension registry file");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(4096);
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}
