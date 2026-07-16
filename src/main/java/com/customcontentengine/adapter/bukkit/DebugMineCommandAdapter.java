package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.mining.MiningSessionService;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Dev tool that starts a custom mining session for integration tests, simulating the player action
 * handled by {@link MiningInputAdapter} without a connected client. It resolves the target block
 * and held tool through the same services and delegates to {@link MiningSessionService#startSession}
 * so the periodic driver processes and completes the session exactly as in production.
 */
public final class DebugMineCommandAdapter implements CommandExecutor {
    public static final String DEBUG_ACTOR_KEY = "debug-mine";

    private final DefinitionRegistry registry;
    private final BlockStorePort blockStore;
    private final MiningSessionService miningSessionService;
    private final FileConfiguration config;

    public DebugMineCommandAdapter(
            DefinitionRegistry registry,
            BlockStorePort blockStore,
            MiningSessionService miningSessionService,
            FileConfiguration config) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.miningSessionService = Objects.requireNonNull(miningSessionService, "miningSessionService");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DebugCommandGate.isAllowed(sender, config)) {
            sender.sendMessage("You do not have permission to use this debug command.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("Usage: /debugmine <toolId> <x> <y> <z> [world]");
            return true;
        }
        String toolId = args[0];
        WorldPosition position = DebugPlaceCommandAdapter.parsePosition(args, 1);
        if (position == null) {
            sender.sendMessage("Invalid coordinates for /debugmine.");
            return true;
        }

        Optional<Short> numericId = blockStore.findNumericId(position);
        if (numericId.isEmpty()) {
            sender.sendMessage("debugmine failed: no custom block at " + position);
            return true;
        }
        Optional<BlockDef> block = registry.findBlockByNumericId(numericId.get());
        if (block.isEmpty() || block.get().miningHardness().isEmpty()) {
            sender.sendMessage("debugmine failed: block is not minable at " + position);
            return true;
        }
        Optional<ItemDef> tool = registry.findItem(new CustomItemId(toolId));
        if (tool.isEmpty() || tool.get().miningSpeed().isEmpty()) {
            sender.sendMessage("debugmine failed: unknown or non-mining tool: " + toolId);
            return true;
        }
        if (!miningSessionService.isTierEligible(tool.get(), block.get())) {
            sender.sendMessage("debugmine rejected: tool tier cannot mine this block.");
            return true;
        }

        miningSessionService.startSession(
                DEBUG_ACTOR_KEY,
                position,
                new CustomItemId(toolId),
                block.get().miningHardness().get(),
                tool.get().miningSpeed().get(),
                System.currentTimeMillis());
        sender.sendMessage("debugmine started: " + toolId + " -> " + position);
        return true;
    }
}
