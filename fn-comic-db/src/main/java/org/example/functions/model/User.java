package org.example.functions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    private String id;
    private String name;
    private String email;
    private String address;
    private String phone;
    private String notes;
    private String preferences;
    private String venmoHandle;
    private String paypalHandle;
    private String ebayUsername;
    private String cashAppHandle;
    private String status; // "PENDING" | "APPROVED"
    private String pinHash;
    private String pinSalt;
    private String createdDate;
    private String approvedDate;
    @JsonProperty("isAdmin")
    private boolean isAdmin;
    private int failedLoginAttempts;
    private String lockedUntil; // ISO-8601 timestamp; null means not locked

    public User() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }

    public String getVenmoHandle() { return venmoHandle; }
    public void setVenmoHandle(String venmoHandle) { this.venmoHandle = venmoHandle; }

    public String getPaypalHandle() { return paypalHandle; }
    public void setPaypalHandle(String paypalHandle) { this.paypalHandle = paypalHandle; }

    public String getEbayUsername() { return ebayUsername; }
    public void setEbayUsername(String ebayUsername) { this.ebayUsername = ebayUsername; }

    public String getCashAppHandle() { return cashAppHandle; }
    public void setCashAppHandle(String cashAppHandle) { this.cashAppHandle = cashAppHandle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getPinSalt() { return pinSalt; }
    public void setPinSalt(String pinSalt) { this.pinSalt = pinSalt; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public String getApprovedDate() { return approvedDate; }
    public void setApprovedDate(String approvedDate) { this.approvedDate = approvedDate; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public String getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(String lockedUntil) { this.lockedUntil = lockedUntil; }
}
