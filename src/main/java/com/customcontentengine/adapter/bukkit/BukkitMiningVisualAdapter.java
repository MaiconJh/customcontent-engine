package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.domain.mining.MiningStage;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.MiningVisualPort;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BukkitMiningVisualAdapter implements MiningVisualPort {
    private static final float CLEAR_PROGRESS = 0.0F;

    private final Plugin plugin;
    private final Map<String, WorldPosition> lastPositions = new ConcurrentHashMap<>();

    public BukkitMiningVisualAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void updateMiningStage(String actorKey, WorldPosition position, MiningStage stage) {
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(stage, "stage");

        Optional<Player> player = player(actorKey);
        Optional<Location> location = location(position);
        if (player.isEmpty() || location.isEmpty()) {
            return;
        }

        lastPositions.put(actorKey, position);
        player.get().sendBlockDamage(location.get(), progressFor(stage));
    }

    @Override
    public void clearMiningVisual(String actorKey) {
        Objects.requireNonNull(actorKey, "actorKey");

        WorldPosition position = lastPositions.remove(actorKey);
        if (position == null) {
            return;
        }

        Optional<Player> player = player(actorKey);
        Optional<Location> location = location(position);
        if (player.isEmpty() || location.isEmpty()) {
            return;
        }

        player.get().sendBlockDamage(location.get(), CLEAR_PROGRESS);
    }

    private Optional<Player> player(String actorKey) {
        try {
            return Optional.ofNullable(server().getPlayer(UUID.fromString(actorKey)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Location> location(WorldPosition position) {
        World world = server().getWorld(position.worldName());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, position.x(), position.y(), position.z()));
    }

    private Server server() {
        return plugin.getServer();
    }

    private float progressFor(MiningStage stage) {
        return Math.min(1.0F, (stage.value() + 1) / 10.0F);
    }
}
