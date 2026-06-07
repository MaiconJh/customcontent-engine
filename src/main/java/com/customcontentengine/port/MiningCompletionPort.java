package com.customcontentengine.port;

import com.customcontentengine.internalapi.identity.CustomItemId;
import com.customcontentengine.internalapi.identity.WorldPosition;

public interface MiningCompletionPort {
    record CompletionRequest(
            String actorKey,
            WorldPosition position,
            CustomItemId toolId) {}

    CompletionResult complete(CompletionRequest request);

    enum CompletionStatus {
        SUCCESS,
        POSITION_NOT_CUSTOM_BLOCK,
        TOOL_MISMATCH,
        REGION_UNSAFE,
        FAILED
    }

    record CompletionResult(
            CompletionStatus status,
            String message) {}
}
