package com.customcontentengine.domain.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import org.junit.jupiter.api.Test;

class MiningSessionTest {
    private static final MiningSessionId SESSION_ID = new MiningSessionId("session-1");
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);
    private static final CustomItemId TOOL_ID = new CustomItemId("ruby_pickaxe");

    @Test
    void sessionStoresPureIdentityAndTimingState() {
        MiningSession session = MiningSession.start(SESSION_ID, "player-one", TARGET, TOOL_ID, 1000L, 4000L);

        assertEquals(SESSION_ID, session.id());
        assertEquals("player-one", session.actorKey());
        assertEquals(TARGET, session.target());
        assertEquals(TOOL_ID, session.toolId());
        assertEquals(1000L, session.startedAtMillis());
        assertEquals(4000L, session.expectedDurationMillis());
        assertEquals(new MiningStage(0), session.lastVisualStage());
    }

    @Test
    void sessionCalculatesProgressFromAbsoluteTime() {
        MiningSession session = MiningSession.start(SESSION_ID, "player-one", TARGET, TOOL_ID, 1000L, 4000L);

        assertEquals(0.25D, session.progressAt(2000L).value());
        assertEquals(1.0D, session.progressAt(6000L).value());
    }

    @Test
    void sessionCalculatesVisualStageFromProgress() {
        MiningSession session = MiningSession.start(SESSION_ID, "player-one", TARGET, TOOL_ID, 1000L, 4000L);

        assertEquals(new MiningStage(0), session.visualStageAt(1000L));
        assertEquals(new MiningStage(2), session.visualStageAt(2000L));
        assertEquals(new MiningStage(9), session.visualStageAt(5000L));
    }

    @Test
    void visualStageChangeComparesWithStoredLastStage() {
        MiningSession session = MiningSession.start(SESSION_ID, "player-one", TARGET, TOOL_ID, 1000L, 4000L);

        assertFalse(session.visualStageChangedAt(1000L));
        assertTrue(session.visualStageChangedAt(2000L));
    }

    @Test
    void updatingLastVisualStageReturnsNewSession() {
        MiningSession session = MiningSession.start(SESSION_ID, "player-one", TARGET, TOOL_ID, 1000L, 4000L);

        MiningSession updated = session.withLastVisualStage(new MiningStage(2));

        assertNotSame(session, updated);
        assertEquals(new MiningStage(0), session.lastVisualStage());
        assertEquals(new MiningStage(2), updated.lastVisualStage());
    }

    @Test
    void invalidSessionValuesFail() {
        assertThrows(IllegalArgumentException.class, () -> new MiningSessionId(""));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSession.start(SESSION_ID, "", TARGET, TOOL_ID, 1000L, 4000L));
        assertThrows(IllegalArgumentException.class,
                () -> MiningSession.start(SESSION_ID, "player-one", TARGET, TOOL_ID, 1000L, 0L));
    }
}
