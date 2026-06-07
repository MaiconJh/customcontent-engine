package com.customcontentengine.builtin.mechanic;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockMutation;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AreaBreakMechanic implements Mechanic {
    public static final MechanicId ID = new MechanicId("area_break");

    private static final MechanicDescriptor DESCRIPTOR = new MechanicDescriptor(ID, Set.of(
            Capability.BLOCK_QUERY,
            Capability.BLOCK_MUTATION,
            Capability.BUDGET_VIEW,
            Capability.COOLDOWN_VIEW,
            Capability.DROP_SINK,
            Capability.EXECUTION_ORIGIN
    ), false);

    public AreaBreakMechanic() {
    }

    @Override
    public MechanicDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public MechanicResult execute(MechanicContext context) {
        Objects.requireNonNull(context, "context");
        BlockQuery blockQuery;
        BlockMutation blockMutation;
        BudgetView budgetView;
        CooldownView cooldownView;
        DropSink dropSink;
        ExecutionOrigin executionOrigin;
        try {
            blockQuery = context.require(BlockQuery.class);
            blockMutation = context.require(BlockMutation.class);
            budgetView = context.require(BudgetView.class);
            cooldownView = context.require(CooldownView.class);
            dropSink = context.require(DropSink.class);
            executionOrigin = context.require(ExecutionOrigin.class);
        } catch (IllegalArgumentException exception) {
            return new MechanicResult.Rejected(exception.getMessage());
        }

        if (!cooldownView.canExecute()) {
            return new MechanicResult.Rejected("Cooldown rejected area_break");
        }

        WorldPosition origin = Objects.requireNonNull(executionOrigin.origin(), "origin");
        List<WorldPosition> positions = flatArea(origin);
        int affectedBlocks = 0;
        for (int index = 0; index < positions.size(); index++) {
            WorldPosition position = positions.get(index);
            Optional<Short> numericId = blockQuery.findCustomBlockNumericId(position);
            if (numericId.isEmpty()) {
                continue;
            }
            if (!budgetView.tryConsume(position)) {
                if (affectedBlocks == 0) {
                    return new MechanicResult.Rejected("Budget exhausted");
                }
                return new MechanicResult.Partial(affectedBlocks, positions.subList(index, positions.size()));
            }
            blockMutation.breakBlock(position);
            dropSink.dropFor(position, numericId.get());
            affectedBlocks++;
        }
        return new MechanicResult.Done(affectedBlocks);
    }

    private static List<WorldPosition> flatArea(WorldPosition origin) {
        List<WorldPosition> positions = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                positions.add(new WorldPosition(origin.worldName(), origin.x() + dx, origin.y(), origin.z() + dz));
            }
        }
        return positions;
    }
}
