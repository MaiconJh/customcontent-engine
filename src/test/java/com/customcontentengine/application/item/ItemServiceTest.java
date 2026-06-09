package com.customcontentengine.application.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.durability.ToolDurability;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.port.ItemMetadataPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemServiceTest {
    @Test
    void rejectsUnknownItem() {
        ItemService<String> service = new ItemService<>(new DefinitionRegistry(List.of(), List.of()), new FakeItemMetadataPort());

        ItemService.ItemCreationResult<String> result = service.createCustomItem("missing_item");

        assertEquals(ItemService.ItemCreationStatus.UNKNOWN_ITEM, result.status());
        assertTrue(result.item().isEmpty());
    }

    @Test
    void callsItemMetadataPortForKnownItem() {
        FakeItemMetadataPort metadata = new FakeItemMetadataPort();
        ItemDef rubyPickaxe = item("ruby_pickaxe");
        ItemService<String> service = new ItemService<>(new DefinitionRegistry(List.of(), List.of(rubyPickaxe)), metadata);

        ItemService.ItemCreationResult<String> result = service.createCustomItem("ruby_pickaxe");

        assertEquals(ItemService.ItemCreationStatus.SUCCESS, result.status());
        assertEquals(Optional.of("created:ruby_pickaxe"), result.item());
        assertEquals(rubyPickaxe, metadata.createdDefinition);
    }

    private ItemDef item(String id) {
        return new ItemDef(new CustomItemId(id), "DIAMOND_PICKAXE", 2001, new ToolAttributes(5.0, 1.2, 500), Optional.empty(), Optional.empty());
    }

    private static final class FakeItemMetadataPort implements ItemMetadataPort<String> {
        private ItemDef createdDefinition;

        @Override
        public String createCustomItem(ItemDef definition) {
            createdDefinition = definition;
            return "created:" + definition.id().value();
        }

        @Override
        public String applyCustomItemIdentity(String item, CustomItemId id) {
            return item + ":" + id.value();
        }

        @Override
        public Optional<CustomItemId> readCustomItemIdentity(String item) {
            return Optional.empty();
        }

        @Override
        public ToolDurability initialDurabilityFor(int max) {
            return new ToolDurability(max, max);
        }

        @Override
        public Optional<ToolDurability> readCurrentDurability(String item, int max) {
            return Optional.of(new ToolDurability(max, max));
        }

        @Override
        public String writeCurrentDurability(String item, ToolDurability durability) {
            return item;
        }
    }
}
