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

    public static boolean isAwardModeEnabled() {
        String val = getProp("AWARD_MODE_ENABLED");
        return val == null || Boolean.parseBoolean(val); // defaults to true if not set
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
