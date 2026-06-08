package com.customcontentengine.application.mining;

import com.customcontentengine.domain.mining.MiningSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryMiningSessionRepository implements MiningSessionRepository {
    private final Map<String, MiningSession> sessions = new HashMap<>();

    @Override
    public Optional<MiningSession> findByActorKey(String actorKey) {
        Objects.requireNonNull(actorKey, "actorKey");
        return Optional.ofNullable(sessions.get(actorKey));
    }

    @Override
    public List<MiningSession> findAll() {
        return List.copyOf(sessions.values());
    }

    @Override
    public void save(MiningSession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.actorKey(), session);
    }

    @Override
    public Optional<MiningSession> remove(String actorKey) {
        Objects.requireNonNull(actorKey, "actorKey");
        return Optional.ofNullable(sessions.remove(actorKey));
    }

    @Override
    public void clear() {
        sessions.clear();
    }
}
