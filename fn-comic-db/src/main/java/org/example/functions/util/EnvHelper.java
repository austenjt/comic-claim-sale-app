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

    public static boolean isEmailEnabled() {
        String val = getProp("EMAIL_ENABLED");
        return val == null || Boolean.parseBoolean(val); // defaults to true if not set
    }

    public static int getFinalizeHours() {
        String val = getProp("FINALIZE_HOURS");
        if (val == null || val.isBlank()) return 20; // default: 20 hours
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return 20; }
    }

    public static boolean isAwardModeEnabled() {
        String val = getProp("AWARD_MODE_ENABLED");
        return val == null || Boolean.parseBoolean(val); // defaults to true if not set
    }

    public static int getCartExpiryDays() {
        String val = getProp("CART_EXPIRY_DAYS");
        if (val == null || val.isBlank()) return 7;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return 7; }
    }

    /** Seller's 2-letter origin state for shipping zone estimation (default "TN"). */
    public static String getOriginState() {
        String val = getProp("ORIGIN_STATE");
        return (val != null && !val.isBlank()) ? val.trim().toUpperCase() : "TN";
    }

    public static String getSiteUrl() {
        String val = getProp("SITE_URL");
        return val != null ? val : "the site";
    }

    public static String getSmtpHost() {
        return getProp("SMTP_HOST");
    }

    public static String getSmtpUsername() {
        return getProp("SMTP_USERNAME");
    }

    public static String getSmtpPassword() {
        return getProp("SMTP_PASSWORD");
    }

    static String getProp(String prop) {
        Optional<String> config = Optional.ofNullable(Configuration.getGlobalConfiguration().get(prop));
        return config.orElse(System.getenv(prop));
    }

}
