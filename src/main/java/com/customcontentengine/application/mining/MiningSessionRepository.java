package com.customcontentengine.application.mining;

import com.customcontentengine.domain.mining.MiningSession;
import com.customcontentengine.internalapi.identity.WorldPosition;
import java.util.Optional;

public interface MiningSessionRepository {
    Optional<MiningSession> findByActorKey(String actorKey);

    void save(MiningSession session);

    Optional<MiningSession> remove(String actorKey);

    void clear();
}
