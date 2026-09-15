package com.dfs.corporate.web.dto;

public class CmsCardSearchRequest {
    private Integer page = 0;
    private Integer size = 20;
    private String sort = "createdOn";
    private String sortDir = "desc";
    private String cardStatusCode;
    private String accountNumber;
    private String relationshipNum;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }
    public String getCardStatusCode() { return cardStatusCode; }
    public void setCardStatusCode(String cardStatusCode) { this.cardStatusCode = cardStatusCode; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getRelationshipNum() { return relationshipNum; }
    public void setRelationshipNum(String relationshipNum) { this.relationshipNum = relationshipNum; }
}
