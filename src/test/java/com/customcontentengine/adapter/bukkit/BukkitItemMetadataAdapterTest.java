package com.customcontentengine.adapter.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.durability.ToolDurability;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.Optional;
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
    private static final NamespacedKey ID_KEY = new NamespacedKey("customcontentengine", "custom_item_id");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("customcontentengine", "tool_durability");

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
                ID_KEY,
                DURABILITY_KEY,
                material -> {
                    assertEquals(Material.DIAMOND_PICKAXE, material);
                    return item;
                },
                material -> true);
        ItemDef definition = new ItemDef(
                new CustomItemId("ruby_pickaxe"),
                "DIAMOND_PICKAXE",
                2001,
                new ToolAttributes(5.0, 1.2, 500),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        ItemStack created = adapter.createCustomItem(definition);

        assertEquals(item, created);
        verify(meta).setCustomModelData(2001);
        verify(container).set(ID_KEY, PersistentDataType.STRING, "ruby_pickaxe");
    }

    @Test
    void readsItemPdcIdentity() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(container.get(ID_KEY, PersistentDataType.STRING)).thenReturn("ruby_pickaxe");
        BukkitItemMetadataAdapter adapter = new BukkitItemMetadataAdapter(ID_KEY, DURABILITY_KEY, material -> item, m -> true);

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
        BukkitItemMetadataAdapter adapter = new BukkitItemMetadataAdapter(ID_KEY, DURABILITY_KEY, material -> item, m -> true);

        adapter.applyCustomItemIdentity(item, new CustomItemId("ruby_pickaxe"));
        editorCaptor.getValue().accept(meta);

        verify(item).editMeta(org.mockito.ArgumentMatchers.<Consumer<ItemMeta>>any());
        verify(container).set(eq(ID_KEY), eq(PersistentDataType.STRING), eq("ruby_pickaxe"));
    }

    @Test
    void writesAndReadsCurrentDurability() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.editMeta(org.mockito.ArgumentMatchers.<Consumer<ItemMeta>>any())).thenAnswer(invocation -> { Consumer<ItemMeta> editor = invocation.getArgument(0); editor.accept(meta); return true; });
        when(container.get(DURABILITY_KEY, PersistentDataType.INTEGER)).thenReturn(495);

        BukkitItemMetadataAdapter adapter = new BukkitItemMetadataAdapter(ID_KEY, DURABILITY_KEY, material -> item, m -> true);

        ToolDurability written = adapter.initialDurabilityFor(500);
        assertEquals(500, written.max());
        assertEquals(500, written.current());

        adapter.writeCurrentDurability(item, new ToolDurability(500, 495));
        verify(container).set(eq(DURABILITY_KEY), eq(PersistentDataType.INTEGER), eq(495));

        Optional<ToolDurability> read = adapter.readCurrentDurability(item, 500);
        assertEquals(500, read.orElseThrow().max());
        assertEquals(495, read.orElseThrow().current());
    }
}
