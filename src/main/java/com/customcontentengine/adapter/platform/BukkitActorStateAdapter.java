package com.customcontentengine.adapter.platform;

import com.customcontentengine.internalapi.mechanic.capability.ActorState;
import java.util.Objects;
import org.bukkit.entity.Player;

/**
 * Paper/Bukkit implementation of {@link ActorState} that reads the sneaking flag
 * from a {@link Player}. Confined to the adapter layer so the mechanic never
 * depends on Bukkit types.
 */
public final class BukkitActorStateAdapter implements ActorState {
    private final Player player;

    public BukkitActorStateAdapter(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public boolean isSneaking() {
        return player.isSneaking();
    }
}
