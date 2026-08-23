package api.base;

import io.restassured.response.Response;
import models.common.ErrorResponse;

/**
 * Generic response-handling utility: status-code verification plus
 * deserialization into either a success POJO or the shared
 * {@link ErrorResponse} envelope. No endpoint-specific logic lives here —
 * a module's tests call through this instead of repeating
 * {@code .statusCode(x).extract().response()} + manual {@code .as(...)} in
 * every test method.
 */
public final class ResponseHandler {

    private ResponseHandler() {
    }

    /**
     * Asserts the response's status code, then deserializes the body into
     * {@code responseType}. Use for the success path.
     */
    public static <T> T expectSuccess(Response response, int expectedStatusCode, Class<T> responseType) {
        assertStatusCode(response, expectedStatusCode);
        return response.as(responseType);
    }

    /**
     * Asserts the response's status code, then deserializes the body into
     * the shared {@link ErrorResponse} envelope. Handles both the
     * PascalCase (gateway/auth layer) and camelCase (application layer)
     * error bodies transparently via the case-insensitive ObjectMapper
     * configured in {@link JacksonConfig}.
     */
    public static ErrorResponse expectError(Response response, int expectedStatusCode) {
        assertStatusCode(response, expectedStatusCode);
        return response.as(ErrorResponse.class);
    }

    public static void assertStatusCode(Response response, int expectedStatusCode) {
        int actual = response.statusCode();
        if (actual != expectedStatusCode) {
            throw new AssertionError(String.format(
                    "Expected status code %d but got %d.%nResponse body: %s",
                    expectedStatusCode, actual, response.asPrettyString()));
        }
    }
}
