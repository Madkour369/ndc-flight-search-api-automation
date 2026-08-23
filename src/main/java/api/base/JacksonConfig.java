package api.base;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.config.ObjectMapperConfig;

/**
 * The single shared Jackson configuration for every request/response the
 * framework serializes or deserializes. Case-insensitive property matching
 * is what lets one {@code ErrorResponse} POJO absorb both the PascalCase
 * error bodies returned by gateway/auth-layer failures and the camelCase
 * bodies returned by application-layer validation failures — see the
 * "Error contract" section of README.md (§1.4). This class has no
 * FlightSearch-specific knowledge; any module's models benefit from it.
 */
public final class JacksonConfig {

    private JacksonConfig() {
    }

    public static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        return mapper;
    }

    public static ObjectMapperConfig objectMapperConfig() {
        return new ObjectMapperConfig().jackson2ObjectMapperFactory((cls, charset) -> buildObjectMapper());
    }
}
