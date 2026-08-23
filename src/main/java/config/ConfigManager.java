package config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central, endpoint-agnostic access point for runtime configuration: base
 * URL, the shared API key, and the Client-Id header. Every value can be
 * overridden per-run with a matching -D system property without editing
 * config.properties, so the same compiled framework works unmodified
 * across environments. No FlightSearch (or any other module) concept is
 * known here — see README.md §3.3.
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

    /**
     * Generic key lookup: -D system property first, then config.properties.
     * Any current or future module reads its own keys through this one
     * primitive instead of ConfigManager growing a new typed accessor per
     * module.
     */
    public static String get(String key) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        if (value == null) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value;
    }

    public static String getOrDefault(String key, String defaultValue) {
        return System.getProperty(key, PROPERTIES.getProperty(key, defaultValue));
    }

    public static String baseUri() {
        return get("api.baseUri");
    }

    /**
     * Client-Id isn't a secret in the way the API key is (it identifies the
     * calling application, not a credential), but it's still resolvable
     * from a GitHub Actions secret (NDC_CLIENT_ID) so CI can pin a
     * different tenant than local dev without touching config.properties.
     * <p>
     * Precedence is deliberately environment-variable-first: the
     * NDC_CLIENT_ID environment variable (how CI/CD injects a GitHub
     * Secret) wins over everything else, so a stray -D flag on a shared
     * build agent can never silently shadow the value CI actually intends.
     * -Dapi.clientId remains available as a local one-off override for
     * whenever no environment variable is set at all, falling back to the
     * config.properties default when neither is present.
     */
    public static String clientId() {
        String value = System.getenv("NDC_CLIENT_ID");
        if (value == null || value.isBlank()) {
            value = System.getProperty("api.clientId");
        }
        if (value == null || value.isBlank()) {
            value = PROPERTIES.getProperty("api.clientId");
        }
        if (value == null) {
            throw new IllegalStateException("Missing required configuration key: api.clientId");
        }
        return value;
    }

    /**
     * The API key is a secret and is deliberately never stored in
     * config.properties (or committed to source control) — unlike
     * {@link #clientId()}, there is no properties-file fallback here at
     * all, on purpose.
     * <p>
     * Precedence is environment-variable-first, same reasoning as
     * {@link #clientId()}: the NDC_API_KEY environment variable (a GitHub
     * Actions secret of the same name in CI) wins over -Dapi.key, which
     * remains available as a local one-off override.
     */
    public static String apiKey() {
        String key = System.getenv("NDC_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("api.key");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "No API key configured. Set it via the NDC_API_KEY environment variable or -Dapi.key=<key>.");
        }
        return key;
    }
}
