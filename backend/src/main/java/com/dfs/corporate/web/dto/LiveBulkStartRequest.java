package com.dfs.corporate.web.dto;

public class LiveBulkStartRequest {
    /** Required for FT bulk confirm; ignored for IBFT/UBP. Never stored in DB. */
    private String mpin;

    public String getMpin() { return mpin; }
    public void setMpin(String mpin) { this.mpin = mpin; }
}
