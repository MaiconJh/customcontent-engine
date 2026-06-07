package com.customcontentengine.adapter.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BukkitDropAdapterTest {
    @Test
    void dropsCustomItemCreatedByFactory() {
        World world = mock(World.class);
        ItemStack customItem = mock(ItemStack.class);
        BukkitDropAdapter adapter = new BukkitDropAdapter(
                id -> Optional.of(customItem),
                raw -> null,
                position -> world);

        adapter.drop(
                new WorldPosition("world", 10, 64, 12),
                new DropTable(List.of(new DropTable.Entry("ruby", 3))));

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<ItemStack> itemCaptor = ArgumentCaptor.forClass(ItemStack.class);
        verify(world).dropItemNaturally(locationCaptor.capture(), itemCaptor.capture());
        assertEquals(10.5, locationCaptor.getValue().getX());
        assertEquals(64.5, locationCaptor.getValue().getY());
        assertEquals(12.5, locationCaptor.getValue().getZ());
        assertEquals(customItem, itemCaptor.getValue());
        verify(customItem).setAmount(3);
    }
}
