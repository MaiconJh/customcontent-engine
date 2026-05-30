package com.customcontentengine.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.customcontentengine.adapter.persistence.PdcBlockCodec.PdcBlockEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdcBlockCodecTest {
    @Test
    void encodesEmptyCodecPayload() {
        PdcBlockCodec codec = new PdcBlockCodec();

        assertArrayEquals(new byte[] {1, 0, 0}, codec.encode(List.of()));
    }

    @Test
    void roundTripsEntries() {
        PdcBlockCodec codec = new PdcBlockCodec();
        List<PdcBlockEntry> entries = List.of(new PdcBlockEntry((short) 12, (short) 1));

        PdcBlockCodec.DecodedPdcBlocks decoded = codec.decode(codec.encode(entries));

        assertEquals(PdcBlockCodec.SCHEMA_VERSION, decoded.schemaVersion());
        assertEquals(entries, decoded.entries());
    }
}
