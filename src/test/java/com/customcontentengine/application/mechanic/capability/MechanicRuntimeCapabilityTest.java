package com.customcontentengine.application.mechanic.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.application.budget.WorkBudget;
import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MechanicRuntimeCapabilityTest {
    private static final WorldPosition POSITION = new WorldPosition("world", 1, 64, 1);

    @Test
    void storedBlockQueryReadsNumericIdFromStore() {
        FakeBlockStore store = new FakeBlockStore((short) 7);

        assertEquals(Optional.of((short) 7), new StoredBlockQuery(store).findCustomBlockNumericId(POSITION));
    }

    @Test
    void storedBlockMutationRemovesIdentityAndClearsBlockMaterial() {
        FakeBlockStore store = new FakeBlockStore((short) 7);
        FakeWorldMutation worldMutation = new FakeWorldMutation();

        new StoredBlockMutation(store, worldMutation).breakBlock(POSITION);

        assertEquals(List.of(POSITION), store.removed);
        assertEquals(List.of("AIR@" + POSITION), worldMutation.mutations);
    }

    @Test
    void storedBlockMutationDoesNotMutateUnsafePosition() {
        FakeBlockStore store = new FakeBlockStore((short) 7);
        FakeWorldMutation worldMutation = new FakeWorldMutation();
        RegionSafetyPort unsafe = position -> false;

        new StoredBlockMutation(store, worldMutation, unsafe).breakBlock(POSITION);

        assertEquals(List.of(), store.removed);
        assertEquals(List.of(), worldMutation.mutations);
    }

    @Test
    void definitionDropSinkUsesNumericIdToDeliverDrops() {
        CapturingDropPort dropPort = new CapturingDropPort();
        DefinitionRegistry definitions = new DefinitionRegistry(List.of(block("ruby_ore", (short) 7)), List.of());

        new DefinitionDropSink(definitions, dropPort).dropFor(POSITION, (short) 7);

        assertEquals(1, dropPort.drops.size());
        assertEquals("ruby", dropPort.drops.get(0).entries().get(0).item());
    }

    @Test
    void workBudgetViewConsumesOneOperationPerPosition() {
        WorkBudgetView budget = new WorkBudgetView(new WorkBudget(1));

        assertTrue(budget.tryConsume(POSITION));
        assertFalse(budget.tryConsume(POSITION));
    }

    @Test
    void staticExecutionOriginReturnsConfiguredOrigin() {
        assertEquals(POSITION, new StaticExecutionOrigin(POSITION).origin());
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
        private final short numericId;
        private final List<WorldPosition> removed = new ArrayList<>();

        private FakeBlockStore(short numericId) {
            this.numericId = numericId;
        }

        @Override
        public Optional<Short> findNumericId(WorldPosition position) {
            return Optional.of(numericId);
        }

        @Override
        public void put(WorldPosition position, short numericId) {
        }

        @Override
        public void remove(WorldPosition position) {
            removed.add(position);
        }
    }

    private static final class FakeWorldMutation implements WorldMutationPort {
        private final List<String> mutations = new ArrayList<>();

        @Override
        public void setBlockMaterial(WorldPosition position, String materialBase) {
            mutations.add(materialBase + "@" + position);
        }
    }

    private static final class CapturingDropPort implements DropPort {
        private final List<DropTable> drops = new ArrayList<>();

        @Override
        public void drop(WorldPosition position, DropTable drops) {
            this.drops.add(drops);
        }
    }
}
