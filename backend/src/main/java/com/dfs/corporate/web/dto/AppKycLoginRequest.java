package com.dfs.corporate.web.dto;

/** Login with phone+temp PIN (invite) or email+password (after force change). */
public class AppKycLoginRequest {
    private String phone;
    private String pin;
    private String email;
    private String password;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
