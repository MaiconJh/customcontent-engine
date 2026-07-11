package com.customcontentengine.application.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.builtin.mechanic.VeinMinerMechanic;
import com.customcontentengine.domain.durability.ToolWearResult;
import com.customcontentengine.domain.mechanic.MechanicBinding;
import com.customcontentengine.domain.mechanic.MechanicBindingRegistry;
import com.customcontentengine.domain.mechanic.MechanicTrigger;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.MechanicResult;
import com.customcontentengine.internalapi.mechanic.capability.ActorState;
import com.customcontentengine.internalapi.mechanic.capability.EnchantmentView;
import com.customcontentengine.port.BlockStorePort;
import com.customcontentengine.port.DropPort;
import com.customcontentengine.port.ProtectionPort;
import com.customcontentengine.port.RegionSafetyPort;
import com.customcontentengine.port.SchedulerPort;
import com.customcontentengine.port.ToolWearPort;
import com.customcontentengine.port.WorldMutationPort;
import java.util.List;
import java.util.Map;
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
                ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty(), null);

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
                ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty(), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void triggerReturnsEmptyWhenPlayerDisabled() {
        BlockStorePort blockStore = mock(BlockStorePort.class);
        DropPort dropPort = mock(DropPort.class);
        WorldMutationPort worldMutation = mock(WorldMutationPort.class);
        RegionSafetyPort regionSafety = mock(RegionSafetyPort.class);
        MechanicBindingRegistry bindings = new MechanicBindingRegistry(List.of(
                new MechanicBinding(ITEM, MechanicTrigger.ON_BLOCK_BREAK, VeinMinerMechanic.ID)));
        PlayerPreferenceService preferences = new PlayerPreferenceService();
        preferences.setEnabled("player-one", false);

        VeinMinerRuntimeService runtime = runtime(bindings, blockStore, dropPort, worldMutation, regionSafety);
        VeinMinerEventTriggerService trigger = new VeinMinerEventTriggerService(
                bindings, VeinMinerMechanic.ID, runtime, preferences);

        Optional<MechanicResult> result = trigger.trigger(
                ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty(), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void appliesPerBlockDurabilityWhenConfigured() {
        BlockStorePort blockStore = mock(BlockStorePort.class);
        mockVein(blockStore);
        DropPort dropPort = mock(DropPort.class);
        WorldMutationPort worldMutation = mock(WorldMutationPort.class);
        RegionSafetyPort regionSafety = mock(RegionSafetyPort.class);
        mockRegion(regionSafety);
        ToolWearPort toolWear = mock(ToolWearPort.class);
        when(toolWear.applyWearIfNeeded("player-one", ITEM, 3))
                .thenReturn(Optional.of(mock(ToolWearResult.class)));
        MechanicBindingRegistry bindings = new MechanicBindingRegistry(List.of(
                new MechanicBinding(ITEM, MechanicTrigger.ON_BLOCK_BREAK, VeinMinerMechanic.ID,
                        Map.of("durability_per_block", true))));

        VeinMinerRuntimeService runtime = runtime(
                bindings, blockStore, dropPort, worldMutation, regionSafety, toolWear, null);
        VeinMinerEventTriggerService trigger = new VeinMinerEventTriggerService(
                bindings, VeinMinerMechanic.ID, runtime);

        trigger.trigger(ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty(), null);

        verify(toolWear, times(1)).applyWearIfNeeded("player-one", ITEM, 3);
    }

    @Test
    void appliesSingleDurabilityWhenPerBlockDisabled() {
        BlockStorePort blockStore = mock(BlockStorePort.class);
        mockVein(blockStore);
        DropPort dropPort = mock(DropPort.class);
        WorldMutationPort worldMutation = mock(WorldMutationPort.class);
        RegionSafetyPort regionSafety = mock(RegionSafetyPort.class);
        mockRegion(regionSafety);
        ToolWearPort toolWear = mock(ToolWearPort.class);
        when(toolWear.applyWearIfNeeded("player-one", ITEM, 1))
                .thenReturn(Optional.of(mock(ToolWearResult.class)));
        MechanicBindingRegistry bindings = new MechanicBindingRegistry(List.of(
                new MechanicBinding(ITEM, MechanicTrigger.ON_BLOCK_BREAK, VeinMinerMechanic.ID,
                        Map.of("durability_per_block", false))));

        VeinMinerRuntimeService runtime = runtime(
                bindings, blockStore, dropPort, worldMutation, regionSafety, toolWear, null);
        VeinMinerEventTriggerService trigger = new VeinMinerEventTriggerService(
                bindings, VeinMinerMechanic.ID, runtime);

        trigger.trigger(ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty(), null);

        verify(toolWear, times(1)).applyWearIfNeeded("player-one", ITEM, 1);
    }

    @Test
    void skipsProtectedBlocksWithoutCounting() {
        BlockStorePort blockStore = mock(BlockStorePort.class);
        WorldPosition p0 = ORIGIN;
        WorldPosition p1 = new WorldPosition("world", 11, 64, 20);
        WorldPosition p2 = new WorldPosition("world", 12, 64, 20);
        when(blockStore.findNumericId(p0)).thenReturn(Optional.of((short) 100));
        when(blockStore.findNumericId(p1)).thenReturn(Optional.of((short) 100));
        when(blockStore.findNumericId(p2)).thenReturn(Optional.of((short) 100));
        DropPort dropPort = mock(DropPort.class);
        WorldMutationPort worldMutation = mock(WorldMutationPort.class);
        RegionSafetyPort regionSafety = mock(RegionSafetyPort.class);
        when(regionSafety.canAccess(p0)).thenReturn(true);
        when(regionSafety.canAccess(p1)).thenReturn(true);
        when(regionSafety.canAccess(p2)).thenReturn(true);
        ProtectionPort protection = mock(ProtectionPort.class);
        when(protection.canBuild("player-one", p0)).thenReturn(true);
        when(protection.canBuild("player-one", p1)).thenReturn(true);
        when(protection.canBuild("player-one", p2)).thenReturn(false);
        MechanicBindingRegistry bindings = new MechanicBindingRegistry(List.of(
                new MechanicBinding(ITEM, MechanicTrigger.ON_BLOCK_BREAK, VeinMinerMechanic.ID)));

        VeinMinerRuntimeService runtime = runtime(
                bindings, blockStore, dropPort, worldMutation, regionSafety, null, protection);
        VeinMinerEventTriggerService trigger = new VeinMinerEventTriggerService(
                bindings, VeinMinerMechanic.ID, runtime);

        Optional<MechanicResult> result = trigger.trigger(
                ITEM, ORIGIN, "player-one", (EnchantmentView) key -> OptionalInt.empty(), null);

        assertTrue(result.isPresent());
        int affected = result.get() instanceof MechanicResult.Done done
                ? done.affectedBlocks()
                : ((MechanicResult.Partial) result.get()).affectedBlocks();
        assertEquals(2, affected);
        verify(worldMutation, never()).setBlockMaterial(eq(p2), anyString());
    }

    private void mockVein(BlockStorePort blockStore) {
        when(blockStore.findNumericId(ORIGIN)).thenReturn(Optional.of((short) 100));
        when(blockStore.findNumericId(new WorldPosition("world", 11, 64, 20))).thenReturn(Optional.of((short) 100));
        when(blockStore.findNumericId(new WorldPosition("world", 12, 64, 20))).thenReturn(Optional.of((short) 100));
    }

    private void mockRegion(RegionSafetyPort regionSafety) {
        when(regionSafety.canAccess(ORIGIN)).thenReturn(true);
        when(regionSafety.canAccess(new WorldPosition("world", 11, 64, 20))).thenReturn(true);
        when(regionSafety.canAccess(new WorldPosition("world", 12, 64, 20))).thenReturn(true);
    }

    private VeinMinerRuntimeService runtime(
            MechanicBindingRegistry bindings,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            RegionSafetyPort regionSafety) {
        return runtime(bindings, blockStore, dropPort, worldMutation, regionSafety, null, null);
    }

    private VeinMinerRuntimeService runtime(
            MechanicBindingRegistry bindings,
            BlockStorePort blockStore,
            DropPort dropPort,
            WorldMutationPort worldMutation,
            RegionSafetyPort regionSafety,
            ToolWearPort toolWearPort,
            ProtectionPort protectionPort) {
        return new VeinMinerRuntimeService(
                new MechanicRegistry(List.of(new VeinMinerMechanic())),
                VeinMinerMechanic.ID,
                new DefinitionRegistry(List.of(), List.of(), bindings),
                blockStore,
                dropPort,
                worldMutation,
                new com.customcontentengine.application.mechanic.capability.InMemoryCooldowns(),
                mock(SchedulerPort.class),
                regionSafety,
                toolWearPort,
                protectionPort);
    }
}
