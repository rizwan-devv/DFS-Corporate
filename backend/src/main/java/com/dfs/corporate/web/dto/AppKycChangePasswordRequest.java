package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AppKycChangePasswordRequest {
    /** Current temporary PIN (or current password if already set). */
    @NotBlank
    private String currentPassword;
    @NotBlank
    @Size(min = 6, max = 64)
    private String newPassword;
    @NotBlank
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
