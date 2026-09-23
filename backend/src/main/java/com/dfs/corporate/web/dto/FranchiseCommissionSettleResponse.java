package com.dfs.corporate.web.dto;

public class FranchiseCommissionSettleResponse {
    private int scannedCredits;
    private int created;
    private int posted;
    private int failed;
    private int skipped;
    private String message;

    public int getScannedCredits() { return scannedCredits; }
    public void setScannedCredits(int scannedCredits) { this.scannedCredits = scannedCredits; }
    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
    public int getPosted() { return posted; }
    public void setPosted(int posted) { this.posted = posted; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
