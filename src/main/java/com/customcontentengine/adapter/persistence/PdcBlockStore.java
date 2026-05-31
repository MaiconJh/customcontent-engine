package com.customcontentengine.adapter.persistence;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class PdcBlockStore implements BlockStorePort {
    private final PdcBlockCodec codec;
    private final NamespacedKey storageKey;

    public PdcBlockStore(Plugin plugin, PdcBlockCodec codec) {
        this.codec = java.util.Objects.requireNonNull(codec, "codec");
        this.storageKey = new NamespacedKey(plugin, "custom_blocks");
    }

    @Override
    public Optional<Short> findNumericId(WorldPosition position) {
        return Optional.empty();
    }

    @Override
    public void put(WorldPosition position, short numericId) {
        // Real chunk PersistentDataContainer write is intentionally deferred beyond the foundation skeleton.
    }

    @Override
    public void remove(WorldPosition position) {
        // Real chunk PersistentDataContainer removal is intentionally deferred beyond the foundation skeleton.
    }

    public PdcBlockCodec codec() {
        return codec;
    }

    public NamespacedKey storageKey() {
        return storageKey;
    }
}
