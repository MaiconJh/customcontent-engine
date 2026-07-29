package com.customcontentengine.application.mining;

import com.customcontentengine.application.mechanic.MechanicEventTriggerService;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.MiningCompletionPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.ToolWearPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CustomMiningCompletionService implements MiningCompletionPort {
    private final DefinitionRegistry definitions;
    private final BlockStorePort blockStore;
    private final WorldMutationPort worldMutation;
    private final DropPort dropPort;
    private final RegionSafetyPort regionSafety;
    private final MechanicEventTriggerService mechanicTriggerService;
    private final ToolWearPort toolWearPort;

    public CustomMiningCompletionService(
            DefinitionRegistry definitions,
            BlockStorePort blockStore,
            WorldMutationPort worldMutation,
            DropPort dropPort,
            RegionSafetyPort regionSafety,
            MechanicEventTriggerService mechanicTriggerService,
            ToolWearPort toolWearPort) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.blockStore = Objects.requireNonNull(blockStore, "blockStore");
        this.worldMutation = Objects.requireNonNull(worldMutation, "worldMutation");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
        this.regionSafety = Objects.requireNonNull(regionSafety, "regionSafety");
        this.mechanicTriggerService = Objects.requireNonNull(mechanicTriggerService, "mechanicTriggerService");
        this.toolWearPort = toolWearPort;
    }

    @Override
    public CompletionResult complete(CompletionRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            Optional<Short> numericId = blockStore.findNumericId(request.position());
            if (numericId.isEmpty()) {
                return new CompletionResult(
                        CompletionStatus.POSITION_NOT_CUSTOM_BLOCK,
                        "Position is not a custom block.");
            }

            Optional<BlockDef> block = definitions.findBlockByNumericId(numericId.get());
            if (block.isEmpty()) {
                return new CompletionResult(
                        CompletionStatus.POSITION_NOT_CUSTOM_BLOCK,
                        "Stored custom block id is not registered.");
            }

            if (!block.get().requiredTool().equals(request.toolId().value())) {
                return new CompletionResult(
                        CompletionStatus.TOOL_MISMATCH,
                        "Mining tool does not match block required tool.");
            }

            if (!regionSafety.canAccess(request.position())) {
                return new CompletionResult(
                        CompletionStatus.REGION_UNSAFE,
                        "Region is not safe for mining completion.");
            }

            short originNumericId = numericId.get();
            blockStore.remove(request.position());
            worldMutation.setBlockMaterial(request.position(), "AIR");
            dropPort.drop(request.position(), block.get().drops());
            applyToolWear(request.actorKey(), request.toolId());
            Map<String, Object> extraArgs = Map.of("origin_numeric_id", originNumericId);
            mechanicTriggerService.trigger(request.toolId(), request.position(), request.actorKey(), extraArgs);
            return new CompletionResult(CompletionStatus.SUCCESS, "Custom mining completed.");
        } catch (RuntimeException exception) {
            return new CompletionResult(
                    CompletionStatus.FAILED,
                    "Could not complete custom mining: " + exception.getMessage());
        }
    }

    private void applyToolWear(String actorKey, CustomItemId toolId) {
        if (toolWearPort != null) {
            toolWearPort.applyWearIfNeeded(actorKey, toolId);
        }
    }
}
