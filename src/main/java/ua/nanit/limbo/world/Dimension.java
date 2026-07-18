package ua.nanit.limbo.world;

import net.kyori.adventure.nbt.BinaryTagIo;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Dimension {
    private CompoundBinaryTag data;
    private byte[] serialized;

    public CompoundBinaryTag getData() { return data; }
    public byte[] getSerialized() { return serialized; }

    public static Dimension fromResource(String resourceName) throws Exception {
        Dimension dim = new Dimension();
        InputStream is = dim.getClass().getClassLoader().getResourceAsStream(resourceName);
        if (is == null) {
            // Try from resources directory
            Path resPath = Path.of(resourceName);
            if (Files.exists(resPath)) {
                String snbt = new String(Files.readAllBytes(resPath), StandardCharsets.UTF_8);
                dim.data = net.kyori.adventure.nbt.TagStringIO.stringLoader().load(snbt);
            }
        } else {
            String snbt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            dim.data = net.kyori.adventure.nbt.TagStringIO.stringLoader().load(snbt);
        }
        dim.serialized = BinaryTagIo.DEFAULT_CODEC().serialize(dim.data);
        return dim;
    }
}
