package com.customcontentengine.builtin.mechanic;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockPlacement;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.internalapi.mechanic.capability.MechanicConfig;
import java.util.Objects;
import java.util.Set;

public final class BlockTransformMechanic implements Mechanic {
    public static final MechanicId ID = new MechanicId("block_transform");
    private static final MechanicDescriptor DESCRIPTOR = new MechanicDescriptor(ID, Set.of(
            Capability.BLOCK_PLACEMENT,
            Capability.BUDGET_VIEW,
            Capability.COOLDOWN_VIEW,
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
        BlockPlacement blockPlacement;
        MechanicConfig config;
        BudgetView budgetView;
        CooldownView cooldownView;
        ExecutionOrigin executionOrigin;
        try {
            blockPlacement = context.require(BlockPlacement.class);
            config = context.require(MechanicConfig.class);
            budgetView = context.require(BudgetView.class);
            cooldownView = context.require(CooldownView.class);
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

        boolean consumeBudget = config.getBoolean("consume_budget").orElse(true);

        WorldPosition origin = Objects.requireNonNull(executionOrigin.origin(), "origin");

        if (consumeBudget && !budgetView.tryConsume(origin)) {
            return new MechanicResult.Rejected("Budget exhausted");
        }

        if (toBlock.matches("\\d+")) {
            blockPlacement.placeBlock(origin, Short.parseShort(toBlock));
        } else {
            blockPlacement.placeMaterial(origin, toBlock);
        }

        return new MechanicResult.Done(1);
    }
}