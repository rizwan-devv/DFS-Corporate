package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.AssociatedPersonRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PartnerInviteCreateRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String fullName;
    @NotNull
    private AssociatedPersonRole roleType = AssociatedPersonRole.PARTNER;
    private Boolean authorizedToOperate = false;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public AssociatedPersonRole getRoleType() { return roleType; }
    public void setRoleType(AssociatedPersonRole roleType) { this.roleType = roleType; }
    public Boolean getAuthorizedToOperate() { return authorizedToOperate; }
    public void setAuthorizedToOperate(Boolean authorizedToOperate) { this.authorizedToOperate = authorizedToOperate; }
}
