package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.internalapi.identity.WorldPosition;
import org.bukkit.Location;

/**
 * Utility that converts Bukkit {@link Location} instances to the pure
 * {@link WorldPosition} value object used by the domain and application layers.
 *
 * <p>This mapper is intentionally placed in the adapter layer because it depends
 * on Bukkit types. It centralises the conversion so adapters do not duplicate
 * the same coordinate extraction logic.</p>
 */
public final class BukkitLocationMapper {

    private BukkitLocationMapper() {
    }

    public static WorldPosition toWorldPosition(Location location) {
        return new WorldPosition(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}