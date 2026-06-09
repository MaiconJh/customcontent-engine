package com.customcontentengine.adapter.bukkit;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.customcontentengine.application.item.ItemService;
import com.customcontentengine.domain.durability.ToolDurability;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ItemCommandAdapterTest {
    @Test
    void reportsUsageWhenArgumentIsMissing() {
        ItemCommandAdapter adapter = new ItemCommandAdapter(emptyService());
        Player player = mock(Player.class);

        adapter.onCommand(player, null, "givecustomitem", new String[0]);

        verify(player).sendMessage(contains("/givecustomitem <id>"));
    }

    @Test
    void reportsUnknownItem() {
        ItemCommandAdapter adapter = new ItemCommandAdapter(emptyService());
        Player player = mock(Player.class);

        adapter.onCommand(player, null, "givecustomitem", new String[] {"missing_item"});

        verify(player).sendMessage(contains("Unknown custom item"));
    }

    private ItemService<ItemStack> emptyService() {
        return new ItemService<>(new DefinitionRegistry(List.of(), List.of()), new NoopItemMetadataPort());
    }

    private static final class NoopItemMetadataPort implements ItemMetadataPort<ItemStack> {
        @Override
        public ItemStack createCustomItem(com.customcontentengine.domain.definition.ItemDef definition) {
            throw new UnsupportedOperationException("not used by these tests");
        }

        @Override
        public ItemStack applyCustomItemIdentity(ItemStack item, com.customcontentengine.internalapi.identity.CustomItemId id) {
            return item;
        }

        @Override
        public Optional<com.customcontentengine.internalapi.identity.CustomItemId> readCustomItemIdentity(ItemStack item) {
            return Optional.empty();
        }

        @Override
        public ToolDurability initialDurabilityFor(int max) {
            return new ToolDurability(max, max);
        }

        @Override
        public Optional<ToolDurability> readCurrentDurability(ItemStack item, int max) {
            return Optional.of(new ToolDurability(max, max));
        }

        @Override
        public ItemStack writeCurrentDurability(ItemStack item, ToolDurability durability) {
            return item;
        }
    }
}
