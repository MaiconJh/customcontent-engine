package com.customcontentengine.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void findsNumericIdByPackedPosition() {
        PdcBlockCodec codec = new PdcBlockCodec();
        short packedPosition = codec.packRelativePosition(10, 64, 12);
        byte[] data = codec.encode(List.of(new PdcBlockEntry(packedPosition, (short) 7)));

        assertEquals((short) 7, codec.findNumericId(data, packedPosition).orElseThrow());
    }

    @Test
    void removesEntryByPackedPositionWithoutChangingFormat() {
        PdcBlockCodec codec = new PdcBlockCodec();
        short removedPosition = codec.packRelativePosition(10, 64, 12);
        short remainingPosition = codec.packRelativePosition(11, 64, 12);
        byte[] data = codec.encode(List.of(
                new PdcBlockEntry(removedPosition, (short) 7),
                new PdcBlockEntry(remainingPosition, (short) 8)));

        PdcBlockCodec.DecodedPdcBlocks decoded = codec.decode(codec.remove(data, removedPosition));

        assertEquals(PdcBlockCodec.SCHEMA_VERSION, decoded.schemaVersion());
        assertEquals(List.of(new PdcBlockEntry(remainingPosition, (short) 8)), decoded.entries());
        assertTrue(codec.findNumericId(codec.remove(data, removedPosition), removedPosition).isEmpty());
    }
}
