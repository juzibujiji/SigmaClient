package com.elfmcys.yesstevemodel;

public final class OpenYsmClientConfig {
    private boolean renderPlayers;
    private String selectedModelId = "misc/2_steve";
    private String selectedTextureId = "";

    public boolean isRenderPlayers() {
        return this.renderPlayers;
    }

    public void setRenderPlayers(boolean renderPlayers) {
        this.renderPlayers = renderPlayers;
    }

    public String getSelectedModelId() {
        return this.selectedModelId == null || this.selectedModelId.isEmpty() ? "misc/2_steve" : this.selectedModelId;
    }

    public void setSelectedModelId(String selectedModelId) {
        this.selectedModelId = selectedModelId;
    }

    public String getSelectedTextureId() {
        return this.selectedTextureId == null ? "" : this.selectedTextureId;
    }

    public void setSelectedTextureId(String selectedTextureId) {
        this.selectedTextureId = selectedTextureId == null ? "" : selectedTextureId;
    }
}
