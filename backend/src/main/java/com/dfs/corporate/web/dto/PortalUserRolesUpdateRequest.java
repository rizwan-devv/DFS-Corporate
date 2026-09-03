package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class PortalUserRolesUpdateRequest {
    @NotEmpty
    private List<String> roles;

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
