package com.customcontentengine.application.mechanic;

import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.SchedulerPort;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class MechanicExecutor {
    private static final int DEFAULT_MAX_RESCHEDULES = 8;

    private final MechanicRegistry registry;
    private final MechanicContextFactory contextFactory;
    private final Function<WorldPosition, MechanicContextFactory> continuationContextFactory;
    private final SchedulerPort schedulerPort;
    private final int maxReschedules;

    public MechanicExecutor(MechanicRegistry registry, MechanicContextFactory contextFactory) {
        this(registry, contextFactory, null, 0);
    }

    public MechanicExecutor(
            MechanicRegistry registry,
            MechanicContextFactory contextFactory,
            SchedulerPort schedulerPort) {
        this(registry, contextFactory, schedulerPort, DEFAULT_MAX_RESCHEDULES);
    }

    public MechanicExecutor(
            MechanicRegistry registry,
            MechanicContextFactory contextFactory,
            SchedulerPort schedulerPort,
            int maxReschedules) {
        this(registry, contextFactory, schedulerPort, ignored -> contextFactory, maxReschedules);
    }

    public MechanicExecutor(
            MechanicRegistry registry,
            MechanicContextFactory contextFactory,
            SchedulerPort schedulerPort,
            Function<WorldPosition, MechanicContextFactory> continuationContextFactory,
            int maxReschedules) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.continuationContextFactory = Objects.requireNonNull(
                continuationContextFactory,
                "continuationContextFactory");
        if (maxReschedules < 0) {
            throw new IllegalArgumentException("maxReschedules must not be negative");
        }
        if (schedulerPort == null && maxReschedules > 0) {
            throw new IllegalArgumentException("maxReschedules requires schedulerPort");
        }
        this.schedulerPort = schedulerPort;
        this.maxReschedules = maxReschedules;
    }

    public MechanicResult execute(MechanicId id) {
        Objects.requireNonNull(id, "id");
        return registry.find(id)
                .map(mechanic -> execute(mechanic, contextFactory, 0, null))
                .orElseGet(() -> new MechanicResult.Rejected("Unknown mechanic: " + id));
    }

    private MechanicResult execute(
            Mechanic mechanic,
            MechanicContextFactory activeContextFactory,
            int reschedulesUsed,
            MechanicResult.Partial previousPartial) {
        try {
            MechanicContext context = activeContextFactory.createContext(mechanic.descriptor());
            MechanicResult result = Objects.requireNonNull(mechanic.execute(context), "mechanic result");
            if (result instanceof MechanicResult.Partial partial) {
                scheduleContinuation(mechanic, partial, reschedulesUsed, previousPartial);
            }
            return result;
        } catch (IllegalArgumentException exception) {
            return new MechanicResult.Rejected(exception.getMessage());
        }
    }

    private void scheduleContinuation(
            Mechanic mechanic,
            MechanicResult.Partial partial,
            int reschedulesUsed,
            MechanicResult.Partial previousPartial) {
        if (schedulerPort == null || partial.remaining().isEmpty()) {
            return;
        }
        if (reschedulesUsed >= maxReschedules || !madeProgress(previousPartial, partial)) {
            return;
        }

        WorldPosition anchor = partial.remaining().get(0);
        schedulerPort.runOnRegion(anchor, () -> execute(
                mechanic,
                Objects.requireNonNull(continuationContextFactory.apply(anchor), "continuation context factory"),
                reschedulesUsed + 1,
                partial));
    }

    private boolean madeProgress(MechanicResult.Partial previousPartial, MechanicResult.Partial currentPartial) {
        if (previousPartial == null) {
            return true;
        }
        if (currentPartial.affectedBlocks() > 0) {
            return true;
        }
        if (currentPartial.remaining().size() < previousPartial.remaining().size()) {
            return true;
        }
        return !Objects.equals(anchor(previousPartial.remaining()), anchor(currentPartial.remaining()));
    }

    private WorldPosition anchor(List<WorldPosition> remaining) {
        return remaining.isEmpty() ? null : remaining.get(0);
    }
}
