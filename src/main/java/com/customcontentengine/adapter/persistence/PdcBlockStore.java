package com.customcontentengine.adapter.persistence;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PdcBlockStore implements BlockStorePort {
    private final PdcBlockCodec codec;
    private final NamespacedKey storageKey;
    private final Function<WorldPosition, Chunk> chunkResolver;

    public PdcBlockStore(Plugin plugin, PdcBlockCodec codec) {
        this(
                codec,
                new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "custom_blocks"),
                PdcBlockStore::resolveChunk);
    }

    PdcBlockStore(PdcBlockCodec codec, NamespacedKey storageKey, Function<WorldPosition, Chunk> chunkResolver) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.chunkResolver = Objects.requireNonNull(chunkResolver, "chunkResolver");
    }

    @Override
    public Optional<Short> findNumericId(WorldPosition position) {
        Objects.requireNonNull(position, "position");
        byte[] data = chunkResolver.apply(position)
                .getPersistentDataContainer()
                .get(storageKey, PersistentDataType.BYTE_ARRAY);
        short packedPosition = packedPosition(position);
        return codec.decode(data).entries().stream()
                .filter(entry -> entry.packedPosition() == packedPosition)
                .map(PdcBlockCodec.PdcBlockEntry::numericId)
                .findFirst();
    }

    @Override
    public void put(WorldPosition position, short numericId) {
        Objects.requireNonNull(position, "position");
        if (numericId <= 0) {
            throw new IllegalArgumentException("numericId must be positive");
        }

        Chunk chunk = chunkResolver.apply(position);
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        byte[] existingData = container.get(storageKey, PersistentDataType.BYTE_ARRAY);
        List<PdcBlockCodec.PdcBlockEntry> entries = new ArrayList<>(codec.decode(existingData).entries());
        short packedPosition = packedPosition(position);

        boolean updated = false;
        for (int index = 0; index < entries.size(); index++) {
            PdcBlockCodec.PdcBlockEntry entry = entries.get(index);
            if (entry.packedPosition() == packedPosition) {
                entries.set(index, new PdcBlockCodec.PdcBlockEntry(packedPosition, numericId));
                updated = true;
                break;
            }
        }
        if (!updated) {
            entries.add(new PdcBlockCodec.PdcBlockEntry(packedPosition, numericId));
        }

        container.set(storageKey, PersistentDataType.BYTE_ARRAY, codec.encode(entries));
    }

    @Override
    public void remove(WorldPosition position) {
        // Removing custom block identity is intentionally deferred until custom block breaking is in scope.
    }

    public PdcBlockCodec codec() {
        return codec;
    }

    public NamespacedKey storageKey() {
        return storageKey;
    }

    private short packedPosition(WorldPosition position) {
        return codec.packRelativePosition(
                Math.floorMod(position.x(), 16),
                position.y(),
                Math.floorMod(position.z(), 16));
    }

    private static Chunk resolveChunk(WorldPosition position) {
        World world = Bukkit.getWorld(position.worldName());
        if (world == null) {
            throw new IllegalArgumentException("Unknown world: " + position.worldName());
        }
        return world.getChunkAt(position.x() >> 4, position.z() >> 4);
    }
}
