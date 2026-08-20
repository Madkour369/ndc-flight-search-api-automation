package com.tildetech.ndc.automation.providers;

import com.tildetech.ndc.automation.config.ConfigManager;
import com.tildetech.ndc.automation.models.request.FlightSearchRequest;
import com.tildetech.ndc.automation.models.request.PassengerRequest;
import com.tildetech.ndc.automation.models.request.SearchCriteria;
import org.testng.annotations.DataProvider;

import java.util.List;

/**
 * Central source of request payloads for the FlightSearch suite. Kept
 * independent of TestNG assertions so scenarios stay reusable across
 * positive and negative test classes.
 */
public final class FlightSearchDataProvider {

    private FlightSearchDataProvider() {
    }

    // ---------------------------------------------------------------------
    // Positive scenarios: one-way, round-trip/multi-city, varied pax mixes
    // ---------------------------------------------------------------------

    @DataProvider(name = "positiveSearchScenarios")
    public static Object[][] positiveSearchScenarios() {
        return new Object[][]{
                {
                        "One-way | ADT+CHD+INF mix",
                        request(
                                List.of(criteria("CAI", "RUH", "2026-09-08")),
                                List.of(passenger("ADT", 3), passenger("CHD", 2), passenger("INF", 3))
                        )
                },
                {
                        "One-way | Single adult",
                        request(
                                List.of(criteria("CAI", "RUH", "2026-09-15")),
                                List.of(passenger("ADT", 1))
                        )
                },
                {
                        "One-way | Adults with children, no infants",
                        request(
                                List.of(criteria("CAI", "JED", "2026-09-20")),
                                List.of(passenger("ADT", 2), passenger("CHD", 1))
                        )
                },
                {
                        "Round-trip | CAI-RUH outbound + RUH-CAI return",
                        request(
                                List.of(
                                        criteria("CAI", "RUH", "2026-09-08"),
                                        criteria("RUH", "CAI", "2026-09-15")
                                ),
                                List.of(passenger("ADT", 2))
                        )
                },
                {
                        "One-way | Adult with infant, alternate route",
                        request(
                                List.of(criteria("CAI", "JED", "2026-09-25")),
                                List.of(passenger("ADT", 1), passenger("INF", 1))
                        )
                },
        };
    }

    // ---------------------------------------------------------------------
    // Negative: invalid date formats in search criteria (expect 400)
    // ---------------------------------------------------------------------

    @DataProvider(name = "invalidDateFormatScenarios")
    public static Object[][] invalidDateFormatScenarios() {
        return new Object[][]{
                {
                        "Date in DD-MM-YYYY format",
                        request(List.of(criteria("CAI", "RUH", "08-09-2026")), List.of(passenger("ADT", 1)))
                },
                {
                        "Date as free text",
                        request(List.of(criteria("CAI", "RUH", "next-tuesday")), List.of(passenger("ADT", 1)))
                },
                {
                        "Date with slashes",
                        request(List.of(criteria("CAI", "RUH", "2026/09/08")), List.of(passenger("ADT", 1)))
                },
        };
    }

    // ---------------------------------------------------------------------
    // Negative: missing required top-level fields (expect 400, with a token
    // that must appear in the error context describing the offending field)
    // ---------------------------------------------------------------------

    @DataProvider(name = "missingRequiredFieldScenarios")
    public static Object[][] missingRequiredFieldScenarios() {
        return new Object[][]{
                {
                        "Missing searchCriteria",
                        FlightSearchRequest.builder()
                                .supplier(ConfigManager.defaultSupplier())
                                .credentialsSelector(ConfigManager.defaultCredentialsSelector())
                                .isdebug(true)
                                .passengers(List.of(passenger("ADT", 1)))
                                .build(),
                        "SearchCriteria"
                },
                {
                        "Missing passengers",
                        FlightSearchRequest.builder()
                                .supplier(ConfigManager.defaultSupplier())
                                .credentialsSelector(ConfigManager.defaultCredentialsSelector())
                                .isdebug(true)
                                .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                                .build(),
                        "Passengers"
                },
                {
                        "Missing supplier",
                        FlightSearchRequest.builder()
                                .credentialsSelector(ConfigManager.defaultCredentialsSelector())
                                .isdebug(true)
                                .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                                .passengers(List.of(passenger("ADT", 1)))
                                .build(),
                        "Supplier"
                },
        };
    }

    // ---------------------------------------------------------------------
    // Reusable builders
    // ---------------------------------------------------------------------

    public static FlightSearchRequest validOneWayRequest() {
        return request(
                List.of(criteria("CAI", "RUH", "2026-09-08")),
                List.of(passenger("ADT", 1))
        );
    }

    /**
     * True multi-city itinerary (3+ distinct city codes). flyadeal rejects
     * this as a business rule ("only oneway or round trips allowed"), so it
     * is used to exercise the 409 business-validation path rather than as a
     * positive scenario.
     */
    public static FlightSearchRequest multiCityRequest() {
        return request(
                List.of(
                        criteria("CAI", "RUH", "2026-09-08"),
                        criteria("RUH", "JED", "2026-09-12")
                ),
                List.of(passenger("ADT", 1), passenger("CHD", 1))
        );
    }

    public static FlightSearchRequest request(List<SearchCriteria> criteria, List<PassengerRequest> passengers) {
        return FlightSearchRequest.builder()
                .supplier(ConfigManager.defaultSupplier())
                .credentialsSelector(ConfigManager.defaultCredentialsSelector())
                .isdebug(true)
                .searchCriteria(criteria)
                .passengers(passengers)
                .build();
    }

    public static SearchCriteria criteria(String origin, String destination, String date) {
        return SearchCriteria.builder()
                .origin(origin)
                .destination(destination)
                .date(date)
                .build();
    }

    public static PassengerRequest passenger(String typeCode, int count) {
        return PassengerRequest.builder()
                .passengerTypeCode(typeCode)
                .count(count)
                .build();
    }
}
