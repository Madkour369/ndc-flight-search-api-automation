package providers;

import models.request.FlightSearchRequest;
import models.request.Passenger;
import models.request.SearchCriteria;
import org.testng.annotations.DataProvider;

import java.time.LocalDate;
import java.util.List;

/**
 * Central source of FlightSearch request payloads for the suite, kept
 * independent of TestNG assertions so scenarios stay reusable across
 * positive and negative test classes. Every {@code @DataProvider} and
 * factory method here maps back to one or more rows in TestCases.md — see
 * the Javadoc on each for the exact TC IDs it covers.
 * <p>
 * A handful of scenarios (genuine JSON type/shape mismatches — a string
 * where an integer belongs, an object where an array belongs, or outright
 * malformed JSON) cannot be expressed through {@link FlightSearchRequest}
 * at all: its fields are correctly, strongly typed, so there's no builder
 * call that produces a wrongly-typed field. Those scenarios are provided as
 * raw JSON strings instead and sent via {@code BaseTest#searchRaw}.
 */
public final class FlightSearchDataProvider {

    public static final String DEFAULT_SUPPLIER = "flyadeal";
    public static final String DEFAULT_CREDENTIALS_SELECTOR = "EGY";

    private FlightSearchDataProvider() {
    }

    // ---------------------------------------------------------------------
    // Reusable builders
    // ---------------------------------------------------------------------

    public static FlightSearchRequest request(List<SearchCriteria> criteria, List<Passenger> passengers) {
        return FlightSearchRequest.builder()
                .supplier(DEFAULT_SUPPLIER)
                .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                .isdebug(true)
                .searchCriteria(criteria)
                .passengers(passengers)
                .build();
    }

    public static SearchCriteria criteria(String origin, String destination, String date) {
        return SearchCriteria.builder().origin(origin).destination(destination).date(date).build();
    }

    public static Passenger passenger(String typeCode, int count) {
        return Passenger.builder().passengerTypeCode(typeCode).count(count).build();
    }

    /** The simplest valid request: one-way, single adult. */
    public static FlightSearchRequest validOneWayRequest() {
        return request(List.of(criteria("CAI", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)));
    }

    /** TC_POS_009 (isdebug=true) / TC_POS_010 (isdebug=null, omitted from the wire payload). */
    public static FlightSearchRequest requestWithDebug(Boolean isdebug) {
        return FlightSearchRequest.builder()
                .supplier(DEFAULT_SUPPLIER)
                .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                .isdebug(isdebug)
                .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                .passengers(List.of(passenger("ADT", 1)))
                .build();
    }

    /** TC_POS_005/006's itinerary, reused by TC_BIZ_006's ref-integrity check. */
    public static FlightSearchRequest roundTripRequest() {
        return request(
                List.of(criteria("CAI", "RUH", "2026-09-08"), criteria("RUH", "CAI", "2026-09-15")),
                List.of(passenger("ADT", 2)));
    }

    /** TC_DATE_012: 2028-02-29 is a genuine leap day and must be accepted. */
    public static FlightSearchRequest leapYearValidRequest() {
        return request(List.of(criteria("CAI", "RUH", "2028-02-29")), List.of(passenger("ADT", 1)));
    }

    /** TC_FLD_014: a blank (empty-string) supplier value. */
    public static FlightSearchRequest blankSupplierRequest() {
        return FlightSearchRequest.builder()
                .supplier("")
                .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                .isdebug(true)
                .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                .passengers(List.of(passenger("ADT", 1)))
                .build();
    }

    /** TC_TYPE_006: an unrecognized passengerTypeCode. */
    public static FlightSearchRequest invalidPassengerTypeCodeRequest() {
        return request(List.of(criteria("CAI", "RUH", "2026-09-08")), List.of(passenger("XYZ", 1)));
    }

    /** TC_TYPE_018 — sent raw since FlightSearchRequest has no slot for an arbitrary extra property. */
    public static final String REQUEST_WITH_UNKNOWN_FIELD_JSON = """
            {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
             "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
             "passengers":[{"passengerTypeCode":"ADT","count":1}],
             "unexpectedField":"someValue"}
            """;

    // ---------------------------------------------------------------------
    // Positive: TC_POS_001-006, 008, 011
    // ---------------------------------------------------------------------

    @DataProvider(name = "positiveSearchScenarios")
    public static Object[][] positiveSearchScenarios() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        return new Object[][]{
                {"TC_POS_001 One-way | single adult",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"TC_POS_002 One-way | ADT+CHD+INF mix",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08")),
                                List.of(passenger("ADT", 3), passenger("CHD", 2), passenger("INF", 3)))},
                {"TC_POS_003 One-way | adults with children, no infants",
                        request(List.of(criteria("CAI", "JED", "2026-09-20")),
                                List.of(passenger("ADT", 2), passenger("CHD", 1)))},
                {"TC_POS_004 One-way | adult with a single infant",
                        request(List.of(criteria("CAI", "JED", "2026-09-25")),
                                List.of(passenger("ADT", 1), passenger("INF", 1)))},
                {"TC_POS_005 Round-trip | same city pair reversed",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08"), criteria("RUH", "CAI", "2026-09-15")),
                                List.of(passenger("ADT", 2)))},
                {"TC_POS_006 Round-trip | full passenger mix",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08"), criteria("RUH", "CAI", "2026-09-15")),
                                List.of(passenger("ADT", 2), passenger("CHD", 1), passenger("INF", 1)))},
                {"TC_POS_008 One-way | infants equal to adults (1:1)",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08")),
                                List.of(passenger("ADT", 2), passenger("INF", 2)))},
                {"TC_POS_011 One-way | earliest bookable date (tomorrow)",
                        request(List.of(criteria("CAI", "RUH", tomorrow)), List.of(passenger("ADT", 1)))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_FLD_001-003 — missing a required top-level field
    // ---------------------------------------------------------------------

    @DataProvider(name = "missingRequiredFieldScenarios")
    public static Object[][] missingRequiredFieldScenarios() {
        return new Object[][]{
                {"Missing searchCriteria", FlightSearchRequest.builder()
                        .supplier(DEFAULT_SUPPLIER)
                        .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                        .isdebug(true)
                        .passengers(List.of(passenger("ADT", 1)))
                        .build(),
                        "SearchCriteria"},
                {"Missing passengers", FlightSearchRequest.builder()
                        .supplier(DEFAULT_SUPPLIER)
                        .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                        .isdebug(true)
                        .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                        .build(),
                        "Passengers"},
                {"Missing supplier", FlightSearchRequest.builder()
                        .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                        .isdebug(true)
                        .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                        .passengers(List.of(passenger("ADT", 1)))
                        .build(),
                        "Supplier"},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_FLD_005-006 — empty required array
    // ---------------------------------------------------------------------

    @DataProvider(name = "emptyRequiredArrayScenarios")
    public static Object[][] emptyRequiredArrayScenarios() {
        return new Object[][]{
                {"Empty searchCriteria array", request(List.of(), List.of(passenger("ADT", 1)))},
                {"Empty passengers array", request(List.of(criteria("CAI", "RUH", "2026-09-08")), List.of())},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_FLD_007, TC_FLD_008, TC_DATE_008 — missing searchCriteria sub-field
    // ---------------------------------------------------------------------

    @DataProvider(name = "missingSearchCriteriaFieldScenarios")
    public static Object[][] missingSearchCriteriaFieldScenarios() {
        return new Object[][]{
                {"Missing origin", request(
                        List.of(SearchCriteria.builder().destination("RUH").date("2026-09-08").build()),
                        List.of(passenger("ADT", 1)))},
                {"Missing destination", request(
                        List.of(SearchCriteria.builder().origin("CAI").date("2026-09-08").build()),
                        List.of(passenger("ADT", 1)))},
                {"Missing date", request(
                        List.of(SearchCriteria.builder().origin("CAI").destination("RUH").build()),
                        List.of(passenger("ADT", 1)))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_FLD_009-010 — missing passenger sub-field
    // ---------------------------------------------------------------------

    @DataProvider(name = "missingPassengerFieldScenarios")
    public static Object[][] missingPassengerFieldScenarios() {
        return new Object[][]{
                {"Missing passengerTypeCode", request(
                        List.of(criteria("CAI", "RUH", "2026-09-08")),
                        List.of(Passenger.builder().count(1).build()))},
                {"Missing count", request(
                        List.of(criteria("CAI", "RUH", "2026-09-08")),
                        List.of(Passenger.builder().passengerTypeCode("ADT").build()))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_DATE_001, 002, 005, 006, 007, 009, 011, 013, 016, 017
    // ---------------------------------------------------------------------

    @DataProvider(name = "invalidDateScenarios")
    public static Object[][] invalidDateScenarios() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        return new Object[][]{
                {"Departure date in the past",
                        request(List.of(criteria("CAI", "RUH", yesterday)), List.of(passenger("ADT", 1)))},
                {"Departure date far in the past",
                        request(List.of(criteria("CAI", "RUH", "2000-01-01")), List.of(passenger("ADT", 1)))},
                {"Date in DD-MM-YYYY format",
                        request(List.of(criteria("CAI", "RUH", "08-09-2026")), List.of(passenger("ADT", 1)))},
                {"Date as free text",
                        request(List.of(criteria("CAI", "RUH", "next-tuesday")), List.of(passenger("ADT", 1)))},
                {"Date with slashes",
                        request(List.of(criteria("CAI", "RUH", "2026/09/08")), List.of(passenger("ADT", 1)))},
                {"Date as empty string",
                        request(List.of(criteria("CAI", "RUH", "")), List.of(passenger("ADT", 1)))},
                {"Calendar-invalid date (Feb 30)",
                        request(List.of(criteria("CAI", "RUH", "2026-02-30")), List.of(passenger("ADT", 1)))},
                {"Leap day in a non-leap year (2026-02-29)",
                        request(List.of(criteria("CAI", "RUH", "2026-02-29")), List.of(passenger("ADT", 1)))},
                {"Date includes a time component",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08T10:00:00")), List.of(passenger("ADT", 1)))},
                {"Date not zero-padded",
                        request(List.of(criteria("CAI", "RUH", "2026-9-8")), List.of(passenger("ADT", 1)))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_TYPE_002-003 — invalid passenger count value (valid type)
    // ---------------------------------------------------------------------

    @DataProvider(name = "invalidPassengerCountScenarios")
    public static Object[][] invalidPassengerCountScenarios() {
        return new Object[][]{
                {"Negative passenger count",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08")), List.of(passenger("ADT", -1)))},
                {"Zero passenger count",
                        request(List.of(criteria("CAI", "RUH", "2026-09-08")), List.of(passenger("ADT", 0)))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_TYPE_016-017, TC_SEC_001-003 — malicious/oversized string values
    // ---------------------------------------------------------------------

    @DataProvider(name = "maliciousOrOversizedFieldValueScenarios")
    public static Object[][] maliciousOrOversizedFieldValueScenarios() {
        String longOrigin = "A".repeat(500);
        String longCredentialsSelector = "A".repeat(10_000);
        return new Object[][]{
                {"SQL-injection-style string in origin", request(
                        List.of(criteria("CAI'; DROP TABLE offers;--", "RUH", "2026-09-08")),
                        List.of(passenger("ADT", 1)))},
                {"Excessively long origin string", request(
                        List.of(criteria(longOrigin, "RUH", "2026-09-08")),
                        List.of(passenger("ADT", 1)))},
                {"XSS-style payload in supplier", FlightSearchRequest.builder()
                        .supplier("<script>alert(1)</script>")
                        .credentialsSelector(DEFAULT_CREDENTIALS_SELECTOR)
                        .isdebug(true)
                        .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                        .passengers(List.of(passenger("ADT", 1)))
                        .build()},
                {"Null-byte injection in origin", request(
                        List.of(criteria("CAI ", "RUH", "2026-09-08")),
                        List.of(passenger("ADT", 1)))},
                {"Extremely long credentialsSelector", FlightSearchRequest.builder()
                        .supplier(DEFAULT_SUPPLIER)
                        .credentialsSelector(longCredentialsSelector)
                        .isdebug(true)
                        .searchCriteria(List.of(criteria("CAI", "RUH", "2026-09-08")))
                        .passengers(List.of(passenger("ADT", 1)))
                        .build()},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_APT_001-007, TC_APT_009 — invalid airport codes
    // ---------------------------------------------------------------------

    @DataProvider(name = "invalidAirportCodeScenarios")
    public static Object[][] invalidAirportCodeScenarios() {
        return new Object[][]{
                {"Well-formed but non-existent code",
                        request(List.of(criteria("ZZZ", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"Code shorter than 3 letters",
                        request(List.of(criteria("CA", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"Code longer than 3 letters",
                        request(List.of(criteria("CAIR", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"Numeric-only code",
                        request(List.of(criteria("123", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"Special characters in code",
                        request(List.of(criteria("C@I", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"Empty string airport code",
                        request(List.of(criteria("", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"Identical origin and destination",
                        request(List.of(criteria("CAI", "CAI", "2026-09-08")), List.of(passenger("ADT", 1)))},
                {"ICAO code instead of IATA",
                        request(List.of(criteria("HECA", "RUH", "2026-09-08")), List.of(passenger("ADT", 1)))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_BIZ_001-002 — true multi-city itinerary
    // ---------------------------------------------------------------------

    @DataProvider(name = "multiCityScenarios")
    public static Object[][] multiCityScenarios() {
        return new Object[][]{
                {"Three-city itinerary (2 legs)", request(
                        List.of(criteria("CAI", "RUH", "2026-09-08"), criteria("RUH", "JED", "2026-09-12")),
                        List.of(passenger("ADT", 1), passenger("CHD", 1)))},
                {"Four-city itinerary (3 legs)", request(
                        List.of(criteria("CAI", "RUH", "2026-09-08"), criteria("RUH", "JED", "2026-09-12"),
                                criteria("JED", "CAI", "2026-09-16")),
                        List.of(passenger("ADT", 1)))},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_TYPE_001, 004, 008, 011-014, 019; TC_FLD_013; TC_DATE_010
    // Genuine JSON type/shape mismatches -- not representable via the
    // strongly-typed request POJOs, so these are raw JSON strings.
    // ---------------------------------------------------------------------

    @DataProvider(name = "malformedOrTypeMismatchedPayloadScenarios")
    public static Object[][] malformedOrTypeMismatchedPayloadScenarios() {
        return new Object[][]{
                {"passengers[].count as a numeric string", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
                         "passengers":[{"passengerTypeCode":"ADT","count":"3"}]}"""},
                {"passengers[].count as a decimal", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
                         "passengers":[{"passengerTypeCode":"ADT","count":1.5}]}"""},
                {"passengers[].passengerTypeCode as a non-string", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
                         "passengers":[{"passengerTypeCode":1,"count":1}]}"""},
                {"searchCriteria as an object, not an array", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":{"origin":"CAI","destination":"RUH","date":"2026-09-08"},
                         "passengers":[{"passengerTypeCode":"ADT","count":1}]}"""},
                {"passengers as an object, not an array", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
                         "passengers":{"passengerTypeCode":"ADT","count":1}}"""},
                {"supplier as a non-string type", """
                        {"supplier":12345,"credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
                         "passengers":[{"passengerTypeCode":"ADT","count":1}]}"""},
                {"origin as a non-string type", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":123,"destination":"RUH","date":"2026-09-08"}],
                         "passengers":[{"passengerTypeCode":"ADT","count":1}]}"""},
                {"Malformed JSON syntax (trailing comma)", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":"2026-09-08"}],
                         "passengers":[{"passengerTypeCode":"ADT","count":1}],}"""},
                {"Request body is a JSON array, not an object", "[]"},
                {"date as an explicit JSON null", """
                        {"supplier":"flyadeal","credentialsSelector":"EGY","isdebug":true,
                         "searchCriteria":[{"origin":"CAI","destination":"RUH","date":null}],
                         "passengers":[{"passengerTypeCode":"ADT","count":1}]}"""},
        };
    }

    // ---------------------------------------------------------------------
    // Negative: TC_AUTH_001, TC_AUTH_003 (+ TC_SEC_004 folded in by the test)
    // ---------------------------------------------------------------------

    @DataProvider(name = "invalidOrEmptyApiKeyScenarios")
    public static Object[][] invalidOrEmptyApiKeyScenarios() {
        return new Object[][]{
                {"Bogus x-api-key value", "invalid-00000000-0000-0000-0000-000000000000-key"},
                {"Empty x-api-key value", ""},
        };
    }
}
