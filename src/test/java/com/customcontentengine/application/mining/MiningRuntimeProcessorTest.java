package com.customcontentengine.application.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.customcontentengine.domain.mining.MiningDurationPolicy;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.MiningVisualPort;
import com.customcontentengine.port.SchedulerPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MiningRuntimeProcessorTest {
    private static final String ACTOR_KEY = "player-one";
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);
    private static final CustomItemId TOOL_ID = new CustomItemId("ruby_pickaxe");
    private static final MiningHardness HARDNESS = new MiningHardness(50.0D);
    private static final MiningSpeed SPEED = new MiningSpeed(10.0D);

    @Test
    void stageUpdateCallsVisualPortWhenStageChanges() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        MiningRuntimeProcessor processor = processor(service, visual);
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        int scheduled = processor.processActiveSessions(1500L, 8);

        assertEquals(1, scheduled);
        assertEquals(1, visual.updates.size());
        assertEquals(ACTOR_KEY, visual.updates.get(0).actorKey());
        assertEquals(TARGET, visual.updates.get(0).position());
        assertEquals(1, visual.updates.get(0).stageValue());
    }

    @Test
    void sameStageDoesNotUpdateVisualAgain() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        MiningRuntimeProcessor processor = processor(service, visual);
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);

        processor.processActiveSessions(1500L, 8);
        processor.processActiveSessions(1600L, 8);

        assertEquals(1, visual.updates.size());
    }

    @Test
    void completionOnlyClearsVisualForNow() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        MiningRuntimeProcessor processor = processor(service, visual);
        service.startSession(ACTOR_KEY, TARGET, TOOL_ID, new MiningHardness(2.0D), new MiningSpeed(1.0D), 1000L);

        processor.processActiveSessions(3000L, 8);

        assertEquals(List.of(ACTOR_KEY), visual.clears);
        assertFalse(service.getActiveSession(ACTOR_KEY).isPresent());
    }

    @Test
    void absenceOfSessionDoesNotTouchVisualPort() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        MiningRuntimeProcessor processor = processor(service, visual);

        MiningRuntimeUpdate update = processor.processSession(ACTOR_KEY, TARGET, 1500L);

        assertEquals(MiningRuntimeUpdate.VisualAction.NONE, update.visualAction());
        assertEquals(0, visual.updates.size());
        assertEquals(0, visual.clears.size());
    }

    @Test
    void processingIsLimitedByBudget() {
        MiningSessionService service = service();
        CapturingVisualPort visual = new CapturingVisualPort();
        MiningRuntimeProcessor processor = processor(service, visual);
        service.startSession("one", TARGET, TOOL_ID, HARDNESS, SPEED, 1000L);
        service.startSession("two", new WorldPosition("world", 11, 64, 10), TOOL_ID, HARDNESS, SPEED, 1000L);

        int scheduled = processor.processActiveSessions(1500L, 1);

        assertEquals(1, scheduled);
        assertEquals(1, visual.updates.size());
    }

    private static MiningRuntimeProcessor processor(MiningSessionService service, CapturingVisualPort visual) {
        return new MiningRuntimeProcessor(service, visual, new ImmediateScheduler());
    }

    private static MiningSessionService service() {
        return new MiningSessionService(new InMemoryMiningSessionRepository(), MiningDurationPolicy.DEFAULT);
    }

    private record VisualUpdate(String actorKey, WorldPosition position, int stageValue) {}

    private static final class CapturingVisualPort implements MiningVisualPort {
        private final List<VisualUpdate> updates = new ArrayList<>();
        private final List<String> clears = new ArrayList<>();

        @Override
        public void updateMiningStage(
                String actorKey,
                WorldPosition position,
                com.customcontentengine.domain.mining.MiningStage stage) {
            updates.add(new VisualUpdate(actorKey, position, stage.value()));
        }

        @Override
        public void clearMiningVisual(String actorKey) {
            clears.add(actorKey);
        }
    }

    private static final class ImmediateScheduler implements SchedulerPort {
        @Override
        public void runOnRegion(WorldPosition position, Runnable task) {
            task.run();
        }
    }
}
