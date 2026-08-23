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
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import models.request.FlightSearchRequest;
import models.response.FlightSearchResponse;
import models.response.Journey;
import models.response.Offer;
import models.response.PassengerFareBreakdown;
import models.response.SegmentDetail;
import org.testng.annotations.Test;
import providers.FlightSearchDataProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Epic("NDC Supplier Integration")
@Feature("Flight Search")
public class FlightSearchPositiveTests extends BaseTest {

    @Test(dataProvider = "positiveSearchScenarios", dataProviderClass = FlightSearchDataProvider.class,
            description = "Flight search returns 200 with valid offers across itinerary and passenger-mix variations")
    @Story("Successful search")
    @Severity(SeverityLevel.BLOCKER)
    @Description("TC_POS_001-006, 008, 011: one-way/round-trip, ADT/CHD/INF mixes, and the earliest-bookable-date boundary.")
    public void searchReturnsValidOffers(String scenario, FlightSearchRequest request) {
        Response response = search(request);
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        assertValidOfferResponse(body, request, scenario);
    }

    @Test(description = "isdebug=true exposes the raw supplier request/response traffic")
    @Story("Debug mode")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POS_009.")
    public void debugModeOnExposesSupplierTraffic() {
        Response response = search(FlightSearchDataProvider.requestWithDebug(true));
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        assertFalse(isBlank(body.getSupplierRequest()), "supplierRequest should be populated when isdebug=true");
        assertFalse(isBlank(body.getSupplierResponse()), "supplierResponse should be populated when isdebug=true");
    }

    @Test(description = "isdebug omitted keeps the supplier raw traffic out of the response")
    @Story("Debug mode")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POS_010.")
    public void debugModeOffHidesSupplierTraffic() {
        Response response = search(FlightSearchDataProvider.requestWithDebug(null));
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        assertNull(body.getSupplierRequest(), "supplierRequest must not leak when isdebug is omitted");
        assertNull(body.getSupplierResponse(), "supplierResponse must not leak when isdebug is omitted");
    }

    @Test(description = "2028-02-29, a genuine leap day, is accepted as a valid departure date")
    @Story("Date boundaries")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_DATE_012.")
    public void leapYearDateIsAccepted() {
        FlightSearchRequest request = FlightSearchDataProvider.leapYearValidRequest();
        Response response = search(request);
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        assertValidOfferResponse(body, request, "Leap day 2028-02-29");
    }

    @Test(description = "An unrecognized top-level field is silently ignored, not rejected")
    @Story("Forward compatibility")
    @Severity(SeverityLevel.MINOR)
    @Description("TC_TYPE_018 — sent as raw JSON since FlightSearchRequest has no slot for an arbitrary extra property.")
    public void unknownTopLevelFieldIsIgnored() {
        Response response = searchRaw(FlightSearchDataProvider.REQUEST_WITH_UNKNOWN_FIELD_JSON);
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        assertFalse(isBlank(body.getResponseId()), "responseId should be present");
        assertFalse(body.getOffers().isEmpty(), "offers should be non-empty");
    }

    @Test(description = "Header names are treated case-insensitively, per HTTP semantics")
    @Story("Authentication")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_AUTH_009 — same request sent with X-API-KEY (all caps) instead of x-api-key.")
    public void headerNameCaseDoesNotAffectAuthentication() {
        RequestSpecification spec = RequestSpecFactory.specWithHeaders(Map.of(
                "X-API-KEY", ConfigManager.apiKey(),
                RequestSpecFactory.HEADER_CLIENT_ID, ConfigManager.clientId()));
        FlightSearchRequest request = FlightSearchDataProvider.validOneWayRequest();
        Response response = search(request, spec);
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        assertValidOfferResponse(body, request, "Header name case-insensitivity");
    }

    @Test(description = "Round-trip offers only reference journeys belonging to that offer's own itinerary")
    @Story("Response structural integrity")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_BIZ_006.")
    public void roundTripOffersReferenceOnlyTheirOwnJourneys() {
        Response response = search(FlightSearchDataProvider.roundTripRequest());
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);
        for (Offer offer : body.getOffers()) {
            for (String journeyId : offer.getOfferJourneys()) {
                assertTrue(body.getJourneys().containsKey(journeyId),
                        "Offer " + offer.getOfferId() + " references unknown journey " + journeyId);
            }
        }
    }

    @Test(description = "Every ref ID an offer/journey points to actually exists in the response's lookup maps")
    @Story("Response structural integrity")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POS_013 — walks Offer -> Journey -> FlightSegment/PriceClass/BaggageDetail.")
    public void allReferencedIdsResolve() {
        Response response = search(FlightSearchDataProvider.validOneWayRequest());
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);

        for (Offer offer : body.getOffers()) {
            for (String journeyId : offer.getOfferJourneys()) {
                Journey journey = body.getJourneys().get(journeyId);
                assertNotNull(journey, "Offer " + offer.getOfferId() + " references missing journey " + journeyId);
                for (String segmentId : journey.getSegmentRefIds()) {
                    assertTrue(body.getFlightSegments().containsKey(segmentId),
                            "Journey " + journeyId + " references missing flightSegment " + segmentId);
                }
            }
            for (PassengerFareBreakdown fare : offer.getPassengerFareBreakdown()) {
                for (SegmentDetail detail : fare.getSegmentDetails()) {
                    assertTrue(body.getPriceClasses().containsKey(detail.getPriceClassRefId()),
                            "SegmentDetail references missing priceClass " + detail.getPriceClassRefId());
                    assertTrue(body.getBaggageDetails().containsKey(detail.getBaggageDetailsRefId()),
                            "SegmentDetail references missing baggageDetail " + detail.getBaggageDetailsRefId());
                }
            }
        }
    }

    @Test(description = "Fare totals are internally consistent and use valid currency codes")
    @Story("Response structural integrity")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POS_014.")
    public void monetaryFieldsAreConsistent() {
        Response response = search(FlightSearchDataProvider.validOneWayRequest());
        FlightSearchResponse body = ResponseHandler.expectSuccess(response, 200, FlightSearchResponse.class);

        for (Offer offer : body.getOffers()) {
            for (PassengerFareBreakdown fare : offer.getPassengerFareBreakdown()) {
                BigDecimal base = fare.getPaxBaseAmount().getAmount();
                BigDecimal tax = fare.getPaxTotalTaxAmount().getAmount();
                BigDecimal total = fare.getPaxTotalAmount().getAmount();

                assertEquals(base.add(tax).setScale(2, RoundingMode.HALF_UP), total.setScale(2, RoundingMode.HALF_UP),
                        "base + tax should equal total for passenger type " + fare.getPassengerTypeCode());
                assertTrue(total.compareTo(BigDecimal.ZERO) >= 0, "total amount should not be negative");
                assertEquals(fare.getPaxTotalAmount().getCurrency().length(), 3,
                        "currency should be a 3-letter ISO code");
            }
        }
    }

    // -------------------------------------------------------------------
    // Shared assertions
    // -------------------------------------------------------------------

    private void assertValidOfferResponse(FlightSearchResponse body, FlightSearchRequest request, String scenario) {
        assertFalse(isBlank(body.getResponseId()), "[" + scenario + "] responseId should be present");
        assertTrue(body.getSupplier() != null && body.getSupplier().equalsIgnoreCase(request.getSupplier()),
                "[" + scenario + "] supplier should echo the requested supplier");
        assertFalse(body.getOffers() == null || body.getOffers().isEmpty(),
                "[" + scenario + "] offers should be non-empty");

        Offer firstOffer = body.getOffers().get(0);
        assertFalse(isBlank(firstOffer.getOfferId()), "[" + scenario + "] offerId should be present");
        assertFalse(firstOffer.getPassengerFareBreakdown() == null || firstOffer.getPassengerFareBreakdown().isEmpty(),
                "[" + scenario + "] passengerFareBreakdown should be non-empty");
        assertNotNull(firstOffer.getPriceDetails(), "[" + scenario + "] priceDetails should be present");
        assertTrue(firstOffer.getPriceDetails().getTotalAmount().getAmount().compareTo(BigDecimal.ZERO) > 0,
                "[" + scenario + "] total offer amount should be positive");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
