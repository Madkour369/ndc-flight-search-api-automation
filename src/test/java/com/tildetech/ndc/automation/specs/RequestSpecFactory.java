package com.tildetech.ndc.automation.specs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tildetech.ndc.automation.config.ConfigManager;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Central factory for RestAssured {@link RequestSpecification} instances.
 * Encapsulates base URI, base path, standard headers, the Jackson
 * ObjectMapper, and request/response logging so individual tests never touch
 * that plumbing directly.
 */
public final class RequestSpecFactory {

    private RequestSpecFactory() {
    }

    /**
     * Full happy-path spec: valid base URI, valid x-api-key, valid Client-Id.
     */
    public static RequestSpecification defaultSpec() {
        return baseSpecBuilder()
                .addHeader("x-api-key", ConfigManager.apiKey())
                .build();
    }

    /**
     * Same as {@link #defaultSpec()} but with a caller-supplied x-api-key,
     * for exercising authentication failure scenarios.
     */
    public static RequestSpecification specWithApiKey(String apiKey) {
        return baseSpecBuilder()
                .addHeader("x-api-key", apiKey)
                .build();
    }

    private static RequestSpecBuilder baseSpecBuilder() {
        return new RequestSpecBuilder()
                .setBaseUri(ConfigManager.baseUri())
                .setBasePath(ConfigManager.searchPath())
                .setContentType(ContentType.JSON)
                .addHeader("Client-Id", ConfigManager.clientId())
                .setConfig(RestAssuredConfig.config().objectMapperConfig(objectMapperConfig()))
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .addFilter(new AllureRestAssured());
    }

    private static ObjectMapperConfig objectMapperConfig() {
        return new ObjectMapperConfig().jackson2ObjectMapperFactory((cls, charset) -> buildObjectMapper());
    }

    public static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        return mapper;
    }
}
