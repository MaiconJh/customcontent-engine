package com.customcontentengine.adapter.persistence;

import java.nio.ByteBuffer;
import java.util.List;

public final class PdcBlockCodec {
    public static final byte SCHEMA_VERSION = 1;
    private static final int HEADER_BYTES = 3;
    private static final int ENTRY_BYTES = 4;

    public byte[] encode(List<PdcBlockEntry> entries) {
        List<PdcBlockEntry> safeEntries = List.copyOf(entries == null ? List.of() : entries);
        if (safeEntries.size() > 0xFFFF) {
            throw new IllegalArgumentException("Too many PDC block entries");
        }
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + safeEntries.size() * ENTRY_BYTES);
        buffer.put(SCHEMA_VERSION);
        buffer.putShort((short) safeEntries.size());
        for (PdcBlockEntry entry : safeEntries) {
            buffer.putShort(entry.packedPosition());
            buffer.putShort(entry.numericId());
        }
        return buffer.array();
    }

    public DecodedPdcBlocks decode(byte[] data) {
        if (data == null || data.length == 0) {
            return new DecodedPdcBlocks(SCHEMA_VERSION, List.of());
        }
        if (data.length < HEADER_BYTES) {
            throw new IllegalArgumentException("PDC block data is shorter than the header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte schemaVersion = buffer.get();
        int count = Short.toUnsignedInt(buffer.getShort());
        int expectedLength = HEADER_BYTES + count * ENTRY_BYTES;
        if (data.length != expectedLength) {
            throw new IllegalArgumentException("PDC block data length does not match entry count");
        }
        java.util.ArrayList<PdcBlockEntry> entries = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new PdcBlockEntry(buffer.getShort(), buffer.getShort()));
        }
        return new DecodedPdcBlocks(schemaVersion, entries);
    }

    public record PdcBlockEntry(short packedPosition, short numericId) {
    }

    public record DecodedPdcBlocks(byte schemaVersion, List<PdcBlockEntry> entries) {
        public DecodedPdcBlocks {
            entries = List.copyOf(entries == null ? List.of() : entries);
        }
    }
}
