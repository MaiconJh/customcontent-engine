package com.customcontentengine.application.mechanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerPreferenceServiceTest {

    private final PlayerPreferenceService service = new PlayerPreferenceService();

    @Test
    void defaultsToEnabledWhenAbsent() {
        assertTrue(service.isEnabled("unknown-player"));
    }

    @Test
    void setEnabledPersistsValue() {
        service.setEnabled("player-one", false);
        assertFalse(service.isEnabled("player-one"));
    }

    @Test
    void toggleSwitchesValue() {
        service.setEnabled("player-two", true);
        assertEquals(false, service.toggle("player-two"));
        assertEquals(true, service.toggle("player-two"));
    }

    @Test
    void toggleDefaultsAbsentPlayerToEnabledThenFalse() {
        assertEquals(false, service.toggle("new-player"));
        assertEquals(true, service.toggle("new-player"));
    }
}
