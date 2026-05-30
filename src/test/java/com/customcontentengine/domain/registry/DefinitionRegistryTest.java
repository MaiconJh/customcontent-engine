package com.customcontentengine.domain.registry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.domain.definition.ItemDef;
import com.customcontentengine.domain.definition.ToolAttributes;
import com.customcontentengine.internalapi.identity.CustomBlockId;
import com.customcontentengine.internalapi.identity.CustomItemId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinitionRegistryTest {
    @Test
    void supportsLookupByTextAndNumericId() {
        BlockDef block = block("ruby_ore", (short) 1);
        ItemDef item = item("ruby_pickaxe");
        DefinitionRegistry registry = new DefinitionRegistry(List.of(block), List.of(item));

        assertTrue(registry.findBlock("ruby_ore").isPresent());
        assertTrue(registry.findBlockByNumericId((short) 1).isPresent());
        assertTrue(registry.findItem("ruby_pickaxe").isPresent());
    }

    @Test
    void rejectsDuplicateNumericId() {
        assertThrows(IllegalArgumentException.class, () -> new DefinitionRegistry(
                List.of(block("ruby_ore", (short) 1), block("sapphire_ore", (short) 1)),
                List.of()
        ));
    }

    private BlockDef block(String id, short numericId) {
        return new BlockDef(new CustomBlockId(id), numericId, "NOTE_BLOCK", 1001, "ruby_pickaxe", new DropTable(List.of(new DropTable.Entry("ruby", 1))));
    }

    private ItemDef item(String id) {
        return new ItemDef(new CustomItemId(id), "DIAMOND_PICKAXE", 2001, new ToolAttributes(5.0, 1.2, 500));
    }
}
