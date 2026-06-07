package com.customcontentengine.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.List;
import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PdcBlockStoreTest {
    private static final NamespacedKey KEY = new NamespacedKey("customcontentengine", "custom_blocks");

    @Test
    void writesNewBlockEntryToChunkPdc() {
        PdcBlockCodec codec = new PdcBlockCodec();
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(container);
        PdcBlockStore store = new PdcBlockStore(codec, KEY, position -> chunk);

        store.put(new WorldPosition("world", 10, 64, 12), (short) 1);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(container).set(
                org.mockito.ArgumentMatchers.eq(KEY),
                org.mockito.ArgumentMatchers.eq(PersistentDataType.BYTE_ARRAY),
                dataCaptor.capture());
        assertEquals(
                new PdcBlockCodec.PdcBlockEntry(codec.packRelativePosition(10, 64, 12), (short) 1),
                codec.decode(dataCaptor.getValue()).entries().getFirst());
    }

    @Test
    void updatesExistingBlockEntryAtSameRelativePosition() {
        PdcBlockCodec codec = new PdcBlockCodec();
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(container);
        short packedPosition = codec.packRelativePosition(10, 64, 12);
        when(container.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(codec.encode(List.of(
                new PdcBlockCodec.PdcBlockEntry(packedPosition, (short) 1))));
        PdcBlockStore store = new PdcBlockStore(codec, KEY, position -> chunk);

        store.put(new WorldPosition("world", 10, 64, 12), (short) 2);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(container).set(
                org.mockito.ArgumentMatchers.eq(KEY),
                org.mockito.ArgumentMatchers.eq(PersistentDataType.BYTE_ARRAY),
                dataCaptor.capture());
        assertEquals(
                List.of(new PdcBlockCodec.PdcBlockEntry(packedPosition, (short) 2)),
                codec.decode(dataCaptor.getValue()).entries());
    }

    @Test
    void findsExistingNumericIdAtRelativePosition() {
        PdcBlockCodec codec = new PdcBlockCodec();
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(container);
        when(container.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(codec.encode(List.of(
                new PdcBlockCodec.PdcBlockEntry(codec.packRelativePosition(10, 64, 12), (short) 7))));
        PdcBlockStore store = new PdcBlockStore(codec, KEY, position -> chunk);

        assertEquals(Optional.of((short) 7), store.findNumericId(new WorldPosition("world", 10, 64, 12)));
    }

    @Test
    void returnsEmptyWhenRelativePositionIsNotStored() {
        PdcBlockCodec codec = new PdcBlockCodec();
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(container);
        when(container.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(codec.encode(List.of(
                new PdcBlockCodec.PdcBlockEntry(codec.packRelativePosition(10, 64, 12), (short) 7))));
        PdcBlockStore store = new PdcBlockStore(codec, KEY, position -> chunk);

        assertTrue(store.findNumericId(new WorldPosition("world", 11, 64, 12)).isEmpty());
    }

    @Test
    void removesBlockEntryAtRelativePosition() {
        PdcBlockCodec codec = new PdcBlockCodec();
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(chunk.getPersistentDataContainer()).thenReturn(container);
        short removedPosition = codec.packRelativePosition(10, 64, 12);
        short remainingPosition = codec.packRelativePosition(11, 64, 12);
        when(container.get(KEY, PersistentDataType.BYTE_ARRAY)).thenReturn(codec.encode(List.of(
                new PdcBlockCodec.PdcBlockEntry(removedPosition, (short) 7),
                new PdcBlockCodec.PdcBlockEntry(remainingPosition, (short) 8))));
        PdcBlockStore store = new PdcBlockStore(codec, KEY, position -> chunk);

        store.remove(new WorldPosition("world", 10, 64, 12));

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(container).set(
                org.mockito.ArgumentMatchers.eq(KEY),
                org.mockito.ArgumentMatchers.eq(PersistentDataType.BYTE_ARRAY),
                dataCaptor.capture());
        assertEquals(
                List.of(new PdcBlockCodec.PdcBlockEntry(remainingPosition, (short) 8)),
                codec.decode(dataCaptor.getValue()).entries());
    }
}
