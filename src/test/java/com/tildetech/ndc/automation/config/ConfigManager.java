package com.tildetech.ndc.automation.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central point of access for environment configuration.
 * Values come from {@code config.properties} on the test classpath and can be
 * overridden per-run with a matching -D system property (useful for CI).
 */
public final class ConfigManager {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPERTIES = loadProperties();

    private ConfigManager() {
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = ConfigManager.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IllegalStateException(CONFIG_FILE + " not found on classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CONFIG_FILE, e);
        }
        return properties;
    }

    public static String get(String key) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        if (value == null) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value;
    }

    public static String baseUri() {
        return get("api.baseUri");
    }

    public static String searchPath() {
        return get("api.path");
    }

    /**
     * The real x-api-key is a secret and is intentionally kept out of
     * config.properties (and out of git). Supply it via -Dapi.key=... or the
     * NDC_API_KEY environment variable (a GitHub Actions secret in CI).
     */
    public static String apiKey() {
        String key = System.getProperty("api.key", System.getenv("NDC_API_KEY"));
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "No API key configured. Set it via -Dapi.key=<key> or the NDC_API_KEY environment variable.");
        }
        return key;
    }

    public static String invalidApiKey() {
        return get("api.invalidKey");
    }

    public static String clientId() {
        return get("api.clientId");
    }

    public static String defaultSupplier() {
        return get("api.supplier");
    }

    public static String defaultCredentialsSelector() {
        return get("api.credentialsSelector");
    }
}
