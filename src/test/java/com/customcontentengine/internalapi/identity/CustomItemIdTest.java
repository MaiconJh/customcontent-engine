package com.customcontentengine.internalapi.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CustomItemIdTest {
    @Test
    void acceptsSimplePredictableId() {
        assertEquals("ruby_pickaxe", new CustomItemId("ruby_pickaxe").value());
    }

    @Test
    void rejectsBlankAndSpaces() {
        assertThrows(IllegalArgumentException.class, () -> new CustomItemId(" "));
        assertThrows(IllegalArgumentException.class, () -> new CustomItemId("ruby pickaxe"));
    }
}
