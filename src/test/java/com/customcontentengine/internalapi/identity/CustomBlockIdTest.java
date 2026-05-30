package com.customcontentengine.internalapi.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CustomBlockIdTest {
    @Test
    void acceptsSimplePredictableId() {
        assertEquals("ruby_ore", new CustomBlockId("ruby_ore").value());
    }

    @Test
    void rejectsBlankAndSpaces() {
        assertThrows(IllegalArgumentException.class, () -> new CustomBlockId(""));
        assertThrows(IllegalArgumentException.class, () -> new CustomBlockId("ruby ore"));
    }
}
