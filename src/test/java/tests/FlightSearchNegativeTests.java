package tests;

import api.base.RequestSpecFactory;
import api.base.ResponseHandler;
import base.BaseTest;
import config.ConfigManager;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import models.common.ErrorContext;
import models.common.ErrorResponse;
import models.request.FlightSearchRequest;
import org.testng.annotations.Test;
import providers.FlightSearchDataProvider;

import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Epic("NDC Supplier Integration")
@Feature("Flight Search")
public class FlightSearchNegativeTests extends BaseTest {

    // -------------------------------------------------------------------
    // Request validation (400)
    // -------------------------------------------------------------------

    @Test(dataProvider = "missingRequiredFieldScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "Requests missing a required top-level field are rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_FLD_001-003.")
    public void missingRequiredFieldReturns400(String scenario, FlightSearchRequest request, String expectedFieldToken) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextReferences(error, expectedFieldToken, scenario);
    }

    @Test(dataProvider = "emptyRequiredArrayScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "An empty searchCriteria/passengers array is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_FLD_005-006.")
    public void emptyRequiredArrayReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(dataProvider = "missingSearchCriteriaFieldScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "A searchCriteria entry missing origin/destination/date is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_FLD_007, TC_FLD_008, TC_DATE_008.")
    public void missingSearchCriteriaFieldReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(dataProvider = "missingPassengerFieldScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "A passengers entry missing passengerTypeCode/count is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_FLD_009-010.")
    public void missingPassengerFieldReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(description = "A blank supplier value is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_FLD_014.")
    public void blankSupplierReturns400() {
        Response response = search(FlightSearchDataProvider.blankSupplierRequest());
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextReferences(error, "Supplier", "Blank supplier");
    }

    @Test(description = "A completely empty request body ({}) is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_FLD_011.")
    public void emptyRequestBodyReturns400() {
        Response response = search(FlightSearchRequest.builder().build());
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, "Empty request body");
    }

    @Test(dataProvider = "invalidDateScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "Malformed or out-of-range date values are rejected with 400")
    @Story("Date validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_DATE_001, 002, 005, 006, 007, 009, 011, 013, 016, 017.")
    public void invalidDateFormatReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(dataProvider = "invalidPassengerCountScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "A zero or negative passenger count is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_TYPE_002-003.")
    public void invalidPassengerCountReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(description = "An unrecognized passengerTypeCode is rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_TYPE_006.")
    public void invalidPassengerTypeCodeReturns400() {
        Response response = search(FlightSearchDataProvider.invalidPassengerTypeCodeRequest());
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, "Invalid passengerTypeCode");
    }

    @Test(dataProvider = "maliciousOrOversizedFieldValueScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "Injection-style or oversized string values are rejected with 400, never a 500")
    @Story("Security & injection hardening")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_TYPE_016-017, TC_SEC_001-003.")
    public void maliciousOrOversizedFieldValueReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(dataProvider = "invalidAirportCodeScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "Malformed or nonexistent airport codes are rejected with 400")
    @Story("Airport code validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_APT_001-007, TC_APT_009.")
    public void invalidAirportCodeReturns400(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 400);
        assertContextNonEmpty(error, scenario);
    }

    @Test(dataProvider = "malformedOrTypeMismatchedPayloadScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "Structurally wrong JSON (wrong field types, wrong container shapes, syntax errors) is rejected with 400, never a 500")
    @Story("Request validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_TYPE_001, 004, 008, 011-014, 019; TC_FLD_013; TC_DATE_010. Sent as raw JSON since these "
            + "shapes can't be represented by the strongly-typed request POJOs. Only the status code is "
            + "asserted here -- unlike the other 400 groups, the exact error-body shape for a JSON parse "
            + "failure hasn't been verified and shouldn't be guessed at.")
    public void malformedOrTypeMismatchedPayloadReturns400(String scenario, String rawJsonBody) {
        Response response = searchRaw(rawJsonBody);
        ResponseHandler.assertStatusCode(response, 400);
    }

    // -------------------------------------------------------------------
    // Business rule validation (409)
    // -------------------------------------------------------------------

    @Test(dataProvider = "multiCityScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "True multi-city itineraries (3+ distinct cities) are rejected with 409 for this supplier")
    @Story("Business rule validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_BIZ_001-002 -- flyadeal supports one-way/round-trip only.")
    public void multiCityItineraryReturns409(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        ErrorResponse error = ResponseHandler.expectError(response, 409);
        assertTrue(error.getErrorCode() != null && error.getErrorCode().equalsIgnoreCase("ValidationError"),
                "[" + scenario + "] errorCode should be ValidationError");
        assertContextReferences(error, "multicity", scenario);
    }

    // -------------------------------------------------------------------
    // Content negotiation / unsupported methods (405 / 415)
    // -------------------------------------------------------------------

    @Test(description = "GET is not a supported method on the FlightSearch resource")
    @Story("Content negotiation")
    @Severity(SeverityLevel.MINOR)
    @Description("TC_STRUCT_001.")
    public void searchViaGetReturns405() {
        Response response = searchViaGet();
        ResponseHandler.assertStatusCode(response, 405);
    }

    @Test(description = "PUT is not a supported method on the FlightSearch resource")
    @Story("Content negotiation")
    @Severity(SeverityLevel.MINOR)
    @Description("TC_STRUCT_002.")
    public void searchViaPutReturns405() {
        Response response = searchViaPut(FlightSearchDataProvider.validOneWayRequest());
        ResponseHandler.assertStatusCode(response, 405);
    }

    @Test(description = "A non-JSON Content-Type is rejected with 415")
    @Story("Content negotiation")
    @Severity(SeverityLevel.MINOR)
    @Description("TC_AUTH_011.")
    public void incorrectContentTypeReturns415() {
        RequestSpecification spec = RequestSpecFactory.specWithHeader("Content-Type", "text/plain");
        Response response = search(FlightSearchDataProvider.validOneWayRequest(), spec);
        ResponseHandler.assertStatusCode(response, 415);
    }

    // -------------------------------------------------------------------
    // Authentication failures (401)
    // -------------------------------------------------------------------

    @Test(dataProvider = "invalidOrEmptyApiKeyScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "An invalid or empty x-api-key is rejected with 401, and the real key is never echoed back")
    @Story("Authentication failures")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_AUTH_001, TC_AUTH_003, plus the TC_SEC_004 no-echo check folded into the same call.")
    public void invalidOrEmptyApiKeyReturns401(String scenario, String apiKeyValue) {
        RequestSpecification spec = RequestSpecFactory.specWithHeader(RequestSpecFactory.HEADER_API_KEY, apiKeyValue);
        Response response = search(FlightSearchDataProvider.validOneWayRequest(), spec);

        ErrorResponse error = ResponseHandler.expectError(response, 401);
        assertTrue(error.getErrorCode() != null && error.getErrorCode().equalsIgnoreCase("AuthenticationError"),
                "[" + scenario + "] errorCode should be AuthenticationError");
        assertTrue(error.getErrorMessage() != null && error.getErrorMessage().toLowerCase().contains("credentials"),
                "[" + scenario + "] errorMessage should mention credentials");

        String realApiKey = ConfigManager.apiKey();
        assertFalse(response.asString().contains(realApiKey),
                "[" + scenario + "] the real x-api-key must never be echoed back in the error body (TC_SEC_004)");
    }

    @Test(description = "Both auth headers missing at once are rejected with 401")
    @Story("Authentication failures")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_AUTH_008.")
    public void missingAuthHeadersReturns401() {
        RequestSpecification spec = RequestSpecFactory.specWithHeaders(Map.of());
        Response response = search(FlightSearchDataProvider.validOneWayRequest(), spec);
        ResponseHandler.assertStatusCode(response, 401);
    }

    @Test(description = "A valid x-api-key sent only as a query parameter (not a header) is rejected")
    @Story("Authentication failures")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_AUTH_012 -- proves the key is accepted via header only, never via the URL.")
    public void apiKeyViaQueryParamIsRejected() {
        RequestSpecification withoutApiKeyHeader = RequestSpecFactory.specWithoutHeader(RequestSpecFactory.HEADER_API_KEY);
        RequestSpecification withApiKeyAsQueryParam = new RequestSpecBuilder()
                .addRequestSpecification(withoutApiKeyHeader)
                .addQueryParam(RequestSpecFactory.HEADER_API_KEY, ConfigManager.apiKey())
                .build();

        Response response = search(FlightSearchDataProvider.validOneWayRequest(), withApiKeyAsQueryParam);
        ResponseHandler.assertStatusCode(response, 401);
    }

    // -------------------------------------------------------------------
    // Shared assertions
    // -------------------------------------------------------------------

    private void assertContextNonEmpty(ErrorResponse error, String scenario) {
        assertNotNull(error.getContext(), "[" + scenario + "] context should be present");
        assertFalse(error.getContext().isEmpty(), "[" + scenario + "] context should be non-empty");
    }

    private void assertContextReferences(ErrorResponse error, String token, String scenario) {
        assertContextNonEmpty(error, scenario);
        boolean matched = error.getContext().stream().anyMatch(ctx -> matches(ctx, token));
        assertTrue(matched, "[" + scenario + "] context should reference '" + token + "'");
    }

    private static boolean matches(ErrorContext ctx, String token) {
        return containsIgnoreCase(ctx.getName(), token) || containsIgnoreCase(ctx.getValue(), token);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
