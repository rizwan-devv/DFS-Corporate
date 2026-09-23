package com.dfs.corporate.web.dto;

public class FranchiseCommissionSettleRequest {
    private Long childPartyId;
    private String mpin;

    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public String getMpin() { return mpin; }
    public void setMpin(String mpin) { this.mpin = mpin; }
}
