package com.customcontentengine.application.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.customcontentengine.builtin.mechanic.VeinMinerMechanic;
import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class VeinMinerRuntimeServiceTest {
    private static final WorldPosition ORIGIN = new WorldPosition("world", 10, 64, 20);
    private static final CustomItemId ITEM = new CustomItemId("ruby_pickaxe");

    @Test
    void triggerExecutesMechanicWhenBindingExists() {
        BlockStorePort blockStore = mock(BlockStorePort.class);
        when(blockStore.findNumericId(ORIGIN)).thenReturn(Optional.of((short) 100));
        when(blockStore.findNumericId(new WorldPosition("world", 11, 64, 20))).thenReturn(Optional.of((short) 100));
        when(blockStore.findNumericId(new WorldPosition("world", 12, 64, 20))).thenReturn(Optional.of((short) 100));
        DropPort dropPort = mock(DropPort.class);
        WorldMutationPort worldMutation = mock(WorldMutationPort.class);
        RegionSafetyPort regionSafety = mock(RegionSafetyPort.class);
        when(regionSafety.canAccess(ORIGIN)).thenReturn(true);
        when(regionSafety.canAccess(new WorldPosition("world", 11, 64, 20))).thenReturn(true);
        when(regionSafety.canAccess(new WorldPosition("world", 12, 64, 20))).thenReturn(true);
        MechanicBindingRegistry bindings = new MechanicBindingRegistry(List.of(
                new MechanicBinding(ITEM, MechanicTrigger.ON_BLOCK_BREAK, VeinMinerMechanic.ID)));

        VeinMinerRuntimeService runtime = runtime(bindings, blockStore, dropPort, worldMutation, regionSafety);
        VeinMinerEventTriggerService trigger = new VeinMinerEventTriggerService(
                bindings, VeinMinerMechanic.ID, runtime);

        Optional<MechanicResult> result = trigger.trigger(
                ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty());

        assertTrue(result.isPresent());
        MechanicResult mechanicResult = result.get();
        int affected = mechanicResult instanceof MechanicResult.Done done
                ? done.affectedBlocks()
                : ((MechanicResult.Partial) mechanicResult).affectedBlocks();
        assertEquals(3, affected);
    }

    @Test
    void triggerReturnsEmptyWhenNoBinding() {
        BlockStorePort blockStore = mock(BlockStorePort.class);
        DropPort dropPort = mock(DropPort.class);
        WorldMutationPort worldMutation = mock(WorldMutationPort.class);
        RegionSafetyPort regionSafety = mock(RegionSafetyPort.class);
        MechanicBindingRegistry bindings = MechanicBindingRegistry.empty();

        VeinMinerRuntimeService runtime = runtime(bindings, blockStore, dropPort, worldMutation, regionSafety);
        VeinMinerEventTriggerService trigger = new VeinMinerEventTriggerService(
                bindings, VeinMinerMechanic.ID, runtime);

        Optional<MechanicResult> result = trigger.trigger(
                ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty());

        assertTrue(result.isEmpty());
    }

    private VeinMinerRuntimeService runtime(
            MechanicBindingRegistry bindings,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            RegionSafetyPort regionSafety) {
        return new VeinMinerRuntimeService(
                new MechanicRegistry(List.of(new VeinMinerMechanic())),
                VeinMinerMechanic.ID,
                new DefinitionRegistry(List.of(), List.of(), bindings),
                blockStore,
                dropPort,
                worldMutation,
                new com.customcontentengine.application.mechanic.capability.InMemoryCooldowns(),
                mock(SchedulerPort.class),
                regionSafety);
    }
}
