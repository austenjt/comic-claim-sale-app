package org.example.functions.model;

/** Represents the identity extracted from a validated Entra External ID JWT. */
public class UserIdentity {

    private final String oid;
    private final String email;
    private final String displayName;
    private final String tenantId;

    public UserIdentity(String oid, String email, String displayName, String tenantId) {
        this.oid = oid;
        this.email = email;
        this.displayName = displayName;
        this.tenantId = tenantId;
    }

    public String getOid()         { return oid; }
    public String getEmail()       { return email; }
    public String getDisplayName() { return displayName; }
    public String getTenantId()    { return tenantId; }
}
