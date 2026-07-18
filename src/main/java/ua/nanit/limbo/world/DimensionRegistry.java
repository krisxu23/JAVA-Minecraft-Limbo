package ua.nanit.limbo.world;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DimensionRegistry {
    private final LimboServer server;
    private byte[] registryData;
    private Dimension dimension1_21;
    private final Map<String, Dimension> dimensionCache = new HashMap<>();

    public DimensionRegistry(LimboServer server) { this.server = server; }

    public void load(String dimensionType) throws Exception {
        String fileName = getFileName(dimensionType);
        if (fileName == null) {
            Log.warn("Unknown dimension type: %s, defaulting to 1.21", dimensionType);
            fileName = "dimension/codec_1_21.snbt";
        }

        dimensionCache.put("default", Dimension.fromResource(fileName));
        registryData = dimensionCache.get("default").getSerialized();
        dimension1_21 = dimensionCache.get("default");

        // Also load dimension for 1.21+ registry format
        try {
            dimension1_21 = Dimension.fromResource("dimension/codec_1_21.snbt");
        } catch (Exception ignored) {}

        Log.info("Loaded dimension registry: %s (%s)", fileName, dimensionType);
    }

    private String getFileName(String dimensionType) {
        String lower = dimensionType.toLowerCase();
        if (lower.contains("1_21")) return "dimension/codec_1_21.snbt";
        if (lower.contains("1_20")) return "dimension/codec_1_20.snbt";
        if (lower.contains("1_19_4")) return "dimension/codec_1_19_4.snbt";
        if (lower.contains("1_19_1")) return "dimension/codec_1_19_1.snbt";
        if (lower.contains("1_19")) return "dimension/codec_1_19.snbt";
        if (lower.contains("1_18")) return "dimension/codec_1_18_2.snbt";
        if (lower.contains("1_16")) return "dimension/codec_1_16.snbt";
        return null;
    }

    public byte[] getRegistryData() { return registryData; }
    public Dimension getDimension_1_21() { return dimension1_21; }
}
