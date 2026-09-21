package com.dfs.corporate.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope for DFS portal txn/app responses forwarded to the UI.
 * Always inspect {@link #responsecode} ({@code 000} = success).
 */
public class DfsPortalTxnResponse {
    private String responsecode;
    private String messages;
    private JsonNode data;
    private JsonNode raw;
    private String fromAccountNo;
    private String product;

    public static DfsPortalTxnResponse from(JsonNode root) {
        DfsPortalTxnResponse r = new DfsPortalTxnResponse();
        r.raw = root;
        if (root == null) {
            return r;
        }
        // Some DFS endpoints return a bare array (bankList / getbiller)
        if (root.isArray()) {
            r.responsecode = "000";
            r.messages = "SUCCESS";
            r.data = root;
            return r;
        }
        if (root.has("responsecode")) {
            r.responsecode = root.get("responsecode").asText(null);
        } else if (root.has("responseCode")) {
            r.responsecode = root.get("responseCode").asText(null);
        } else {
            r.responsecode = "000";
        }
        if (root.has("messages")) {
            r.messages = root.get("messages").asText(null);
        } else if (root.has("message")) {
            r.messages = root.get("message").asText(null);
        }
        if (root.has("data")) {
            r.data = root.get("data");
        } else if (!root.has("responsecode") && !root.has("responseCode")) {
            r.data = root;
        }
        return r;
    }

    public String getResponsecode() { return responsecode; }
    public void setResponsecode(String responsecode) { this.responsecode = responsecode; }
    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }
    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }
    public JsonNode getRaw() { return raw; }
    public void setRaw(JsonNode raw) { this.raw = raw; }
    public String getFromAccountNo() { return fromAccountNo; }
    public void setFromAccountNo(String fromAccountNo) { this.fromAccountNo = fromAccountNo; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
}
