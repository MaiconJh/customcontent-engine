package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

public final class BukkitWorldMutationAdapter implements WorldMutationPort {
    @Override
    public void setBlockMaterial(WorldPosition position, String materialBase) {
        Objects.requireNonNull(position, "position");
        if (materialBase == null || materialBase.isBlank()) {
            throw new IllegalArgumentException("materialBase must not be blank");
        }
        World world = Bukkit.getWorld(position.worldName());
        if (world == null) {
            throw new IllegalArgumentException("Unknown world: " + position.worldName());
        }
        Material material = Material.matchMaterial(materialBase.toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("Unknown block material: " + materialBase);
        }
        world.getBlockAt(position.x(), position.y(), position.z()).setType(material, false);
    }
}
