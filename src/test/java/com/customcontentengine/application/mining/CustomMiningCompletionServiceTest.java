package com.customcontentengine.application.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.customcontentengine.application.mechanic.MechanicEventTriggerService;
import com.customcontentengine.application.mechanic.MechanicRuntimeService;
import com.customcontentengine.application.mechanic.MechanicRegistry;
import com.customcontentengine.application.mechanic.capability.InMemoryCooldowns;
import com.customcontentengine.builtin.mechanic.AreaBreakMechanic;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.MiningCompletionPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomMiningCompletionServiceTest {
    private static final String ACTOR_KEY = "player-one";
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);
    private static final CustomItemId TOOL_ID = new CustomItemId("ruby_pickaxe");
    private static final CustomItemId WRONG_TOOL_ID = new CustomItemId("ruby");

    @Test
    void successRemovesIdentityMutatesDropsAndTriggersMechanicOnce() {
        FakeBlockStore blockStore = new FakeBlockStore(Optional.of((short) 1));
        CapturingWorldMutation worldMutation = new CapturingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        MechanicEventTriggerService triggerService = mock(MechanicEventTriggerService.class);
        CustomMiningCompletionService service = service(blockStore, worldMutation, dropPort, position -> true, triggerService);

        MiningCompletionPort.CompletionResult result = service.complete(request(TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.SUCCESS, result.status());
        assertEquals(List.of(TARGET), blockStore.removed);
        assertEquals(List.of(TARGET), worldMutation.positions);
        assertEquals(List.of("AIR"), worldMutation.materials);
        assertEquals(List.of(TARGET), dropPort.positions);
        verify(triggerService).trigger(TOOL_ID, TARGET, ACTOR_KEY);
    }

    @Test
    void nonCustomBlockFailsWithoutMutation() {
        FakeBlockStore blockStore = new FakeBlockStore(Optional.empty());
        CapturingWorldMutation worldMutation = new CapturingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        MechanicEventTriggerService triggerService = mock(MechanicEventTriggerService.class);
        CustomMiningCompletionService service = service(blockStore, worldMutation, dropPort, position -> true, triggerService);

        MiningCompletionPort.CompletionResult result = service.complete(request(TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.POSITION_NOT_CUSTOM_BLOCK, result.status());
        assertEquals(0, blockStore.removed.size());
        assertEquals(0, worldMutation.positions.size());
        assertEquals(0, dropPort.positions.size());
        verifyNoInteractions(triggerService);
    }

    @Test
    void unsafeRegionFailsWithoutMutation() {
        FakeBlockStore blockStore = new FakeBlockStore(Optional.of((short) 1));
        CapturingWorldMutation worldMutation = new CapturingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        MechanicEventTriggerService triggerService = mock(MechanicEventTriggerService.class);
        CustomMiningCompletionService service = service(blockStore, worldMutation, dropPort, position -> false, triggerService);

        MiningCompletionPort.CompletionResult result = service.complete(request(TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.REGION_UNSAFE, result.status());
        assertEquals(0, blockStore.removed.size());
        assertEquals(0, worldMutation.positions.size());
        assertEquals(0, dropPort.positions.size());
        verifyNoInteractions(triggerService);
    }

    @Test
    void toolMismatchFailsWithoutMutation() {
        FakeBlockStore blockStore = new FakeBlockStore(Optional.of((short) 1));
        CapturingWorldMutation worldMutation = new CapturingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        MechanicEventTriggerService triggerService = mock(MechanicEventTriggerService.class);
        CustomMiningCompletionService service = service(blockStore, worldMutation, dropPort, position -> true, triggerService);

        MiningCompletionPort.CompletionResult result = service.complete(request(WRONG_TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.TOOL_MISMATCH, result.status());
        assertEquals(0, blockStore.removed.size());
        assertEquals(0, worldMutation.positions.size());
        assertEquals(0, dropPort.positions.size());
        verifyNoInteractions(triggerService);
    }

    @Test
    void secondCompletionDoesNotDuplicateEffects() {
        FakeBlockStore blockStore = new FakeBlockStore(Optional.of((short) 1));
        CapturingWorldMutation worldMutation = new CapturingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        MechanicEventTriggerService triggerService = mock(MechanicEventTriggerService.class);
        CustomMiningCompletionService service = service(blockStore, worldMutation, dropPort, position -> true, triggerService);

        MiningCompletionPort.CompletionResult first = service.complete(request(TOOL_ID));
        MiningCompletionPort.CompletionResult second = service.complete(request(TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.SUCCESS, first.status());
        assertEquals(MiningCompletionPort.CompletionStatus.POSITION_NOT_CUSTOM_BLOCK, second.status());
        assertEquals(1, blockStore.removed.size());
        assertEquals(1, worldMutation.positions.size());
        assertEquals(1, dropPort.positions.size());
        verify(triggerService).trigger(TOOL_ID, TARGET, ACTOR_KEY);
    }

    @Test
    void areaBreakContinuesWorkingAfterCustomMiningCompletion() {
        AreaBlockStore blockStore = new AreaBlockStore();
        flatArea(TARGET).forEach(position -> blockStore.blocks.put(position, (short) 1));
        CapturingWorldMutation worldMutation = new CapturingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        DefinitionRegistry registry = registryWithAreaBreakBinding();
        MechanicRuntimeService runtime = new MechanicRuntimeService(
                new MechanicRegistry(List.of(new AreaBreakMechanic())),
                registry,
                blockStore,
                dropPort,
                worldMutation,
                new InMemoryCooldowns(),
                new ImmediateScheduler(),
                position -> true);
        MechanicEventTriggerService triggerService = new MechanicEventTriggerService(
                registry.mechanicBindings(),
                runtime);
        CustomMiningCompletionService service = new CustomMiningCompletionService(
                registry,
                blockStore,
                worldMutation,
                dropPort,
                position -> true,
                triggerService,
                null);

        MiningCompletionPort.CompletionResult result = service.complete(request(TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.SUCCESS, result.status());
        assertEquals(9, blockStore.removed.size());
        assertEquals(1, blockStore.removed.stream().filter(TARGET::equals).count());
        assertEquals(9, worldMutation.positions.size());
        assertEquals(1, worldMutation.positions.stream().filter(TARGET::equals).count());
        assertEquals(9, dropPort.positions.size());
        assertEquals(1, dropPort.positions.stream().filter(TARGET::equals).count());
    }

    @Test
    void worldMutationFailureReturnsFailedAndStopsPipeline() {
        FakeBlockStore blockStore = new FakeBlockStore(Optional.of((short) 1));
        FailingWorldMutation worldMutation = new FailingWorldMutation();
        CapturingDropPort dropPort = new CapturingDropPort();
        MechanicEventTriggerService triggerService = mock(MechanicEventTriggerService.class);
        CustomMiningCompletionService service = service(blockStore, worldMutation, dropPort, position -> true, triggerService);

        MiningCompletionPort.CompletionResult result = service.complete(request(TOOL_ID));

        assertEquals(MiningCompletionPort.CompletionStatus.FAILED, result.status());
        assertEquals(1, blockStore.removed.size());
        assertEquals(0, dropPort.positions.size());
        verifyNoInteractions(triggerService);
    }

    private static CustomMiningCompletionService service(
            FakeBlockStore blockStore,
            WorldMutationPort worldMutation,
            CapturingDropPort dropPort,
            RegionSafetyPort regionSafety,
            MechanicEventTriggerService triggerService) {
        return new CustomMiningCompletionService(
                registry(),
                blockStore,
                worldMutation,
                dropPort,
                regionSafety,
                triggerService,
                null);
    }

    private static MiningCompletionPort.CompletionRequest request(CustomItemId toolId) {
        return new MiningCompletionPort.CompletionRequest(ACTOR_KEY, TARGET, toolId);
    }

    private static DefinitionRegistry registry() {
        return new DefinitionRegistry(
                List.of(block("ruby_ore", (short) 1)),
                List.of(
                        item("ruby_pickaxe"),
                        item("ruby")));
    }

    private static DefinitionRegistry registryWithAreaBreakBinding() {
        return new DefinitionRegistry(
                List.of(block("ruby_ore", (short) 1)),
                List.of(item("ruby_pickaxe")),
                new MechanicBindingRegistry(List.of(new MechanicBinding(
                        TOOL_ID,
                        MechanicTrigger.ON_BLOCK_BREAK,
                        AreaBreakMechanic.ID))));
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

    private static ItemDef item(String id) {
        return new ItemDef(
                new CustomItemId(id),
                "DIAMOND_PICKAXE",
                2001,
                new ToolAttributes(5.0D, 1.2D, 500),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static final class FakeBlockStore implements BlockStorePort {
        private Optional<Short> numericId;
        private final List<WorldPosition> removed = new ArrayList<>();

        private FakeBlockStore(Optional<Short> numericId) {
            this.numericId = numericId;
        }

        @Override
        public Optional<Short> findNumericId(WorldPosition position) {
            return numericId;
        }

        @Override
        public void put(WorldPosition position, short numericId) {
            this.numericId = Optional.of(numericId);
        }

        @Override
        public void remove(WorldPosition position) {
            removed.add(position);
            numericId = Optional.empty();
        }
    }

    private static final class CapturingWorldMutation implements WorldMutationPort {
        private final List<WorldPosition> positions = new ArrayList<>();
        private final List<String> materials = new ArrayList<>();

        @Override
        public void setBlockMaterial(WorldPosition position, String materialBase) {
            positions.add(position);
            materials.add(materialBase);
        }
    }

    private static final class CapturingDropPort implements DropPort {
        private final List<WorldPosition> positions = new ArrayList<>();

        @Override
        public void drop(WorldPosition position, DropTable drops) {
            positions.add(position);
        }
    }

    private static final class AreaBlockStore implements BlockStorePort {
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

    private static final class ImmediateScheduler implements SchedulerPort {
        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
            task.run();
        }
    }

    private static final class FailingWorldMutation implements WorldMutationPort {
        @Override
        public void setBlockMaterial(WorldPosition position, String materialBase) {
            throw new RuntimeException("Simulated world mutation failure");
        }
    }
}
