package com.customcontentengine.application.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.builtin.mechanic.AreaBreakMechanic;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.CooldownView;
import com.customcontentengine.internalapi.mechanic.capability.ExecutionOrigin;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AreaBreakRuntimeServiceTest {
    private static final WorldPosition ORIGIN = new WorldPosition("world", 10, 64, 10);

    @Test
    void composesRuntimeCapabilitiesAndExecutesAreaBreak() {
        FakeBlockStore blockStore = new FakeBlockStore();
        flatArea(ORIGIN).forEach(position -> blockStore.blocks.put(position, (short) 7));
        CapturingDropPort dropPort = new CapturingDropPort();
        FakeWorldMutation worldMutation = new FakeWorldMutation();
        AreaBreakRuntimeService service = service(blockStore, dropPort, worldMutation);

        MechanicResult result = service.execute(ORIGIN, "player-one");

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(9, done.affectedBlocks());
        assertEquals(9, blockStore.removed.size());
        assertEquals(9, worldMutation.mutations.size());
        assertEquals(9, dropPort.drops.size());
    }

    @Test
    void additionalAreaExecutionExcludesOriginFromMutationAndDrops() {
        FakeBlockStore blockStore = new FakeBlockStore();
        flatArea(ORIGIN).forEach(position -> blockStore.blocks.put(position, (short) 7));
        CapturingDropPort dropPort = new CapturingDropPort();
        FakeWorldMutation worldMutation = new FakeWorldMutation();
        AreaBreakRuntimeService service = service(blockStore, dropPort, worldMutation);

        MechanicResult result = service.executeAdditionalArea(ORIGIN, "player-one");

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result);
        assertEquals(8, done.affectedBlocks());
        assertEquals(8, blockStore.removed.size());
        assertFalse(blockStore.removed.contains(ORIGIN));
        assertEquals(8, worldMutation.positions.size());
        assertFalse(worldMutation.positions.contains(ORIGIN));
        assertEquals(8, dropPort.positions.size());
        assertFalse(dropPort.positions.contains(ORIGIN));
        assertEquals(Optional.of((short) 7), blockStore.findNumericId(ORIGIN));
    }

    @Test
    void unsafePositionIsNotMutatedAndRemainsPartial() {
        WorldPosition unsafePosition = new WorldPosition("world", 11, 64, 11);
        FakeBlockStore blockStore = new FakeBlockStore();
        flatArea(ORIGIN).forEach(position -> blockStore.blocks.put(position, (short) 7));
        CapturingDropPort dropPort = new CapturingDropPort();
        FakeWorldMutation worldMutation = new FakeWorldMutation();
        AreaBreakRuntimeService service = service(
                blockStore,
                dropPort,
                worldMutation,
                position -> !position.equals(unsafePosition));

        MechanicResult result = service.executeAdditionalArea(ORIGIN, "player-one");

        MechanicResult.Partial partial = assertInstanceOf(MechanicResult.Partial.class, result);
        assertEquals(7, partial.affectedBlocks());
        assertEquals(List.of(unsafePosition), partial.remaining());
        assertFalse(blockStore.removed.contains(unsafePosition));
        assertFalse(worldMutation.positions.contains(unsafePosition));
        assertFalse(dropPort.positions.contains(unsafePosition));
        assertEquals(Optional.of((short) 7), blockStore.findNumericId(unsafePosition));
    }

    @Test
    void usesCooldownKeyForRepeatedControlledExecution() {
        FakeBlockStore blockStore = new FakeBlockStore();
        flatArea(ORIGIN).forEach(position -> blockStore.blocks.put(position, (short) 7));
        AreaBreakRuntimeService service = service(blockStore, new CapturingDropPort(), new FakeWorldMutation());

        assertInstanceOf(MechanicResult.Done.class, service.execute(ORIGIN, "player-one"));
        MechanicResult result = service.execute(ORIGIN, "player-one");

        MechanicResult.Rejected rejected = assertInstanceOf(MechanicResult.Rejected.class, result);
        assertEquals("Cooldown rejected area_break", rejected.reason());
    }

    @Test
    void partialContinuationIsNotRejectedByInitialCooldown() {
        WorldPosition continuationOrigin = new WorldPosition("world", 11, 64, 10);
        CooldownRecordingMechanic mechanic = new CooldownRecordingMechanic(continuationOrigin);
        AreaBreakRuntimeService service = service(
                new FakeBlockStore(),
                new CapturingDropPort(),
                new FakeWorldMutation(),
                new ImmediateScheduler(),
                new MechanicRegistry(List.of(mechanic)),
                CooldownRecordingMechanic.ID);

        assertInstanceOf(MechanicResult.Partial.class, service.execute(ORIGIN, "player-one"));

        assertEquals(List.of(true, true), mechanic.cooldownAllowed);
        assertEquals(List.of(ORIGIN, continuationOrigin), mechanic.seenOrigins);
    }

    private static AreaBreakRuntimeService service(
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation) {
        return service(blockStore, dropPort, worldMutation, position -> true);
    }

    private static AreaBreakRuntimeService service(
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            RegionSafetyPort regionSafety) {
        return service(
                blockStore,
                dropPort,
                worldMutation,
                new CapturingScheduler(),
                new MechanicRegistry(List.of(new AreaBreakMechanic())),
                AreaBreakMechanic.ID,
                regionSafety);
    }

    private static AreaBreakRuntimeService service(
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            SchedulerPort scheduler,
            MechanicRegistry mechanicRegistry,
            MechanicId mechanicId) {
        return service(blockStore, dropPort, worldMutation, scheduler, mechanicRegistry, mechanicId, position -> true);
    }

    private static AreaBreakRuntimeService service(
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            SchedulerPort scheduler,
            MechanicRegistry mechanicRegistry,
            MechanicId mechanicId,
            RegionSafetyPort regionSafety) {
        DefinitionRegistry definitions = new DefinitionRegistry(List.of(block("ruby_ore", (short) 7)), List.of());
        return new AreaBreakRuntimeService(
                mechanicRegistry,
                mechanicId,
                definitions,
                blockStore,
                dropPort,
                worldMutation,
                new InMemoryCooldowns(),
                scheduler,
                regionSafety);
    }

    private static List<WorldPosition> flatArea(WorldPosition origin) {
        List<WorldPosition> positions = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                positions.add(new WorldPosition(origin.worldName(), origin.x() + dx, origin.y(), origin.z() + dz));
            }
        }
        return positions;
    }

    private static BlockDef block(String id, short numericId) {
        return new BlockDef(
                new CustomBlockId(id),
                numericId,
                "NOTE_BLOCK",
                1001,
                "ruby_pickaxe",
                new DropTable(List.of(new DropTable.Entry("ruby", 1))));
    }

    private static final class FakeBlockStore implements BlockStorePort {
        private final Map<WorldPosition, Short> blocks = new HashMap<>();
        private final List<WorldPosition> removed = new ArrayList<>();

        @Override
        public Optional<Short> findNumericId(WorldPosition position) {
            return Optional.ofNullable(blocks.get(position));
        }

        @Override
        public void put(WorldPosition position, short numericId) {
            blocks.put(position, numericId);
        }

        @Override
        public void remove(WorldPosition position) {
            removed.add(position);
            blocks.remove(position);
        }
    }

    private static final class FakeWorldMutation implements WorldMutationPort {
        private final List<WorldPosition> positions = new ArrayList<>();
        private final List<String> mutations = new ArrayList<>();

        @Override
        public void setBlockMaterial(WorldPosition position, String materialBase) {
            positions.add(position);
            mutations.add(materialBase + "@" + position);
        }
    }

    private static final class CapturingDropPort implements DropPort {
        private final List<WorldPosition> positions = new ArrayList<>();
        private final List<DropTable> drops = new ArrayList<>();

        @Override
        public void drop(WorldPosition position, DropTable drops) {
            positions.add(position);
            this.drops.add(drops);
        }
    }

    private static final class CapturingScheduler implements SchedulerPort {
        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
        }
    }

    private static final class ImmediateScheduler implements SchedulerPort {
        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
            task.run();
        }
    }

    private static final class CooldownRecordingMechanic implements Mechanic {
        private static final MechanicId ID = new MechanicId("area_break");

        private final WorldPosition continuationOrigin;
        private final List<Boolean> cooldownAllowed = new ArrayList<>();
        private final List<WorldPosition> seenOrigins = new ArrayList<>();

        private CooldownRecordingMechanic(WorldPosition continuationOrigin) {
            this.continuationOrigin = continuationOrigin;
        }

        @Override
        public MechanicDescriptor descriptor() {
            return new MechanicDescriptor(
                    ID,
                    java.util.Set.of(Capability.COOLDOWN_VIEW, Capability.EXECUTION_ORIGIN),
                    false);
        }

        @Override
        public MechanicResult execute(MechanicContext context) {
            boolean allowed = context.require(CooldownView.class).canExecute();
            cooldownAllowed.add(allowed);
            seenOrigins.add(context.require(ExecutionOrigin.class).origin());
            if (!allowed) {
                return new MechanicResult.Rejected("cooldown");
            }
            if (cooldownAllowed.size() == 1) {
                return new MechanicResult.Partial(1, List.of(continuationOrigin));
            }
            return new MechanicResult.Done(1);
        }
    }
}
