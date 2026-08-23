package models.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Shared error envelope for every endpoint on this gateway. Covers both
 * observed error shapes: the gateway/auth layer returns PascalCase fields
 * (e.g. "Status", "ErrorMessage") on 401s/409s, while the application
 * validation layer returns camelCase fields on 400s. Both map cleanly onto
 * this one POJO because the shared ObjectMapper
 * ({@link api.base.JacksonConfig}) enables case-insensitive property
 * matching — see README.md §1.4. Endpoint-agnostic: any future module
 * reuses this class as-is.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {

    Integer status;
    String transactionId;
    String errorCode;
    String errorMessage;
    List<ErrorContext> context;
    String originalSupplierRequest;
    String originalSupplierResponse;
}
