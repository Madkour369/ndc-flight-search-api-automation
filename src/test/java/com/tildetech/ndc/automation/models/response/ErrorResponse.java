package com.tildetech.ndc.automation.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Covers both error shapes observed from the API: the gateway/auth layer
 * returns PascalCase fields (e.g. "Status", "ErrorMessage") on 401s, while the
 * application validation layer returns camelCase fields on 400s. Both map
 * cleanly onto this POJO because the shared ObjectMapper (see
 * RequestSpecFactory) enables case-insensitive property matching.
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
