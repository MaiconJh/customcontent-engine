package com.customcontentengine.builtin.mechanic;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockPlacement;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.internalapi.mechanic.capability.MechanicConfig;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BlockTransformMechanic implements Mechanic {
    public static final MechanicId ID = new MechanicId("block_transform");
    private static final MechanicDescriptor DESCRIPTOR = new MechanicDescriptor(ID, Set.of(
            Capability.BLOCK_QUERY,
            Capability.BLOCK_MUTATION,
            Capability.BLOCK_PLACEMENT,
            Capability.BUDGET_VIEW,
            Capability.COOLDOWN_VIEW,
            Capability.DROP_SINK,
            Capability.EXECUTION_ORIGIN,
            Capability.MECHANIC_CONFIG
    ), false);

    @Override
    public MechanicDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public MechanicResult execute(MechanicContext context) {
        Objects.requireNonNull(context, "context");
        BlockQuery blockQuery;
        BlockMutation blockMutation;
        BlockPlacement blockPlacement;
        MechanicConfig config;
        BudgetView budgetView;
        CooldownView cooldownView;
        DropSink dropSink;
        ExecutionOrigin executionOrigin;
        try {
            blockQuery = context.require(BlockQuery.class);
            blockMutation = context.require(BlockMutation.class);
            blockPlacement = context.require(BlockPlacement.class);
            config = context.require(MechanicConfig.class);
            budgetView = context.require(BudgetView.class);
            cooldownView = context.require(CooldownView.class);
            dropSink = context.require(DropSink.class);
            executionOrigin = context.require(ExecutionOrigin.class);
        } catch (IllegalArgumentException exception) {
            return new MechanicResult.Rejected(exception.getMessage());
        }

        if (!cooldownView.canExecute()) {
            return new MechanicResult.Rejected("Cooldown rejected block_transform");
        }

        String toBlock = config.getString("to_block").orElse(null);
        if (toBlock == null) {
            return new MechanicResult.Rejected("Missing required argument: to_block");
        }

        boolean dropOriginal = config.getBoolean("drop_original").orElse(false);
        boolean consumeBudget = config.getBoolean("consume_budget").orElse(true);

        WorldPosition origin = Objects.requireNonNull(executionOrigin.origin(), "origin");
        Optional<Short> currentNumericId = blockQuery.findCustomBlockNumericId(origin);
        if (currentNumericId.isEmpty()) {
            return new MechanicResult.Done(0);
        }

        if (consumeBudget && !budgetView.tryConsume(origin)) {
            return new MechanicResult.Rejected("Budget exhausted");
        }

        short oldNumericId = currentNumericId.get();
        blockMutation.breakBlock(origin);

        if (toBlock.matches("\\d+")) {
            blockPlacement.placeBlock(origin, Short.parseShort(toBlock));
        } else {
            blockPlacement.placeMaterial(origin, toBlock);
        }

        if (dropOriginal) {
            dropSink.dropFor(origin, oldNumericId);
        }

        return new MechanicResult.Done(1);
    }
}