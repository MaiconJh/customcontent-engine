package com.customcontentengine.application.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.builtin.mechanic.AreaBreakMechanic;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.Capability;
import com.customcontentengine.internalapi.mechanic.Mechanic;
import com.customcontentengine.internalapi.mechanic.MechanicContext;
import com.customcontentengine.internalapi.mechanic.MechanicDescriptor;
import com.customcontentengine.internalapi.mechanic.MechanicId;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AreaBreakEventTriggerServiceTest {
    private static final WorldPosition ORIGIN = new WorldPosition("world", 10, 64, 10);

    @Test
    void bindingValidatorRejectsUnknownMechanic() {
        MechanicBindingRegistry bindings = bindings(new CustomItemId("ruby"), new MechanicId("missing"));
        MechanicBindingValidator validator = new MechanicBindingValidator(new MechanicRegistry(List.of(new AreaBreakMechanic())));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> validator.validate(bindings));

        assertTrue(exception.getMessage().contains("references unknown mechanic: missing"));
    }

    @Test
    void bindingValidatorRejectsMechanicNotAllowedInThisPhase() {
        MechanicId otherId = new MechanicId("other_mechanic");
        MechanicRegistry registry = new MechanicRegistry(List.of(new AreaBreakMechanic(), new FakeMechanic(otherId, java.util.Set.of())));
        MechanicBindingValidator validator = new MechanicBindingValidator(registry, java.util.Set.of(AreaBreakMechanic.ID));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(bindings(new CustomItemId("ruby"), otherId)));

        assertTrue(exception.getMessage().contains("references mechanic not allowed in this phase: other_mechanic"));
    }

    @Test
    void itemWithoutBindingDoesNotExecuteAreaBreak() {
        FakeBlockStore blockStore = new FakeBlockStore();
        flatArea(ORIGIN).forEach(position -> blockStore.blocks.put(position, (short) 7));
        FakeWorldMutation worldMutation = new FakeWorldMutation();
        AreaBreakEventTriggerService service = service(
                blockStore,
                new NoopDropPort(),
                worldMutation,
                MechanicBindingRegistry.empty());

        Optional<MechanicResult> result = service.trigger(new CustomItemId("ruby"), ORIGIN, "player-one");

        assertTrue(result.isEmpty());
        assertEquals(0, blockStore.removed.size());
        assertEquals(0, worldMutation.positions.size());
    }

    @Test
    void itemWithYamlBindingExecutesAdditionalAreaWithoutOrigin() {
        FakeBlockStore blockStore = new FakeBlockStore();
        flatArea(ORIGIN).forEach(position -> blockStore.blocks.put(position, (short) 7));
        CapturingDropPort dropPort = new CapturingDropPort();
        FakeWorldMutation worldMutation = new FakeWorldMutation();
        AreaBreakEventTriggerService service = service(
                blockStore,
                dropPort,
                worldMutation,
                bindings(new CustomItemId("ruby_pickaxe"), AreaBreakMechanic.ID));

        Optional<MechanicResult> result = service.trigger(new CustomItemId("ruby_pickaxe"), ORIGIN, "player-one");

        MechanicResult.Done done = assertInstanceOf(MechanicResult.Done.class, result.orElseThrow());
        assertEquals(8, done.affectedBlocks());
        assertFalse(blockStore.removed.contains(ORIGIN));
        assertFalse(worldMutation.positions.contains(ORIGIN));
        assertFalse(dropPort.positions.contains(ORIGIN));
    }

    private static AreaBreakEventTriggerService service(
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation) {
        return service(
                blockStore,
                dropPort,
                worldMutation,
                bindings(new CustomItemId("ruby_pickaxe"), AreaBreakMechanic.ID));
    }

    private static AreaBreakEventTriggerService service(
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            MechanicBindingRegistry bindings) {
        AreaBreakRuntimeService runtime = new AreaBreakRuntimeService(
                new MechanicRegistry(List.of(new AreaBreakMechanic())),
                AreaBreakMechanic.ID,
                new DefinitionRegistry(List.of(block("ruby_ore", (short) 7)), List.of()),
                blockStore,
                dropPort,
                worldMutation,
                new InMemoryCooldowns(),
                new NoopScheduler());
        return new AreaBreakEventTriggerService(bindings, AreaBreakMechanic.ID, runtime);
    }

    private static MechanicBindingRegistry bindings(CustomItemId itemId, MechanicId mechanicId) {
        return new MechanicBindingRegistry(List.of(new MechanicBinding(
                itemId,
                MechanicTrigger.ON_BLOCK_BREAK,
                mechanicId)));
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

        @Override
        public void setBlockMaterial(WorldPosition position, String materialBase) {
            positions.add(position);
        }
    }

    private static final class CapturingDropPort implements DropPort {
        private final List<WorldPosition> positions = new ArrayList<>();

        @Override
        public void drop(WorldPosition position, DropTable drops) {
            positions.add(position);
        }
    }

    private static final class NoopDropPort implements DropPort {
        @Override
        public void drop(WorldPosition position, DropTable drops) {
        }
    }

    private static final class NoopScheduler implements SchedulerPort {
        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
        }
    }

    private record FakeMechanic(MechanicId id, java.util.Set<Capability> capabilities) implements Mechanic {
        @Override
        public MechanicDescriptor descriptor() {
            return new MechanicDescriptor(id, capabilities, false);
        }

        @Override
        public MechanicResult execute(MechanicContext context) {
            return new MechanicResult.Done(0);
        }
    }
}
