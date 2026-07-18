package ua.nanit.limbo.protocol;

import ua.nanit.limbo.protocol.registry.Version;

public interface MetadataWriter {
    void write(ByteMessage message, Version version);
}
