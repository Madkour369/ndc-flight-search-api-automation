package api.base;

import config.ConfigManager;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized {@link RequestSpecification} factory. Deliberately knows
 * nothing about any specific endpoint — no resource path, no request body
 * concerns — only the transport-level concerns every endpoint shares: base
 * URI, default headers, content type, the shared Jackson config, and
 * logging/reporting filters. An endpoint wrapper (e.g. FlightSearchApi)
 * supplies its own resource path when it calls {@link BaseApiClient}. A
 * future Booking/SeatMap module reuses this factory unchanged.
 */
public final class RequestSpecFactory {

    public static final String HEADER_API_KEY = "x-api-key";
    public static final String HEADER_CLIENT_ID = "Client-Id";

    private RequestSpecFactory() {
    }

    /** Full happy-path spec: valid base URI, valid API key, valid Client-Id. */
    public static RequestSpecification defaultSpec() {
        return buildSpec(defaultHeaders());
    }

    /**
     * The default headers with a single header added or overridden — for
     * exercising one bad-value scenario (e.g. an invalid x-api-key) without
     * disturbing every other default.
     */
    public static RequestSpecification specWithHeader(String name, String value) {
        Map<String, String> headers = defaultHeaders();
        headers.put(name, value);
        return buildSpec(headers);
    }

    /**
     * The default headers with one header removed entirely — for
     * "header is absent" scenarios, distinct from "header has a bad value".
     * Comparison is case-insensitive since HTTP header names are.
     */
    public static RequestSpecification specWithoutHeader(String name) {
        Map<String, String> headers = defaultHeaders();
        headers.keySet().removeIf(key -> key.equalsIgnoreCase(name));
        return buildSpec(headers);
    }

    /**
     * A fully caller-supplied header set, replacing the defaults entirely.
     * Use for multi-header negative scenarios (e.g. both auth headers
     * missing at once) rather than chaining specWithoutHeader calls.
     */
    public static RequestSpecification specWithHeaders(Map<String, String> headers) {
        return buildSpec(headers);
    }

    private static Map<String, String> defaultHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_API_KEY, ConfigManager.apiKey());
        headers.put(HEADER_CLIENT_ID, ConfigManager.clientId());
        return headers;
    }

    private static RequestSpecification buildSpec(Map<String, String> headers) {
        // Content-Type is handled separately from the rest of the headers:
        // RestAssured tracks it as a distinct builder field, so passing it
        // through addHeaders() as well would risk sending it twice. Pulling
        // it out here lets a caller override Content-Type (e.g. to exercise
        // a 415 Unsupported Media Type scenario) via the same generic
        // specWithHeader("Content-Type", ...) call used for every other
        // header, instead of needing a dedicated method.
        Map<String, String> remainingHeaders = new LinkedHashMap<>(headers);
        String contentTypeOverride = extractCaseInsensitive(remainingHeaders, "Content-Type");

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(ConfigManager.baseUri())
                .addHeaders(remainingHeaders)
                .setConfig(RestAssuredConfig.config().objectMapperConfig(JacksonConfig.objectMapperConfig()))
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .addFilter(new AllureRestAssured());

        if (contentTypeOverride != null) {
            builder.setContentType(contentTypeOverride);
        } else {
            builder.setContentType(ContentType.JSON);
        }
        return builder.build();
    }

    private static String extractCaseInsensitive(Map<String, String> headers, String name) {
        String matchedKey = null;
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                matchedKey = key;
                break;
            }
        }
        return matchedKey == null ? null : headers.remove(matchedKey);
    }
}
