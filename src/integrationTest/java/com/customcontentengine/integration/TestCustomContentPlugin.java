package com.customcontentengine.integration;

import com.customcontentengine.application.mechanic.PlayerPreferenceService;
import com.customcontentengine.bootstrap.CustomContentPlugin;
import com.customcontentengine.port.ProtectionPort;
import com.customcontentengine.port.ToolWearPort;

public final class TestCustomContentPlugin extends CustomContentPlugin {
    private ToolWearPort toolWearPort;
    private PlayerPreferenceService playerPreferenceService;

    @Override
    public void onEnable() {
        getCommand("testprotection").setExecutor(new TestProtectionCommandAdapter());
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
