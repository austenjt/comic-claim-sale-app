package org.example.functions.util;

import com.azure.core.util.Configuration;

import java.util.Optional;

public class EnvHelper {
    public static String getCosmosEndpoint() {
        return getProp("COSMOS_ENDPOINT");
    }

    public static String getAdminEmail() {
        return getProp("ADMIN_EMAIL");
    }

    public static boolean isGmailEnabled() {
        String val = getProp("GMAIL_ENABLED");
        return val == null || Boolean.parseBoolean(val); // defaults to true if not set
    }

    public static String getGmailUsername() {
        return getProp("GMAIL_USERNAME");
    }

    public static String getGmailClientId() {
        return getProp("GMAIL_CLIENT_ID");
    }

    public static String getGmailClientSecret() {
        return getProp("GMAIL_CLIENT_SECRET");
    }

    public static String getGmailRefreshToken() {
        return getProp("GMAIL_REFRESH_TOKEN");
    }

    static String getProp(String prop) {
        Optional<String> config = Optional.ofNullable(Configuration.getGlobalConfiguration().get(prop));
        return config.orElse(System.getenv(prop));
    }

}