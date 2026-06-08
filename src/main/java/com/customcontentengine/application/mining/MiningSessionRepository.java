package com.customcontentengine.application.mining;

import com.customcontentengine.domain.mining.MiningSession;
import java.util.List;
import java.util.Optional;

public interface MiningSessionRepository {
    Optional<MiningSession> findByActorKey(String actorKey);

    List<MiningSession> findAll();

    void save(MiningSession session);

    Optional<MiningSession> remove(String actorKey);

    void clear();
}
