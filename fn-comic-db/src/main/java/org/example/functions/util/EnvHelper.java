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

    static String getProp(String prop) {
        Optional<String> config = Optional.ofNullable(Configuration.getGlobalConfiguration().get(prop));
        return config.orElse(System.getenv(prop));
    }

}