package com.customcontentengine.application.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.mining.MiningDurationPolicy;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningSession;
import com.customcontentengine.domain.mining.MiningSessionId;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MiningSessionServiceTest {
    private static final String ACTOR_KEY = "player-one";
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);
    private static final WorldPosition OTHER_TARGET = new WorldPosition("world", 10, 64, 11);
    private static final CustomItemId TOOL_ID = new CustomItemId("ruby_pickaxe");
    private static final MiningHardness HARDNESS = new MiningHardness(4.0D);
    private static final MiningSpeed SPEED = new MiningSpeed(2.0D);

    @Test
    void startCreatesSession() {
        MiningSessionService service = service();

        MiningSession session = service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        assertNotNull(session);
        assertEquals(ACTOR_KEY, session.actorKey());
        assertEquals(TARGET, session.target());
        assertEquals(TOOL_ID, session.toolId());
        assertEquals(0, session.progressAt(1000L).value(), 0.0D);
        assertEquals(0, session.lastVisualStage().value());
    }

    @Test
    void startReplacesPreviousSessionForSameActorKey() {
        MiningSessionService service = service();

        MiningSession firstSession = service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);
        MiningSession secondSession = service.startSession(ACTOR_KEY, OTHER_TARGET, TOOL_ID, HARDNESS, SPEED, 2000L);

        Optional<MiningSession> active = service.getActiveSession(ACTOR_KEY);
        assertTrue(active.isPresent());
        assertEquals(OTHER_TARGET, active.get().target());
        assertNotEquals(firstSession.id(), secondSession.id());
    }

    @Test
    void getActiveSessionReturnsActiveSession() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        Optional<MiningSession> active = service.getActiveSession(ACTOR_KEY);

        assertTrue(active.isPresent());
        assertEquals(ACTOR_KEY, active.get().actorKey());
    }

    @Test
    void cancelRemovesSession() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        Optional<MiningSession> canceled = service.cancelSession(ACTOR_KEY);
        Optional<MiningSession> active = service.getActiveSession(ACTOR_KEY);

        assertTrue(canceled.isPresent());
        assertFalse(active.isPresent());
    }

    @Test
    void cancelByTargetOnlyRemovesWhenMatches() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        boolean canceledWrongTarget = service.cancelSession(ACTOR_KEY, OTHER_TARGET);
        Optional<MiningSession> activeAfterWrong = service.getActiveSession(ACTOR_KEY);

        assertFalse(canceledWrongTarget);
        assertTrue(activeAfterWrong.isPresent());

        boolean canceledCorrectTarget = service.cancelSession(ACTOR_KEY, TARGET);
        Optional<MiningSession> activeAfterCorrect = service.getActiveSession(ACTOR_KEY);

        assertTrue(canceledCorrectTarget);
        assertFalse(activeAfterCorrect.isPresent());
    }

    @Test
    void oneSessionPerActorKeyIsPreserved() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);
        service.startSession(ACTOR_KEY, OTHER_TARGET, TOOL_ID, HARDNESS, SPEED, 2000L);

        Optional<MiningSession> active = service.getActiveSession(ACTOR_KEY);

        assertTrue(active.isPresent());
        assertEquals(OTHER_TARGET, active.get().target());
    }

    @Test
    void processCalculatesProgressUsingAbsoluteTime() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        MiningSessionService.ProcessResult resultAt1500 = service.processSession(ACTOR_KEY, 1500L);
        assertTrue(resultAt1500.session().isPresent());
        assertFalse(resultAt1500.completed());
        assertEquals(0.25D, resultAt1500.progress().value(), 0.0001D);

        MiningSessionService.ProcessResult resultAt3000 = service.processSession(ACTOR_KEY, 3000L);
        assertTrue(resultAt3000.completed());
        assertEquals(1.0D, resultAt3000.progress().value(), 0.0001D);
        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
    }

    @Test
    void lastVisualStageOnlyChangesWhenNecessary() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, new MiningHardness(50.0D), new MiningSpeed(10.0D), 1000L);

        MiningSessionService.ProcessResult first = service.processSession(ACTOR_KEY, 1500L);
        assertTrue(first.visualStageChanged());
        assertEquals(1, first.visualStage().value());

        MiningSessionService.ProcessResult second = service.processSession(ACTOR_KEY, 1600L);
        assertFalse(second.visualStageChanged());
        assertEquals(1, second.visualStage().value());

        MiningSessionService.ProcessResult third = service.processSession(ACTOR_KEY, 2000L);
        assertTrue(third.visualStageChanged());
        assertEquals(2, third.visualStage().value());
    }

    @Test
    void clearNonexistentSessionIsSafe() {
        MiningSessionService service = service();

        boolean cleared = service.clearSession(ACTOR_KEY);

        assertFalse(cleared);
    }

    @Test
    void processSessionForRuntimeReturnsUpdateWithVisualAction() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, new MiningHardness(50.0D), new MiningSpeed(10.0D), 1000L);

        MiningSessionService.MiningRuntimeUpdate update1 = service.processSessionForRuntime(ACTOR_KEY, 1500L);
        assertEquals(MiningSessionService.MiningRuntimeUpdate.VisualAction.UPDATE_STAGE, update1.visualAction());
        assertFalse(update1.completed());

        MiningSessionService.MiningRuntimeUpdate update2 = service.processSessionForRuntime(ACTOR_KEY, 1600L);
        assertEquals(MiningSessionService.MiningRuntimeUpdate.VisualAction.NONE, update2.visualAction());
        assertFalse(update2.completed());
    }

    @Test
    void processSessionForRuntimeClearsVisualOnCompletion() {
        MiningSessionService service = service();
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        MiningSessionService.MiningRuntimeUpdate update = service.processSessionForRuntime(ACTOR_KEY, 3000L);

        assertEquals(MiningSessionService.MiningRuntimeUpdate.VisualAction.CLEAR_VISUAL, update.visualAction());
        assertTrue(update.completed());
        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
    }

    private static MiningSessionService service() {
        return new MiningSessionService(new InMemoryMiningSessionRepository(), MiningDurationPolicy.DEFAULT);
    }
}
