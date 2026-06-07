package com.customcontentengine.application.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.customcontentengine.domain.mining.MiningDurationPolicy;
import com.customcontentengine.domain.mining.MiningHardness;
import com.customcontentengine.domain.mining.MiningSpeed;
import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.MiningCompletionPort;
import com.customcontentengine.port.MiningVisualPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningRuntimeUpdateTest {
    private static final String ACTOR_KEY = "player-one";
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);

    @Test
    void inProgressWithStageChangedReturnsUpdateStageAction() {
        var update = MiningRuntimeUpdate.inProgress(
                new com.customcontentengine.domain.mining.MiningProgress(0.1D),
                new com.customcontentengine.domain.mining.MiningStage(1),
                true);

        assertEquals(MiningRuntimeUpdate.VisualAction.UPDATE_STAGE, update.visualAction());
        assertFalse(update.completed());
    }

    @Test
    void inProgressWithoutStageChangedReturnsNoneAction() {
        var update = MiningRuntimeUpdate.inProgress(
                new com.customcontentengine.domain.mining.MiningProgress(0.15D),
                new com.customcontentengine.domain.mining.MiningStage(1),
                false);

        assertEquals(MiningRuntimeUpdate.VisualAction.NONE, update.visualAction());
        assertFalse(update.completed());
    }

    @Test
    void completedReturnsClearVisualAction() {
        var update = MiningRuntimeUpdate.completed(
                new com.customcontentengine.domain.mining.MiningProgress(1.0D),
                new com.customcontentengine.domain.mining.MiningStage(9),
                false);

        assertEquals(MiningRuntimeUpdate.VisualAction.CLEAR_VISUAL, update.visualAction());
        assertTrue(update.completed());
    }

    @Test
    void noSessionReturnsNoneActionAndNotCompleted() {
        var update = MiningRuntimeUpdate.noSession();

        assertEquals(MiningRuntimeUpdate.VisualAction.NONE, update.visualAction());
        assertFalse(update.completed());
        assertEquals(0.0D, update.progress().value(), 0.0D);
    }
}

class MiningVisualPortTest {
    private static final String ACTOR_KEY = "player-one";
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);

    @Test
    void capturingVisualPortTracksUpdates() {
        CapturingMiningVisualPort port = new CapturingMiningVisualPort();
        var stage = new com.customcontentengine.domain.mining.MiningStage(2);

        port.updateMiningStage(ACTOR_KEY, TARGET, stage);
        port.clearMiningVisual(ACTOR_KEY);

        assertEquals(1, port.updates.size());
        assertEquals(ACTOR_KEY, port.updates.get(0).actorKey);
        assertEquals(TARGET, port.updates.get(0).position);
        assertEquals(stage, port.updates.get(0).stage);
        assertEquals(1, port.clears.size());
        assertTrue(port.clears.contains(ACTOR_KEY));
    }
}

class MiningCompletionPortTest {
    private static final String ACTOR_KEY = "player-one";
    private static final WorldPosition TARGET = new WorldPosition("world", 10, 64, 10);
    private static final CustomItemId TOOL_ID = new CustomItemId("ruby_pickaxe");

    @Test
    void completionRequestContainsAllFields() {
        var request = new MiningCompletionPort.CompletionRequest(ACTOR_KEY, TARGET, TOOL_ID);

        assertEquals(ACTOR_KEY, request.actorKey());
        assertEquals(TARGET, request.position());
        assertEquals(TOOL_ID, request.toolId());
    }

    @Test
    void completionResultContainsStatusAndMessage() {
        var result = new MiningCompletionPort.CompletionResult(
                MiningCompletionPort.CompletionStatus.SUCCESS,
                "Completed");

        assertEquals(MiningCompletionPort.CompletionStatus.SUCCESS, result.status());
        assertEquals("Completed", result.message());
    }

    @Test
    void fakeCompletionPortNeverMutatesWorld() {
        FakeMiningCompletionPort port = new FakeMiningCompletionPort();
        var request = new MiningCompletionPort.CompletionRequest(ACTOR_KEY, TARGET, TOOL_ID);

        var result = port.complete(request);

        assertEquals(MiningCompletionPort.CompletionStatus.SUCCESS, result.status());
        assertEquals(0, port.mutationCount);
    }
}

class CapturingMiningVisualPort implements MiningVisualPort {
    record Update(String actorKey, WorldPosition position, com.customcontentengine.domain.mining.MiningStage stage) {}

    List<Update> updates = new ArrayList<>();
    Set<String> clears = new HashSet<>();

    @Override
    public void updateMiningStage(
            String actorKey, WorldPosition position, com.customcontentengine.domain.mining.MiningStage stage) {
        updates.add(new Update(actorKey, position, stage));
    }

    @Override
    public void clearMiningVisual(String actorKey) {
        clears.add(actorKey);
    }
}

class FakeMiningCompletionPort implements MiningCompletionPort {
    int mutationCount = 0;

    @Override
    public CompletionResult complete(CompletionRequest request) {
        return new CompletionResult(CompletionStatus.SUCCESS, "Fake completion, no mutations.");
    }
}
