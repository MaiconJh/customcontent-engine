package com.customcontentengine.adapter.bukkit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.domain.mining.MiningStage;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class BukkitMiningVisualAdapterTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACTOR_KEY = PLAYER_ID.toString();
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 12);

    @Test
    void updateMiningStageSendsBlockDamageToPlayer() {
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        BukkitMiningVisualAdapter adapter = adapter(server, world, player);

        adapter.updateMiningStage(ACTOR_KEY, TARGET, new MiningStage(3));

        verify(player).sendBlockDamage(any(Location.class), eq(0.4F));
    }

    @Test
    void clearMiningVisualSendsZeroProgressForLastPosition() {
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        BukkitMiningVisualAdapter adapter = adapter(server, world, player);

        adapter.updateMiningStage(ACTOR_KEY, TARGET, new MiningStage(3));
        adapter.clearMiningVisual(ACTOR_KEY);

        verify(player).sendBlockDamage(any(Location.class), eq(0.0F));
    }

    private static BukkitMiningVisualAdapter adapter(Server server, World world, Player player) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(PLAYER_ID)).thenReturn(player);
        when(server.getWorld(TARGET.worldName())).thenReturn(world);
        return new BukkitMiningVisualAdapter(plugin);
    }
}
