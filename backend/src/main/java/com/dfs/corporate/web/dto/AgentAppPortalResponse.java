package com.dfs.corporate.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** AgentApp getbalance / miniStatment / changempin envelope forwarded to the portal UI. */
public class AgentAppPortalResponse {
    private String responsecode;
    private String messages;
    private JsonNode data;
    private Long childPartyId;
    private String childTrackingId;
    private String mobileNumber;
    private String accountLevelCode;

    public String getResponsecode() { return responsecode; }
    public void setResponsecode(String responsecode) { this.responsecode = responsecode; }
    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }
    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public String getChildTrackingId() { return childTrackingId; }
    public void setChildTrackingId(String childTrackingId) { this.childTrackingId = childTrackingId; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getAccountLevelCode() { return accountLevelCode; }
    public void setAccountLevelCode(String accountLevelCode) { this.accountLevelCode = accountLevelCode; }
}
