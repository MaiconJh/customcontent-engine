package com.customcontentengine.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void packsRelativePositionIntoShort() {
        PdcBlockCodec codec = new PdcBlockCodec();

        assertEquals((short) 0x40CA, codec.packRelativePosition(10, 64, 12));
    }

    @Test
    void rejectsPackedPositionYOutsideCurrentCodecRange() {
        PdcBlockCodec codec = new PdcBlockCodec();

        assertThrows(IllegalArgumentException.class, () -> codec.packRelativePosition(0, 256, 0));
    }
}
