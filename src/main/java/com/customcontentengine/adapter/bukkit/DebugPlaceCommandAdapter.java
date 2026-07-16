package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Dev tool that places a custom block at a world position for integration tests, without requiring
 * a connected player. It delegates to the same {@link BlockStorePort} and {@link WorldMutationPort}
 * used by the real placement flow, so the resulting world state is identical to a player placing
 * the block. Must not contain business logic.
 */
public final class DebugPlaceCommandAdapter implements CommandExecutor {
    private final DefinitionRegistry registry;
    private final BlockStorePort blockStore;
    private final WorldMutationPort worldMutation;
    private final FileConfiguration config;

    public DebugPlaceCommandAdapter(
            DefinitionRegistry registry,
            BlockStorePort blockStore,
            WorldMutationPort worldMutation,
            FileConfiguration config) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DebugCommandGate.isAllowed(sender, config)) {
            sender.sendMessage("You do not have permission to use this debug command.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("Usage: /debugplace <blockId> <x> <y> <z> [world]");
            return true;
        }
        String blockId = args[0];
        WorldPosition position = parsePosition(args, 1);
        if (position == null) {
            sender.sendMessage("Invalid coordinates for /debugplace.");
            return true;
        }

        return registry.findBlock(new CustomBlockId(blockId))
                .map(block -> {
                    try {
                        blockStore.put(position, block.numericId());
                        worldMutation.setBlockMaterial(position, block.materialBase());
                        sender.sendMessage(
                                "debugplace ok: " + blockId + " (" + block.numericId() + ") at " + position);
                        return true;
                    } catch (RuntimeException exception) {
                        sender.sendMessage("debugplace failed: " + exception.getMessage());
                        return true;
                    }
                })
                .orElseGet(() -> {
                    sender.sendMessage("debugplace failed: unknown custom block: " + blockId);
                    return true;
                });
    }

    static WorldPosition parsePosition(String[] args, int offset) {
        try {
            int x = Integer.parseInt(args[offset]);
            int y = Integer.parseInt(args[offset + 1]);
            int z = Integer.parseInt(args[offset + 2]);
            String world = args.length > offset + 3 ? args[offset + 3] : "world";
            return new WorldPosition(world, x, y, z);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
