package com.tildetech.ndc.automation.tests;

import com.tildetech.ndc.automation.base.BaseTest;
import com.tildetech.ndc.automation.models.request.FlightSearchRequest;
import com.tildetech.ndc.automation.models.response.FlightSearchResponse;
import com.tildetech.ndc.automation.models.response.Offer;
import com.tildetech.ndc.automation.providers.FlightSearchDataProvider;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("NDC Supplier Integration")
@Feature("Flight Search")
public class FlightSearchPositiveTests extends BaseTest {

    @Test(dataProvider = "positiveSearchScenarios",
            dataProviderClass = FlightSearchDataProvider.class,
            description = "Flight search returns a 200 with valid offers across itinerary and passenger-mix variations")
    @Story("Successful search")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verifies status 200, a non-null responseId, correct supplier echo, and a non-empty offers array.")
    public void searchReturnsValidOffers(String scenario, FlightSearchRequest request) {
        Response response = given()
                .spec(requestSpec)
                .body(request)
                .when()
                .post()
                .then()
                .statusCode(200)
                .extract().response();

        FlightSearchResponse searchResponse = response.as(FlightSearchResponse.class);

        assertThat(searchResponse.getResponseId())
                .as("[%s] responseId should be present", scenario)
                .isNotBlank();

        assertThat(searchResponse.getSupplier())
                .as("[%s] supplier in the response should echo the requested supplier", scenario)
                .isNotBlank()
                .isEqualToIgnoringCase(request.getSupplier());

        assertThat(searchResponse.getOffers())
                .as("[%s] offers array should not be empty", scenario)
                .isNotEmpty();

        Offer firstOffer = searchResponse.getOffers().get(0);

        assertThat(firstOffer.getOfferId())
                .as("[%s] each offer should carry a non-blank offerId", scenario)
                .isNotBlank();

        assertThat(firstOffer.getPassengerFareBreakdown())
                .as("[%s] offer should break down fares per passenger type", scenario)
                .isNotEmpty();

        assertThat(firstOffer.getPriceDetails())
                .as("[%s] offer should carry price details", scenario)
                .isNotNull();

        assertThat(firstOffer.getPriceDetails().getTotalAmount().getAmount())
                .as("[%s] total offer amount should be a positive value", scenario)
                .isGreaterThan(BigDecimal.ZERO);
    }
}
