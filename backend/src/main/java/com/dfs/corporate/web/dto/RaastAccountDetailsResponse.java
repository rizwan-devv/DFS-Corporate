package com.dfs.corporate.web.dto;

import java.math.BigDecimal;

/** Live DFS accountDetails for Raast QR display. */
public class RaastAccountDetailsResponse {
    private String responsecode;
    private String messages;
    private String accountNo;
    private String mobileNo;
    private String nidNo;
    private String gender;
    private String segmentDescr;
    private String iban;
    private String qrCode;
    private String accountTitle;
    private BigDecimal currentBalance;
    private String accountStatusDescr;

    public String getResponsecode() { return responsecode; }
    public void setResponsecode(String responsecode) { this.responsecode = responsecode; }
    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getMobileNo() { return mobileNo; }
    public void setMobileNo(String mobileNo) { this.mobileNo = mobileNo; }
    public String getNidNo() { return nidNo; }
    public void setNidNo(String nidNo) { this.nidNo = nidNo; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getSegmentDescr() { return segmentDescr; }
    public void setSegmentDescr(String segmentDescr) { this.segmentDescr = segmentDescr; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public String getAccountTitle() { return accountTitle; }
    public void setAccountTitle(String accountTitle) { this.accountTitle = accountTitle; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public String getAccountStatusDescr() { return accountStatusDescr; }
    public void setAccountStatusDescr(String accountStatusDescr) { this.accountStatusDescr = accountStatusDescr; }
}
