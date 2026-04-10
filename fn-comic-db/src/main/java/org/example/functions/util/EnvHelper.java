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

    public static boolean isBiddingModeEnabled() {
        String val = getProp("BIDDING_MODE");
        return Boolean.parseBoolean(val != null ? val : "false");
    }

    public static int getBiddingCycleMins() {
        String val = getProp("BIDDING_CYCLE");
        if (val == null || val.isBlank()) return 10;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return 10; }
    }

    public static int getDashboardPageSize() {
        String val = getProp("DASHBOARD_PAGE_SIZE");
        if (val == null || val.isBlank()) return 500; // default: 500 items per page
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return 500; }
    }

    public static int getCartExpiryDays() {
        String val = getProp("CART_EXPIRY_DAYS");
        if (val == null || val.isBlank()) return 7;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return 7; }
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
