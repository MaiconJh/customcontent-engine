package com.customcontentengine.application.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.BlockQuery;
import com.customcontentengine.internalapi.mechanic.capability.BudgetView;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.port.SchedulerPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MechanicInfrastructureTest {
    @Test
    void registryRegistersAndFindsMechanic() {
        FakeMechanic mechanic = fakeMechanic("sample", Set.of(), new MechanicResult.Done(1));
        MechanicRegistry registry = new MechanicRegistry();

        registry.register(mechanic);

        assertSame(mechanic, registry.find(new MechanicId("sample")).orElseThrow());
    }

    @Test
    void registryRejectsDuplicateIds() {
        MechanicRegistry registry = new MechanicRegistry();
        registry.register(fakeMechanic("sample", Set.of(), new MechanicResult.Done(1)));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(fakeMechanic("sample", Set.of(), new MechanicResult.Done(2))));
    }

    @Test
    void registryExposesImmutableMechanicsCollection() {
        MechanicRegistry registry = new MechanicRegistry(List.of(
                fakeMechanic("sample", Set.of(), new MechanicResult.Done(1))
        ));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.mechanics().add(fakeMechanic("other", Set.of(), new MechanicResult.Done(1))));
    }

    @Test
    void contextReturnsAllowedCapability() {
        FakeBlockQuery blockQuery = new FakeBlockQuery();
        MechanicContext context = new MechanicContextFactory(Map.of(BlockQuery.class, blockQuery))
                .createContext(descriptor("sample", Set.of(Capability.BLOCK_QUERY)));

        assertSame(blockQuery, context.require(BlockQuery.class));
    }

    @Test
    void contextRejectsCapabilityAbsentFromCreatedContext() {
        FakeBlockQuery blockQuery = new FakeBlockQuery();
        MechanicContext context = new MechanicContextFactory(Map.of(BlockQuery.class, blockQuery))
                .createContext(descriptor("sample", Set.of(Capability.BLOCK_QUERY)));

        assertThrows(IllegalArgumentException.class, () -> context.require(DropSink.class));
    }

    @Test
    void factoryCreatesContextWhenCapabilitiesExist() {
        FakeBudgetView budgetView = new FakeBudgetView();
        MechanicContext context = new MechanicContextFactory(Map.of(BudgetView.class, budgetView))
                .createContext(descriptor("sample", Set.of(Capability.BUDGET_VIEW)));

        assertSame(budgetView, context.require(BudgetView.class));
    }

    @Test
    void factoryRejectsMissingCapability() {
        MechanicContextFactory factory = new MechanicContextFactory(Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> factory.createContext(descriptor("sample", Set.of(Capability.BLOCK_QUERY))));
    }

    @Test
    void executorRunsRegisteredMechanic() {
        FakeBlockQuery blockQuery = new FakeBlockQuery();
        FakeMechanic mechanic = fakeMechanic("sample", Set.of(Capability.BLOCK_QUERY), new MechanicResult.Done(7));
        MechanicExecutor executor = executorWith(mechanic, Map.of(BlockQuery.class, blockQuery));

        MechanicResult result = executor.execute(new MechanicId("sample"));

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(7, done.affectedBlocks());
        assertSame(blockQuery, mechanic.seenContext.require(BlockQuery.class));
    }

    @Test
    void executorRejectsUnknownMechanic() {
        MechanicExecutor executor = new MechanicExecutor(new MechanicRegistry(), new MechanicContextFactory());

        MechanicResult result = executor.execute(new MechanicId("missing"));

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Unknown mechanic: missing", rejected.reason());
    }

    @Test
    void executorRejectsMissingCapability() {
        FakeMechanic mechanic = fakeMechanic("sample", Set.of(Capability.BLOCK_QUERY), new MechanicResult.Done(1));
        MechanicExecutor executor = executorWith(mechanic, Map.of());

        MechanicResult result = executor.execute(new MechanicId("sample"));

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Capability is not available: BLOCK_QUERY", rejected.reason());
    }

    @Test
    void executorPropagatesStructuredResultsFromFakeMechanics() {
        MechanicExecutor doneExecutor = executorWith(fakeMechanic("done", Set.of(), new MechanicResult.Done(1)), Map.of());
        MechanicExecutor partialExecutor = executorWith(fakeMechanic("partial", Set.of(),
                new MechanicResult.Partial(2, List.of(new WorldPosition("world", 1, 64, 1)))), Map.of());
        MechanicExecutor rejectedExecutor = executorWith(fakeMechanic("rejected", Set.of(),
                new MechanicResult.Rejected("not now")), Map.of());

        assertInstanceOf(MechanicResult.Done.class, doneExecutor.execute(new MechanicId("done")));
        assertInstanceOf(MechanicResult.Partial.class, partialExecutor.execute(new MechanicId("partial")));
        assertInstanceOf(MechanicResult.Rejected.class, rejectedExecutor.execute(new MechanicId("rejected")));
    }

    @Test
    void doneDoesNotScheduleContinuation() {
        CapturingScheduler scheduler = new CapturingScheduler();
        MechanicExecutor executor = executorWith(
                fakeMechanic("done", Set.of(), new MechanicResult.Done(1)),
                Map.of(),
                scheduler,
                8);

        assertInstanceOf(MechanicResult.Done.class, executor.execute(new MechanicId("done")));

        assertEquals(0, scheduler.anchors.size());
    }

    @Test
    void rejectedDoesNotScheduleContinuation() {
        CapturingScheduler scheduler = new CapturingScheduler();
        MechanicExecutor executor = executorWith(
                fakeMechanic("rejected", Set.of(), new MechanicResult.Rejected("not now")),
                Map.of(),
                scheduler,
                8);

        assertInstanceOf(MechanicResult.Rejected.class, executor.execute(new MechanicId("rejected")));

        assertEquals(0, scheduler.anchors.size());
    }

    @Test
    void partialWithRemainingSchedulesContinuationOnFirstRemainingPosition() {
        WorldPosition anchor = new WorldPosition("world", 5, 64, 5);
        CapturingScheduler scheduler = new CapturingScheduler();
        MechanicExecutor executor = executorWith(
                fakeMechanic("partial", Set.of(), new MechanicResult.Partial(1, List.of(anchor))),
                Map.of(),
                scheduler,
                8);

        assertInstanceOf(MechanicResult.Partial.class, executor.execute(new MechanicId("partial")));

        assertEquals(List.of(anchor), scheduler.anchors);
        assertEquals(1, scheduler.tasks.size());
    }

    @Test
    void continuationContextUsesFirstRemainingPositionAsOrigin() {
        WorldPosition initialOrigin = new WorldPosition("world", 1, 64, 1);
        WorldPosition continuationOrigin = new WorldPosition("world", 5, 64, 5);
        ImmediateScheduler scheduler = new ImmediateScheduler();
        OriginRecordingMechanic mechanic = new OriginRecordingMechanic(continuationOrigin);
        MechanicExecutor executor = new MechanicExecutor(
                new MechanicRegistry(List.of(mechanic)),
                new MechanicContextFactory(Map.of(ExecutionOrigin.class, new StaticOrigin(initialOrigin))),
                scheduler,
                anchor -> new MechanicContextFactory(Map.of(ExecutionOrigin.class, new StaticOrigin(anchor))),
                8);

        assertInstanceOf(MechanicResult.Partial.class, executor.execute(new MechanicId("origin_sample")));

        assertEquals(List.of(initialOrigin, continuationOrigin), mechanic.seenOrigins);
        assertEquals(List.of(continuationOrigin), scheduler.anchors);
    }


    @Test
    void partialWithoutRemainingDoesNotScheduleContinuation() {
        CapturingScheduler scheduler = new CapturingScheduler();
        MechanicExecutor executor = executorWith(
                fakeMechanic("partial", Set.of(), new MechanicResult.Partial(1, List.of())),
                Map.of(),
                scheduler,
                8);

        assertInstanceOf(MechanicResult.Partial.class, executor.execute(new MechanicId("partial")));

        assertEquals(0, scheduler.anchors.size());
    }

    @Test
    void maxRescheduleLimitPreventsInfiniteLoop() {
        WorldPosition anchor = new WorldPosition("world", 5, 64, 5);
        ImmediateScheduler scheduler = new ImmediateScheduler();
        MechanicExecutor executor = executorWith(
                fakeMechanic("partial", Set.of(), new MechanicResult.Partial(1, List.of(anchor))),
                Map.of(),
                scheduler,
                2);

        assertInstanceOf(MechanicResult.Partial.class, executor.execute(new MechanicId("partial")));

        assertEquals(2, scheduler.anchors.size());
    }

    @Test
    void partialWithoutProgressDoesNotScheduleForever() {
        WorldPosition anchor = new WorldPosition("world", 5, 64, 5);
        ImmediateScheduler scheduler = new ImmediateScheduler();
        MechanicExecutor executor = executorWith(
                fakeMechanic("partial", Set.of(), new MechanicResult.Partial(0, List.of(anchor))),
                Map.of(),
                scheduler,
                8);

        assertInstanceOf(MechanicResult.Partial.class, executor.execute(new MechanicId("partial")));

        assertEquals(1, scheduler.anchors.size());
    }

    @Test
    void mechanicDoesNotReceiveSchedulerPortInContext() {
        SchedulerAwareFakeMechanic mechanic = new SchedulerAwareFakeMechanic();
        CapturingScheduler scheduler = new CapturingScheduler();
        MechanicExecutor executor = executorWith(mechanic, Map.of(), scheduler, 8);

        assertInstanceOf(MechanicResult.Done.class, executor.execute(new MechanicId("sample")));

        assertInstanceOf(IllegalArgumentException.class, mechanic.schedulerAccessFailure);
    }

    private static MechanicExecutor executorWith(Mechanic mechanic, Map<Class<?>, ?> capabilities) {
        return new MechanicExecutor(new MechanicRegistry(List.of(mechanic)), new MechanicContextFactory(capabilities));
    }

    private static MechanicExecutor executorWith(
            Mechanic mechanic,
            Map<Class<?>, ?> capabilities,
            SchedulerPort schedulerPort,
            int maxReschedules) {
        return new MechanicExecutor(
                new MechanicRegistry(List.of(mechanic)),
                new MechanicContextFactory(capabilities),
                schedulerPort,
                maxReschedules);
    }

    private static FakeMechanic fakeMechanic(String id, Set<Capability> capabilities, MechanicResult result) {
        return new FakeMechanic(descriptor(id, capabilities), result);
    }

    private static MechanicDescriptor descriptor(String id, Set<Capability> capabilities) {
        return new MechanicDescriptor(new MechanicId(id), capabilities, false);
    }

    private record FakeBlockQuery() implements BlockQuery {
        @Override
        public Optional<Short> findCustomBlockNumericId(WorldPosition position) {
            return Optional.of((short) 1);
        }
    }

    private record FakeBudgetView() implements BudgetView {
        @Override
        public boolean tryConsume(WorldPosition position) {
            return true;
        }
    }

    private static final class FakeMechanic implements Mechanic {
        private final MechanicDescriptor descriptor;
        private final MechanicResult result;
        private MechanicContext seenContext;

        private FakeMechanic(MechanicDescriptor descriptor, MechanicResult result) {
            this.descriptor = descriptor;
            this.result = result;
        }

        @Override
        public MechanicDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public MechanicResult execute(MechanicContext context) {
            this.seenContext = context;
            return result;
        }
    }

    private static final class SchedulerAwareFakeMechanic implements Mechanic {
        private RuntimeException schedulerAccessFailure;

        @Override
        public MechanicDescriptor descriptor() {
            return MechanicInfrastructureTest.descriptor("sample", Set.of());
        }

        @Override
        public MechanicResult execute(MechanicContext context) {
            try {
                context.require(SchedulerPort.class);
            } catch (RuntimeException exception) {
                schedulerAccessFailure = exception;
            }
            return new MechanicResult.Done(1);
        }
    }

    private record StaticOrigin(WorldPosition origin) implements ExecutionOrigin {
    }

    private static final class OriginRecordingMechanic implements Mechanic {
        private final WorldPosition continuationOrigin;
        private final List<WorldPosition> seenOrigins = new ArrayList<>();

        private OriginRecordingMechanic(WorldPosition continuationOrigin) {
            this.continuationOrigin = continuationOrigin;
        }

        @Override
        public MechanicDescriptor descriptor() {
            return MechanicInfrastructureTest.descriptor("origin_sample", Set.of(Capability.EXECUTION_ORIGIN));
        }

        @Override
        public MechanicResult execute(MechanicContext context) {
            seenOrigins.add(context.require(ExecutionOrigin.class).origin());
            if (seenOrigins.size() == 1) {
                return new MechanicResult.Partial(1, List.of(continuationOrigin));
            }
            return new MechanicResult.Done(1);
        }
    }

    private static class CapturingScheduler implements SchedulerPort {
        protected final List<WorldPosition> anchors = new ArrayList<>();
        protected final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
            anchors.add(position);
            tasks.add(task);
        }
    }

    private static final class ImmediateScheduler extends CapturingScheduler {
        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
            super.runOnRegion(position, task);
            task.run();
        }
    }
}
