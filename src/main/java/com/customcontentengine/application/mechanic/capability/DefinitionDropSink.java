package com.customcontentengine.application.mechanic.capability;

import com.customcontentengine.domain.definition.BlockDef;
import com.customcontentengine.domain.registry.DefinitionRegistry;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.internalapi.mechanic.capability.DropSink;
import com.customcontentengine.port.DropPort;
import java.util.Objects;

public final class DefinitionDropSink implements DropSink {
    private final DefinitionRegistry definitions;
    private final DropPort dropPort;

    public DefinitionDropSink(DefinitionRegistry definitions, DropPort dropPort) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.dropPort = Objects.requireNonNull(dropPort, "dropPort");
    }

    @Override
    public void dropFor(WorldPosition position, short numericId) {
        definitions.findBlockByNumericId(numericId)
                .map(BlockDef::drops)
                .ifPresent(drops -> dropPort.drop(position, drops));
    }
}
