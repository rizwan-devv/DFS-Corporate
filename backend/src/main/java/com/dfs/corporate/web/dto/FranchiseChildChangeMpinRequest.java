package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class FranchiseChildChangeMpinRequest {
    @NotBlank
    private String currentMpin;
    @NotBlank
    private String newMpin;
    @NotBlank
    private String confirmMpin;

    public String getCurrentMpin() { return currentMpin; }
    public void setCurrentMpin(String currentMpin) { this.currentMpin = currentMpin; }
    public String getNewMpin() { return newMpin; }
    public void setNewMpin(String newMpin) { this.newMpin = newMpin; }
    public String getConfirmMpin() { return confirmMpin; }
    public void setConfirmMpin(String confirmMpin) { this.confirmMpin = confirmMpin; }
}
