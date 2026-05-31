package com.customcontentengine.adapter.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BukkitItemMetadataAdapterTest {
    private static final NamespacedKey KEY = new NamespacedKey("customcontentengine", "custom_item_id");

    @Test
    void createsItemWithMaterialCustomModelDataAndPdcIdentity() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(item.editMeta(org.mockito.ArgumentMatchers.<Consumer<ItemMeta>>any())).thenAnswer(invocation -> {
            Consumer<ItemMeta> editor = invocation.getArgument(0);
            editor.accept(meta);
            return true;
        });
        BukkitItemMetadataAdapter adapter = new BukkitItemMetadataAdapter(
                KEY,
                material -> {
                    assertEquals(Material.DIAMOND_PICKAXE, material);
                    return item;
                },
                material -> true);
        ItemDef definition = new ItemDef(
                new CustomItemId("ruby_pickaxe"),
                "DIAMOND_PICKAXE",
                2001,
                new ToolAttributes(5.0, 1.2, 500));

        ItemStack created = adapter.createCustomItem(definition);

        assertEquals(item, created);
        verify(meta).setCustomModelData(2001);
        verify(container).set(KEY, PersistentDataType.STRING, "ruby_pickaxe");
    }

    @Test
    void readsItemPdcIdentity() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(container.get(KEY, PersistentDataType.STRING)).thenReturn("ruby_pickaxe");
        BukkitItemMetadataAdapter adapter = new BukkitItemMetadataAdapter(KEY, material -> item);

        assertEquals(new CustomItemId("ruby_pickaxe"), adapter.readCustomItemIdentity(item).orElseThrow());
    }

    @Test
    void appliesIdentityWithEditMeta() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ItemMeta>> editorCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(item.editMeta(editorCaptor.capture())).thenReturn(true);
        BukkitItemMetadataAdapter adapter = new BukkitItemMetadataAdapter(KEY, material -> item);

        adapter.applyCustomItemIdentity(item, new CustomItemId("ruby_pickaxe"));
        editorCaptor.getValue().accept(meta);

        verify(item).editMeta(org.mockito.ArgumentMatchers.<Consumer<ItemMeta>>any());
        verify(container).set(eq(KEY), eq(PersistentDataType.STRING), eq("ruby_pickaxe"));
    }
}
