package com.customcontentengine.integration;

import com.customcontentengine.application.mechanic.PlayerPreferenceService;
import com.customcontentengine.bootstrap.CustomContentPlugin;
import com.customcontentengine.port.ProtectionPort;
import com.customcontentengine.port.ToolWearPort;

/**
 * Test variant of {@link CustomContentPlugin} used by integration tests that need to replace
 * production dependencies with fakes or mocks (ADR-0013 / TEST_INTEGRATION_PLAN.md Phase 4).
 *
 * <p>Subclasses call the {@code set*} methods and then delegate to {@link #onEnable()}, which
 * installs the overrides before the production composition runs. When no override is set, the
 * production default is used, so behavior is identical to the real plugin.</p>
 *
 * <p>This class is intended to be packaged as a separate test plugin (distinct {@code plugin.yml}
 * with {@code main} pointing here) and loaded by the Paper server instead of the production jar
 * for the phases that require injection.</p>
 */
public final class TestCustomContentPlugin extends CustomContentPlugin {
    private ProtectionPort protectionPort;
    private ToolWearPort toolWearPort;
    private PlayerPreferenceService playerPreferenceService;

    @Override
    public void onEnable() {
        this.protectionPort = protectionPort;
        this.toolWearOverride = toolWearPort;
        this.playerPreferenceServiceOverride = playerPreferenceService;
        super.onEnable();
    }

    public void setProtectionPort(ProtectionPort protectionPort) {
        this.protectionPort = protectionPort;
    }

    public void setToolWearPort(ToolWearPort toolWearPort) {
        this.toolWearPort = toolWearPort;
    }

    public void setPlayerPreferenceService(PlayerPreferenceService playerPreferenceService) {
        this.playerPreferenceService = playerPreferenceService;
    }
}
