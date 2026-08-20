package com.tildetech.ndc.automation.tests;

import com.tildetech.ndc.automation.base.BaseTest;
import com.tildetech.ndc.automation.config.ConfigManager;
import com.tildetech.ndc.automation.models.request.FlightSearchRequest;
import com.tildetech.ndc.automation.models.response.ErrorResponse;
import com.tildetech.ndc.automation.providers.FlightSearchDataProvider;
import com.tildetech.ndc.automation.specs.RequestSpecFactory;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("NDC Supplier Integration")
@Feature("Flight Search")
public class FlightSearchNegativeTests extends BaseTest {

    @Test(description = "An invalid x-api-key is rejected with 401 Unauthorized")
    @Story("Authentication failures")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Sends a well-formed payload with a bogus x-api-key and expects a 401 with an AuthenticationError body.")
    public void invalidApiKeyReturns401() {
        FlightSearchRequest request = FlightSearchDataProvider.validOneWayRequest();
        RequestSpecification invalidKeySpec = RequestSpecFactory.specWithApiKey(ConfigManager.invalidApiKey());

        Response response = given()
                .spec(invalidKeySpec)
                .body(request)
                .when()
                .post()
                .then()
                .statusCode(401)
                .extract().response();

        ErrorResponse error = response.as(ErrorResponse.class);

        assertThat(error.getStatus()).isEqualTo(401);
        assertThat(error.getErrorCode()).isEqualToIgnoringCase("AuthenticationError");
        assertThat(error.getErrorMessage()).containsIgnoringCase("credentials");
    }

    @Test(dataProvider = "missingRequiredFieldScenarios",
            dataProviderClass = FlightSearchDataProvider.class,
            description = "Requests missing a required top-level field are rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Omits a required field (searchCriteria, passengers, or supplier) and expects a 400 whose error context names the missing field.")
    public void missingRequiredFieldReturns400(String scenario, FlightSearchRequest request, String expectedFieldToken) {
        Response response = given()
                .spec(requestSpec)
                .body(request)
                .when()
                .post()
                .then()
                .statusCode(400)
                .extract().response();

        ErrorResponse error = response.as(ErrorResponse.class);

        assertThat(error.getStatus())
                .as("[%s] status field in error body should be 400", scenario)
                .isEqualTo(400);

        assertThat(error.getContext())
                .as("[%s] validation context should be present", scenario)
                .isNotEmpty()
                .anySatisfy(ctx -> assertThat(ctx.getName() + " " + ctx.getValue())
                        .as("[%s] error context should reference the missing field", scenario)
                        .containsIgnoringCase(expectedFieldToken));
    }

    @Test(description = "True multi-city itineraries (3+ distinct cities) are rejected with 409 for suppliers that don't support them")
    @Story("Business rule validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("flyadeal only supports one-way and round-trip itineraries; a genuine multi-city request (CAI-RUH, RUH-JED) must be rejected with 409 rather than silently accepted.")
    public void multiCityItineraryReturns409() {
        FlightSearchRequest request = FlightSearchDataProvider.multiCityRequest();

        Response response = given()
                .spec(requestSpec)
                .body(request)
                .when()
                .post()
                .then()
                .statusCode(409)
                .extract().response();

        ErrorResponse error = response.as(ErrorResponse.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getErrorCode()).isEqualToIgnoringCase("ValidationError");
        assertThat(error.getContext())
                .isNotEmpty()
                .anySatisfy(ctx -> assertThat(ctx.getValue()).containsIgnoringCase("multicity"));
    }

    @Test(dataProvider = "invalidDateFormatScenarios",
            dataProviderClass = FlightSearchDataProvider.class,
            description = "Malformed date values in search criteria are rejected with 400")
    @Story("Request validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submits a searchCriteria.date value that cannot be parsed as a date and expects a 400 response.")
    public void invalidDateFormatReturns400(String scenario, FlightSearchRequest request) {
        Response response = given()
                .spec(requestSpec)
                .body(request)
                .when()
                .post()
                .then()
                .statusCode(400)
                .extract().response();

        ErrorResponse error = response.as(ErrorResponse.class);

        assertThat(error.getStatus())
                .as("[%s] status field in error body should be 400", scenario)
                .isEqualTo(400);

        assertThat(error.getContext())
                .as("[%s] validation context should be present", scenario)
                .isNotEmpty();
    }
}
